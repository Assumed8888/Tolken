package com.example.core.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechRecognizerAsrEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : AsrEngine {

    companion object {
        private const val TAG = "TolkenASR"
        const val DANISH_LOCALE = "da-DK"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<AsrEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    @Volatile
    private var isRequestedRunning = false
    private var restartRunnable: Runnable? = null
    private var lastSpeechEndTime = 0L
    private var consecutiveClientErrors = 0
    private var silentTimeoutCycles = 0

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "ASR ready for speech (da-DK)")
            _state.value = AsrState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "ASR beginning of speech detected")
            silentTimeoutCycles = 0
            consecutiveClientErrors = 0
        }

        override fun onRmsChanged(rmsdB: Float) {
            _events.tryEmit(AsrEvent.Rms(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            lastSpeechEndTime = System.currentTimeMillis()
            Log.d(TAG, "ASR end of speech at $lastSpeechEndTime")
            _events.tryEmit(AsrEvent.EndOfSpeech(lastSpeechEndTime))
        }

        override fun onError(error: Int) {
            val errorMsg = getErrorMessage(error)
            Log.d(TAG, "ASR standby status/error: $error ($errorMsg), isRunning=$isRequestedRunning")

            if (isRequestedRunning) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal standby silence waiting for speaker's next phrase
                        consecutiveClientErrors = 0
                        silentTimeoutCycles++
                        if (silentTimeoutCycles >= 150) {
                            // Clean refresh every ~15-20 min of continuous standby silence
                            silentTimeoutCycles = 0
                            destroyRecognizerInstance()
                            scheduleRestart(80L)
                        } else {
                            scheduleRestart(30L)
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        consecutiveClientErrors++
                        if (consecutiveClientErrors >= 2) {
                            destroyRecognizerInstance()
                            scheduleRestart(150L)
                        } else {
                            scheduleRestart(80L)
                        }
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        scheduleRestart(300L)
                    }
                    else -> {
                        scheduleRestart(200L)
                    }
                }
            } else {
                _state.value = AsrState.Error(error, errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveClientErrors = 0
            silentTimeoutCycles = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "ASR final result: '$text'")
            if (text.isNotEmpty()) {
                val endTs = if (lastSpeechEndTime > 0) lastSpeechEndTime else System.currentTimeMillis()
                _events.tryEmit(AsrEvent.Final(text, endTs))
            }
            lastSpeechEndTime = 0L

            if (isRequestedRunning) {
                // Seamlessly restart next listening turn immediately
                scheduleRestart(15L)
            } else {
                _state.value = AsrState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotEmpty()) {
                val now = System.currentTimeMillis()
                _events.tryEmit(AsrEvent.Partial(text, now))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun startListening() {
        mainHandler.post {
            isRequestedRunning = true
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null
            initAndStartRecognizer()
        }
    }

    override fun stopListening() {
        mainHandler.post {
            isRequestedRunning = false
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
            _state.value = AsrState.Idle
        }
    }

    override fun destroy() {
        mainHandler.post {
            isRequestedRunning = false
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            restartRunnable = null
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying recognizer", e)
            }
            speechRecognizer = null
            _state.value = AsrState.Idle
        }
    }

    private fun destroyRecognizerInstance() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.d(TAG, "Error destroying speechRecognizer instance", e)
        }
        speechRecognizer = null
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isRequestedRunning) return
        _state.value = AsrState.Restarting
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = Runnable {
            if (isRequestedRunning) {
                initAndStartRecognizer()
            }
        }
        mainHandler.postDelayed(restartRunnable!!, delayMs)
    }

    private fun initAndStartRecognizer() {
        try {
            if (speechRecognizer == null) {
                // Prefer on-device SpeechRecognizer for zero latency & offline operation
                speechRecognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Creating on-device SpeechRecognizer")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.i(TAG, "Falling back to standard SpeechRecognizer")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                speechRecognizer?.setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, DANISH_LOCALE)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, DANISH_LOCALE)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _state.value = AsrState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            // Re-instantiate on fatal error
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            if (isRequestedRunning) {
                scheduleRestart(250L)
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Danish not supported on device"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Danish package unavailable"
        else -> "Recognition error ($errorCode)"
    }
}
