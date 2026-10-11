package com.example.core.engine

import kotlinx.coroutines.flow.StateFlow

interface TtsEngine {
    val isReady: StateFlow<Boolean>

    fun initialize(onReady: (() -> Unit)? = null)

    fun speak(
        utteranceId: String,
        text: String,
        speechRate: Float,
        queueMode: Int,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )

    fun stop()
    fun shutdown()
}
