package com.example.model

/**
 * Represents a segmented, normalized, and translated clause.
 */
data class Clause(
    val id: Long,
    val danishText: String,
    val russianText: String? = null,
    val status: ClauseStatus = ClauseStatus.COMMITTED,
    val asrCommitMs: Long = System.currentTimeMillis(),
    val speechEndMs: Long = asrCommitMs,
    val translationDoneMs: Long? = null,
    val ttsFirstAudioMs: Long? = null,
    val assignedSpeechRate: Float = 1.0f,
    val skippedPlayback: Boolean = false,
    val containsDigitsOrNames: Boolean = false,
    val isSelfVoiceFiltered: Boolean = false,
    val filterReason: String? = null
) {
    val latencyMs: Long?
        get() = if (ttsFirstAudioMs != null) ttsFirstAudioMs - asrCommitMs else null

    val totalPipelineLatencyMs: Long?
        get() = if (ttsFirstAudioMs != null) ttsFirstAudioMs - speechEndMs else null
}

enum class ClauseStatus {
    PARTIAL,
    COMMITTED,
    TRANSLATING,
    TRANSLATED,
    SPEAKING,
    PLAYED,
    SKIPPED,
    FILTERED_SELF_VOICE
}
