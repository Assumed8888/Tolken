package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TolkenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TranslationService : Service {

    constructor() : super()

    companion object {
        private const val TAG = "TolkenService"
        const val CHANNEL_ID = "tolken_translation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.tolken.ACTION_START"
        const val ACTION_STOP = "com.example.tolken.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, TranslationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TranslationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = TolkenManager.getCoordinator(this)

        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                coordinator.stopPipeline()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                acquireWakeLock()
                startForegroundWithNotification()
                coordinator.startPipeline()

                // Observe latest translations to update notification subtext in background
                serviceScope.launch {
                    coordinator.liveRussianLatest.collect { latestRu ->
                        if (coordinator.isRunning.value) {
                            val displayText = if (latestRu.isNotBlank()) {
                                latestRu
                            } else {
                                "Ожидание датской речи (экран можно выключить)..."
                            }
                            val updatedNotification = buildNotification(displayText)
                            val nm = getSystemService(NotificationManager::class.java)
                            nm.notify(NOTIFICATION_ID, updatedNotification)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Tolken:LongTermTranslationWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                    Log.i(TAG, "Acquired PARTIAL_WAKE_LOCK for long-term screen-off translation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed acquiring WakeLock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Released PARTIAL_WAKE_LOCK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed releasing WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Ожидание датской речи (экран можно выключить)...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TranslationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tolken: Датский \u2192 Русский активен")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Остановить",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tolken: Фоновый синхронный перевод",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поддерживает постоянное распознавание датской речи и вывод перевода в наушники при выключенном экране"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
