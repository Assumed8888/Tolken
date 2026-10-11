package com.example.core.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class SpeakerSource {
    SILENCE,
    INTERLOCUTOR_DANISH,
    USER_NEAR_FIELD,
    TTS_PLAYBACK_ECHO
}

/**
 * Intelligent acoustic & phonetic classifier that distinguishes
 * the external Danish interlocutor (far-field, 1-2m) from the user's
 * own voice (near-field, 3-5cm from headset mic) and TTS audio leakage.
 */
class VoiceIsolationClassifier(
    var ownVoiceThresholdDb: Float = 6.5f,
    var ownVoiceFilteringEnabled: Boolean = true,
    var echoGateTtsEnabled: Boolean = true,
    var cyrillicFilterEnabled: Boolean = true,
    var hangoverMs: Long = 450L
) {
    companion object {
        private const val TAG = "TolkenVoiceIsolation"
        private val CYRILLIC_REGEX = Regex("[\\p{IsCyrillic}]")
    }

    private val _currentSpeaker = MutableStateFlow(SpeakerSource.SILENCE)
    val currentSpeaker = _currentSpeaker.asStateFlow()

    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking = _isUserSpeaking.asStateFlow()

    private val _filteredClausesCount = MutableStateFlow(0)
    val filteredClausesCount = _filteredClausesCount.asStateFlow()

    @Volatile
    var isTtsCurrentlySpeaking: Boolean = false

    @Volatile
    private var lastUserVoiceDetectedMs: Long = 0L

    @Volatile
    private var peakRmsInCurrentClause: Float = 0f

    @Synchronized
    fun onRmsChanged(rmsdB: Float) {
        if (rmsdB > peakRmsInCurrentClause) {
            peakRmsInCurrentClause = rmsdB
        }

        val now = System.currentTimeMillis()

        when {
            // 1. TTS is speaking into earpiece: Echo gate active
            isTtsCurrentlySpeaking && echoGateTtsEnabled -> {
                _currentSpeaker.value = SpeakerSource.TTS_PLAYBACK_ECHO
                _isUserSpeaking.value = false
            }

            // 2. High RMS power: Near-field user vocalization
            ownVoiceFilteringEnabled && rmsdB >= ownVoiceThresholdDb -> {
                lastUserVoiceDetectedMs = now
                _currentSpeaker.value = SpeakerSource.USER_NEAR_FIELD
                _isUserSpeaking.value = true
            }

            // 3. User voice hangover (trailing syllables / breath right after user speech)
            ownVoiceFilteringEnabled && (now - lastUserVoiceDetectedMs < hangoverMs) -> {
                _currentSpeaker.value = SpeakerSource.USER_NEAR_FIELD
                _isUserSpeaking.value = true
            }

            // 4. Normal acoustic energy: Interlocutor Danish speech
            rmsdB >= 0.5f -> {
                _currentSpeaker.value = SpeakerSource.INTERLOCUTOR_DANISH
                _isUserSpeaking.value = false
            }

            // 5. Silence / background noise
            else -> {
                _currentSpeaker.value = SpeakerSource.SILENCE
                _isUserSpeaking.value = false
            }
        }
    }

    /**
     * Evaluates a recognized clause text and determines if it should be
     * suppressed/ignored as user self-speech or TTS echo.
     */
    @Synchronized
    fun evaluateClause(clauseText: String): Pair<Boolean, String?> {
        val now = System.currentTimeMillis()
        val peakRms = peakRmsInCurrentClause
        peakRmsInCurrentClause = 0f // Reset for next clause

        // Check 1: TTS Echo
        if (isTtsCurrentlySpeaking && echoGateTtsEnabled) {
            _filteredClausesCount.value += 1
            Log.i(TAG, "Suppressed clause as TTS Echo: '$clauseText'")
            return Pair(true, "Эхо воспроизведения TTS")
        }

        // Check 2: Cyrillic / Russian words spoken by user
        if (cyrillicFilterEnabled && containsCyrillic(clauseText)) {
            _filteredClausesCount.value += 1
            Log.i(TAG, "Suppressed clause containing Russian/Cyrillic speech: '$clauseText'")
            return Pair(true, "Русская речь владельца")
        }

        // Check 3: Near-field high energy
        if (ownVoiceFilteringEnabled) {
            val wasRecentUserVoice = (now - lastUserVoiceDetectedMs < hangoverMs + 200L)
            if (peakRms >= ownVoiceThresholdDb || wasRecentUserVoice) {
                _filteredClausesCount.value += 1
                Log.i(TAG, "Suppressed near-field user speech (peak ${peakRms}dB >= threshold ${ownVoiceThresholdDb}dB): '$clauseText'")
                return Pair(true, "Голос владельца (близко к микрофону, ${String.format("%.1f", peakRms)} dB)")
            }
        }

        return Pair(false, null)
    }

    private fun containsCyrillic(text: String): Boolean {
        return CYRILLIC_REGEX.containsMatchIn(text)
    }

    fun resetClauseRms() {
        peakRmsInCurrentClause = 0f
    }
}
