package com.example.model

enum class AudioInputMode {
    BLUETOOTH_HEADSET_MIC, // Takes sound from Bluetooth headset microphone (even if phone is in pocket)
    PHONE_BUILTIN_MIC      // Takes sound from phone microphone
}

data class SettingsState(
    val maxSpeechRate: Float = 2.0f,
    val minSpeechRate: Float = 1.15f,
    val clauseWordThreshold: Int = 5, // 4-6 words
    val pauseThresholdMs: Long = 250L,
    val queueSkipThresholdSec: Float = 4.0f,
    val audioDuckingEnabled: Boolean = true,
    val lowLatencyBluetoothRouting: Boolean = true,
    val digitsNamesSpeedCap: Float = 1.3f,
    // Voice Isolation & Own Voice Filtering:
    val audioInputMode: AudioInputMode = AudioInputMode.BLUETOOTH_HEADSET_MIC,
    val ownVoiceFilteringEnabled: Boolean = true,
    val ownVoiceRmsThresholdDb: Float = 6.5f, // RMS dB threshold above which sound is classified as user's near-field speech
    val echoGateTtsEnabled: Boolean = true, // Suppress incoming mic recognition during Russian TTS playback
    val cyrillicSelfFilterEnabled: Boolean = true // Suppress user responses containing Russian words
)

data class TestPhrase(
    val id: Int,
    val danish: String,
    val expectedRussian: String,
    var actualRussian: String? = null,
    var latencyMs: Long? = null,
    var isPassed: Boolean? = null
) {
    companion object {
        fun defaultPhrases(): List<TestPhrase> = listOf(
            TestPhrase(1, "Kan du høre mig?", "Ты меня слышишь?"),
            TestPhrase(2, "Vi skal starte mødet nu.", "Мы должны начать встречу сейчас."),
            TestPhrase(3, "Tjek venligst rapporten.", "Пожалуйста, проверьте отчет."),
            TestPhrase(4, "Hvad tid ankommer toget?", "Во сколько прибывает поезд?"),
            TestPhrase(5, "Vi ses i morgen på kontoret.", "Увидимся завтра в офисе."),
            TestPhrase(6, "Send mig venligst filen.", "Пожалуйста, отправьте мне файл."),
            TestPhrase(7, "Projektet er næsten færdigt.", "Проект почти завершен."),
            TestPhrase(8, "Jeg ringer tilbage om ti minutter.", "Я перезвоню через десять минут."),
            TestPhrase(9, "Er du klar til præsentationen?", "Ты готов к презентации?"),
            TestPhrase(10, "Godt arbejde med opgaven.", "Хорошая работа с задачей."),
            TestPhrase(11, "Mødet er flyttet til klokken to.", "Встреча перенесена на два часа."),
            TestPhrase(12, "Har du modtaget min e-mail?", "Ты получил мое электронное письмо?"),
            TestPhrase(13, "Vi har brug for mere tid.", "Нам нужно больше времени."),
            TestPhrase(14, "Det lyder som en god plan.", "Это звучит как хороший план."),
            TestPhrase(15, "Tak for din hjælp i dag.", "Спасибо за помощь сегодня.")
        )
    }
}
