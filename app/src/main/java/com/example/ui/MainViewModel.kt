package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TolkenManager
import com.example.core.engine.AudioRouteStatus
import com.example.core.engine.ModelDownloadState
import com.example.core.engine.PipelineCoordinator
import com.example.model.Clause
import com.example.model.LatencyRecord
import com.example.model.PipelineMetrics
import com.example.model.SettingsState
import com.example.model.TestPhrase
import com.example.service.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator: PipelineCoordinator = TolkenManager.getCoordinator(application)

    val isRunning: StateFlow<Boolean> = coordinator.isRunning
    val liveDanishPartial: StateFlow<String> = coordinator.liveDanishPartial
    val liveRussianLatest: StateFlow<String> = coordinator.liveRussianLatest
    val micRmsDb: StateFlow<Float> = coordinator.micRmsDb
    val clauses: StateFlow<List<Clause>> = coordinator.clauses
    val metrics: StateFlow<PipelineMetrics> = coordinator.metrics
    val latencyRecords: StateFlow<List<LatencyRecord>> = coordinator.latencyRecords
    val settings: StateFlow<SettingsState> = coordinator.settings
    val modelDownloadState: StateFlow<ModelDownloadState> = coordinator.translatorEngine.modelState
    val audioRouteStatus: StateFlow<AudioRouteStatus> = coordinator.audioRouter.routeStatus
    val currentSpeaker = coordinator.currentSpeaker
    val isUserSpeaking = coordinator.isUserSpeaking
    val filteredClausesCount = coordinator.filteredClausesCount

    private val _testPhrases = MutableStateFlow<List<TestPhrase>>(TestPhrase.defaultPhrases())
    val testPhrases = _testPhrases.asStateFlow()

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning = _isTestRunning.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.translatorEngine.checkOrDownloadModels()
        }
    }

    fun toggleListening(context: Context) {
        if (isRunning.value) {
            TranslationService.stopService(context)
            coordinator.stopPipeline()
        } else {
            TranslationService.startService(context)
        }
    }

    fun updateSettings(newSettings: SettingsState) {
        coordinator.updateSettings(newSettings)
    }

    fun downloadModelNow() {
        viewModelScope.launch {
            coordinator.translatorEngine.checkOrDownloadModels()
        }
    }

    fun clearHistory() {
        coordinator.clearHistory()
    }

    fun shareCsvAudit(context: Context) {
        viewModelScope.launch {
            try {
                val csvFile = coordinator.saveCsvToFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    csvFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Tolken: Аудит задержки конвейера")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Экспорт CSV-лога задержки"))
            } catch (e: Exception) {
                // Fallback to plain text sharing if FileProvider authority isn't configured
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Tolken: Аудит задержки конвейера")
                    putExtra(Intent.EXTRA_TEXT, coordinator.exportCsvData())
                }
                context.startActivity(Intent.createChooser(intent, "Экспорт CSV-лога задержки"))
            }
        }
    }

    fun runAllTestPhrases() {
        if (_isTestRunning.value) return
        _isTestRunning.value = true

        viewModelScope.launch {
            val list = _testPhrases.value.map { it.copy() }
            for (i in list.indices) {
                val updated = coordinator.runTestPhrase(list[i])
                _testPhrases.value = list.toMutableList().also { it[i] = updated }
            }
            _isTestRunning.value = false
        }
    }

    fun runSingleTestPhrase(phrase: TestPhrase) {
        viewModelScope.launch {
            val updated = coordinator.runTestPhrase(phrase.copy())
            _testPhrases.value = _testPhrases.value.map {
                if (it.id == phrase.id) updated else it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
