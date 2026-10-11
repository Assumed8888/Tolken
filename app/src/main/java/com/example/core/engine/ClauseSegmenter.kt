package com.example.core.engine

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

data class SegmentedClause(
    val id: Long,
    val rawDanishText: String,
    val normalizedDanishText: String,
    val asrCommitMs: Long,
    val speechEndMs: Long,
    val containsDigitsOrNames: Boolean
)

/**
 * Segmenter:
 * - Keeps stable prefix (words unchanged across 2 consecutive partials).
 * - Cuts a clause on:
 *    1) Pause >= 250 ms
 *    2) Comma
 *    3) Every 4-6 words (threshold)
 *    4) Speech final result
 * - Light normalization:
 *    - Strips fillers ("øh", "altså", "øhm", "eh", "um", "jamen")
 *    - Collapses consecutive duplicate words ("det det" -> "det")
 * - Translates each committed clause once, never retranslates.
 */
class ClauseSegmenter(
    var wordCountThreshold: Int = 5,
    var pauseThresholdMs: Long = 250L
) {
    companion object {
        private const val TAG = "TolkenSegmenter"

        private val DANISH_FILLERS = setOf(
            "øh", "øhm", "altså", "eh", "um", "jamen", "hm", "hmm"
        )
    }

    private val clauseIdGenerator = AtomicLong(1L)

    private var previousPartialWords: List<String> = emptyList()
    private var committedWordCountInCurrentUtterance = 0
    private var lastPartialTimestampMs: Long = 0L
    private var lastSpeechEndTimeMs: Long = 0L

    /**
     * Feed a partial ASR result string and timestamp.
     * Returns a list of newly committed clauses (if any).
     */
    @Synchronized
    fun onPartialResult(partialText: String, timestampMs: Long): List<SegmentedClause> {
        val currentWords = tokenize(partialText)
        if (currentWords.isEmpty()) return emptyList()

        val timeSinceLastPartial = if (lastPartialTimestampMs > 0) timestampMs - lastPartialTimestampMs else 0L
        lastPartialTimestampMs = timestampMs

        // Find stable prefix with previous partial
        val commonPrefixLength = findCommonPrefixLength(previousPartialWords, currentWords)
        previousPartialWords = currentWords

        // If nothing new is stable, check if pause threshold reached on existing stable prefix
        val newStableWords = if (commonPrefixLength > committedWordCountInCurrentUtterance) {
            currentWords.subList(committedWordCountInCurrentUtterance, commonPrefixLength)
        } else {
            emptyList()
        }

        if (newStableWords.isEmpty()) return emptyList()

        val committed = mutableListOf<SegmentedClause>()
        var chunk = mutableListOf<String>()

        for (i in newStableWords.indices) {
            val word = newStableWords[i]
            chunk.add(word)

            val endsWithComma = word.endsWith(",") || word.endsWith(";")
            val reachedLength = chunk.size >= wordCountThreshold
            val hasPause = timeSinceLastPartial >= pauseThresholdMs && chunk.size >= 3

            if (endsWithComma || reachedLength || hasPause) {
                val clause = createClause(chunk, timestampMs)
                if (clause != null) committed.add(clause)
                committedWordCountInCurrentUtterance += chunk.size
                chunk = mutableListOf()
            }
        }

        return committed
    }

    /**
     * Feed a final ASR result string. Any uncommitted words are flushed immediately.
     */
    @Synchronized
    fun onFinalResult(finalText: String, timestampMs: Long): List<SegmentedClause> {
        val words = tokenize(finalText)
        val committed = mutableListOf<SegmentedClause>()

        if (words.size > committedWordCountInCurrentUtterance) {
            val remainingWords = words.subList(committedWordCountInCurrentUtterance, words.size)
            if (remainingWords.isNotEmpty()) {
                // If remaining words are longer than threshold, split them into chunks of wordCountThreshold
                val chunks = remainingWords.chunked(wordCountThreshold)
                for (chunk in chunks) {
                    val clause = createClause(chunk, timestampMs)
                    if (clause != null) committed.add(clause)
                }
            }
        }

        // Reset utterance state
        resetUtteranceState()
        return committed
    }

    @Synchronized
    fun onEndOfSpeech(timestampMs: Long) {
        lastSpeechEndTimeMs = timestampMs
    }

    @Synchronized
    fun resetUtteranceState() {
        previousPartialWords = emptyList()
        committedWordCountInCurrentUtterance = 0
        lastPartialTimestampMs = 0L
        lastSpeechEndTimeMs = 0L
    }

    private fun findCommonPrefixLength(a: List<String>, b: List<String>): Int {
        val minSize = minOf(a.size, b.size)
        var i = 0
        while (i < minSize && a[i].equals(b[i], ignoreCase = true)) {
            i++
        }
        return i
    }

    private fun tokenize(text: String): List<String> {
        return text.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
    }

    private fun createClause(words: List<String>, timestampMs: Long): SegmentedClause? {
        val rawText = words.joinToString(" ")
        val normalized = normalizeDanish(words)
        if (normalized.isBlank()) return null

        val containsDigitsOrNames = checkContainsDigitsOrNames(normalized)
        val speechEnd = if (lastSpeechEndTimeMs > 0) lastSpeechEndTimeMs else timestampMs

        return SegmentedClause(
            id = clauseIdGenerator.getAndIncrement(),
            rawDanishText = rawText,
            normalizedDanishText = normalized,
            asrCommitMs = timestampMs,
            speechEndMs = speechEnd,
            containsDigitsOrNames = containsDigitsOrNames
        )
    }

    /**
     * Light normalization before translation:
     * 1. Strip fillers: "øh", "altså", "øhm", "eh", "um", "jamen"
     * 2. Collapse repeats: "det det" -> "det", "vi vi" -> "vi"
     */
    fun normalizeDanish(words: List<String>): String {
        val result = mutableListOf<String>()
        var lastWordClean = ""

        for (word in words) {
            val cleanWord = word.trim().lowercase().replace("[,.?!;:]".toRegex(), "")

            // 1. Strip Danish fillers
            if (DANISH_FILLERS.contains(cleanWord)) {
                continue
            }

            // 2. Collapse consecutive repeats ("det det" -> "det")
            if (cleanWord.isNotEmpty() && cleanWord == lastWordClean) {
                continue
            }

            result.add(word)
            lastWordClean = cleanWord
        }

        return result.joinToString(" ").trim().trimEnd(',', ';', ':', '.')
    }

    /**
     * Detects if clause contains digits (e.g. 10, 2024) or capitalized proper names / acronyms.
     * Used by SpeedController to cap TTS speed at 1.3x for clear comprehension.
     */
    fun checkContainsDigitsOrNames(text: String): Boolean {
        if (text.any { it.isDigit() }) return true

        val words = text.split("\\s+".toRegex())
        // Ignore the very first word since it might be capitalized just because of sentence start
        for (i in 1 until words.size) {
            val w = words[i].replace("[,.?!;:]".toRegex(), "")
            if (w.isNotEmpty() && w.first().isUpperCase()) {
                return true
            }
        }
        return false
    }
}
