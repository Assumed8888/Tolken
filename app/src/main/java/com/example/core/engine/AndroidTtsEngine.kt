package com.example.core.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidTtsEngine(
    private val context: Context
) : TtsEngine {

    companion object {
        private const val TAG = "TolkenTTS"
        val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru-RU")
    }

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()

    private val activeCallbacks = ConcurrentHashMap<String, Triple<() -> Unit, () -> Unit, (String) -> Unit>>()

    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "TTS started utterance: $utteranceId")
            if (utteranceId != null) {
                activeCallbacks[utteranceId]?.first?.invoke()
            }
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "TTS finished utterance: $utteranceId")
            if (utteranceId != null) {
                activeCallbacks.remove(utteranceId)?.second?.invoke()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "TTS error on utterance: $utteranceId")
            if (utteranceId != null) {
                activeCallbacks.remove(utteranceId)?.third?.invoke("Playback error")
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "TTS error $errorCode on utterance: $utteranceId")
            if (utteranceId != null) {
                activeCallbacks.remove(utteranceId)?.third?.invoke("Playback error ($errorCode)")
            }
        }
    }

    override fun initialize(onReady: (() -> Unit)?) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val currentTts = tts ?: return@TextToSpeech
                val result = currentTts.setLanguage(RUSSIAN_LOCALE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Russian TTS language not supported or missing data")
                } else {
                    Log.i(TAG, "Russian TTS successfully initialized")
                    // Configure audio attributes for voice communication / low-latency
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    currentTts.setAudioAttributes(audioAttributes)

                    // Find and prefer offline Russian voice
                    selectOfflineRussianVoice(currentTts)
                }

                currentTts.setOnUtteranceProgressListener(utteranceProgressListener)
                _isReady.value = true
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS Initialization failed: status $status")
                _isReady.value = false
            }
        }
    }

    private fun selectOfflineRussianVoice(currentTts: TextToSpeech) {
        try {
            val voices = currentTts.voices
            if (!voices.isNullOrEmpty()) {
                val russianVoices = voices.filter { it.locale.language == "ru" }
                val offlineVoice = russianVoices.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired && voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
                } ?: russianVoices.firstOrNull()

                if (offlineVoice != null) {
                    currentTts.voice = offlineVoice
                    Log.i(TAG, "Selected Russian TTS voice: ${offlineVoice.name} (latency=${offlineVoice.latency})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error selecting custom Russian voice", e)
        }
    }

    override fun speak(
        utteranceId: String,
        text: String,
        speechRate: Float,
        queueMode: Int,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTts = tts
        if (currentTts == null || !_isReady.value) {
            Log.w(TAG, "TTS not ready to speak")
            onError("TTS engine not ready")
            return
        }

        activeCallbacks[utteranceId] = Triple(onStart, onDone, onError)
        currentTts.setSpeechRate(speechRate)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        currentTts.speak(text, queueMode, params, utteranceId)
    }

    override fun stop() {
        activeCallbacks.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    override fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        _isReady.value = false
    }
}
