package com.example.core.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import com.example.model.AudioInputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRouteStatus(
    val isBluetoothConnected: Boolean = false,
    val activeDeviceName: String = "Динамик телефона",
    val isLowLatencyActive: Boolean = false,
    val deviceType: String = "Встроенный",
    val isHeadsetMicActive: Boolean = false
)

class AudioRouter(private val context: Context) {

    companion object {
        private const val TAG = "TolkenAudioRouter"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routeStatus = MutableStateFlow(AudioRouteStatus())
    val routeStatus = _routeStatus.asStateFlow()

    private var activeBluetoothDevice: AudioDeviceInfo? = null
    var currentInputMode: AudioInputMode = AudioInputMode.BLUETOOTH_HEADSET_MIC

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added: ${addedDevices?.size}")
            refreshRouting()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed: ${removedDevices?.size}")
            refreshRouting()
        }
    }

    fun start() {
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            refreshRouting()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRouter", e)
        }
    }

    fun stop() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            clearRouting()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRouter", e)
        }
    }

    fun refreshRouting() {
        try {
            val devices = audioManager.availableCommunicationDevices
            // Prioritize low-latency Bluetooth communication profiles:
            // 1. BLE Headset (ultra-low latency on newer Bluetooth LE Audio)
            // 2. Bluetooth SCO (low-latency bidirectional headset profile: microphone + speaker)
            // 3. Bluetooth A2DP / Hearing Aid
            val btDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_HEARING_AID }

            if (btDevice != null && currentInputMode == AudioInputMode.BLUETOOTH_HEADSET_MIC) {
                // Set MODE_IN_COMMUNICATION so Android activates the Bluetooth headset's microphone
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val success = audioManager.setCommunicationDevice(btDevice)
                if (success) {
                    activeBluetoothDevice = btDevice
                    val typeName = getDeviceTypeName(btDevice.type)
                    Log.i(TAG, "Successfully routed bidirectional audio to Bluetooth headset: ${btDevice.productName} ($typeName)")
                    _routeStatus.value = AudioRouteStatus(
                        isBluetoothConnected = true,
                        activeDeviceName = btDevice.productName?.toString() ?: "Bluetooth Гарнитура",
                        isLowLatencyActive = btDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET || btDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        deviceType = typeName,
                        isHeadsetMicActive = true
                    )
                    return
                }
            }

            // Fallback or explicit Phone Mic selected
            clearRouting()
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission missing for device routing inspection", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error in refreshRouting", e)
        }
    }

    private fun clearRouting() {
        try {
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
            activeBluetoothDevice = null
            _routeStatus.value = AudioRouteStatus(
                isBluetoothConnected = false,
                activeDeviceName = "Динамик и микрофон телефона",
                isLowLatencyActive = false,
                deviceType = "Встроенный",
                isHeadsetMicActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing communication device", e)
        }
    }

    private fun getDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Гарнитура (Микрофон + Звук)"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (Микрофон + Звук)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Слуховой аппарат"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура (Микрофон + Звук)"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Динамик телефона"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Разговорный динамик"
        else -> "Аудиоустройство ($type)"
    }
}
