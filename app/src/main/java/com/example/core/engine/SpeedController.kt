package com.example.core.engine

import android.util.Log

class SpeedController(
    var minRate: Float = 1.15f,
    var maxRate: Float = 2.0f,
    var digitsAndNamesCap: Float = 1.3f,
    var queueSkipThresholdSec: Float = 4.0f
) {
    companion object {
        private const val TAG = "TolkenSpeedCtrl"
        // Typical Russian speech rate is ~13-14 characters per second at 1.0x
        private const val CHARACTERS_PER_SECOND_1X = 13.5f

        private val LOW_VALUE_RUSSIAN_WORDS = setOf(
            "да", "нет", "ага", "ну", "хорошо", "ладно", "понятно", "ясно",
            "так", "вот", "что", "это", "эм", "гм", "значит", "короче"
        )
    }

    private var currentRate: Float = minRate

    /**
     * Estimates audio playback duration in seconds for a given Russian text at a given rate.
     */
    fun estimateDurationSeconds(russianText: String, rate: Float): Float {
        val clean = russianText.trim()
        if (clean.isEmpty()) return 0.0f
        val chars = clean.length
        val rawSec = chars / CHARACTERS_PER_SECOND_1X
        // Minimum utterance time ~0.3s
        return maxOf(0.3f, rawSec) / maxOf(0.5f, rate)
    }

    /**
     * Compute next adaptive speech rate based on pending queue duration.
     * Applies smooth transition and checks digits/names constraint.
     */
    fun computeRate(
        queueDurationSeconds: Float,
        hasDigitsOrNames: Boolean
    ): Float {
        // Target rate rule:
        // queue < 0.5s -> 1.1 - 1.2 (default 1.15)
        // 0.5 - 1.5s -> ~1.5
        // > 1.5s -> 1.8 - 2.0
        val targetRate = when {
            queueDurationSeconds < 0.5f -> minRate
            queueDurationSeconds in 0.5f..1.5f -> {
                // Interpolate 1.2 to 1.6
                val fraction = (queueDurationSeconds - 0.5f) / 1.0f
                1.2f + fraction * 0.4f
            }
            else -> {
                // Interpolate 1.6 to maxRate (up to 2.0)
                val fraction = minOf(1.0f, (queueDurationSeconds - 1.5f) / 2.5f)
                1.6f + fraction * (maxRate - 1.6f)
            }
        }

        // Smooth transition (lerp)
        val alpha = 0.35f
        currentRate += (targetRate - currentRate) * alpha
        currentRate = currentRate.coerceIn(minRate, maxRate)

        // Digits and names capped at 1.3 for clear comprehension
        val effectiveRate = if (hasDigitsOrNames) {
            minOf(currentRate, digitsAndNamesCap)
        } else {
            currentRate
        }

        return (Math.round(effectiveRate * 100.0) / 100.0).toFloat()
    }

    /**
     * Determines if a Russian clause is considered low-value
     * when the queue is overflowing (> 4.0 s) to show as text-only.
     */
    fun isLowValueClause(russianText: String): Boolean {
        val words = russianText.lowercase()
            .replace("[,.?!;:]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        if (words.size <= 2) return true

        val lowValueCount = words.count { LOW_VALUE_RUSSIAN_WORDS.contains(it) }
        return lowValueCount >= (words.size / 2)
    }
}
