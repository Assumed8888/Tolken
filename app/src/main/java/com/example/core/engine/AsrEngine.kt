package com.example.core.engine

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed class AsrState {
    object Idle : AsrState()
    object Listening : AsrState()
    object Restarting : AsrState()
    data class Error(val code: Int, val message: String) : AsrState()
}

sealed class AsrEvent {
    data class Partial(val text: String, val timestampMs: Long = System.currentTimeMillis()) : AsrEvent()
    data class Final(val text: String, val timestampMs: Long = System.currentTimeMillis()) : AsrEvent()
    data class EndOfSpeech(val timestampMs: Long = System.currentTimeMillis()) : AsrEvent()
    data class Rms(val db: Float) : AsrEvent()
    data class Error(val code: Int, val message: String) : AsrEvent()
}

/**
 * Pluggable ASR Engine interface.
 * Can be implemented by Android on-device SpeechRecognizer,
 * or Phase 2 streaming ONNX / sherpa-onnx / Whisper.
 */
interface AsrEngine {
    val state: StateFlow<AsrState>
    val events: SharedFlow<AsrEvent>

    fun startListening()
    fun stopListening()
    fun destroy()
}
