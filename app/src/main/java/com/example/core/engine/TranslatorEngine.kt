package com.example.core.engine

import kotlinx.coroutines.flow.StateFlow

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val message: String) : ModelDownloadState()
    object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Swappable translation interface so Phase 2 can substitute
 * ONNX Runtime (e.g. opus-mt-da-ru INT8) without UI changes.
 */
interface TranslatorEngine {
    val modelState: StateFlow<ModelDownloadState>

    suspend fun checkOrDownloadModels(): Boolean
    suspend fun translate(text: String): String
    fun close()
}
