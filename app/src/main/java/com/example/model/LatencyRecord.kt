package com.example.model

/**
 * Detailed latency audit item for a clause.
 */
data class LatencyRecord(
    val clauseId: Long,
    val danishText: String,
    val russianText: String,
    val asrCommitMs: Long,
    val translationDoneMs: Long,
    val ttsFirstAudioMs: Long,
    val asrToTransMs: Long,
    val transToTtsMs: Long,
    val totalLatencyMs: Long,
    val speechRate: Float,
    val wasSkipped: Boolean
) {
    fun toCsvRow(): String {
        val safeDa = danishText.replace("\"", "\"\"")
        val safeRu = russianText.replace("\"", "\"\"")
        return "$clauseId,\"$safeDa\",\"$safeRu\",$asrCommitMs,$translationDoneMs,$ttsFirstAudioMs,$asrToTransMs,$transToTtsMs,$totalLatencyMs,$speechRate,$wasSkipped"
    }

    companion object {
        const val CSV_HEADER = "clause_id,danish_text,russian_text,asr_commit_ms,translation_done_ms,tts_first_audio_ms,asr_to_trans_ms,trans_to_tts_ms,total_latency_ms,speech_rate,was_skipped"
    }
}

data class PipelineMetrics(
    val totalClausesCount: Int = 0,
    val medianLatencyMs: Long = 0L,
    val p90LatencyMs: Long = 0L,
    val minLatencyMs: Long = 0L,
    val maxLatencyMs: Long = 0L,
    val skippedClausesCount: Int = 0,
    val currentRate: Float = 1.15f,
    val queueDurationSec: Float = 0.0f,
    val queueClausesCount: Int = 0
)
