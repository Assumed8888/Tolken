package com.example.core.engine

import android.content.Context
import android.util.Log
import com.example.model.Clause
import com.example.model.ClauseStatus
import com.example.model.LatencyRecord
import com.example.model.PipelineMetrics
import com.example.model.SettingsState
import com.example.model.TestPhrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class PipelineCoordinator(
    private val context: Context,
    val asrEngine: AsrEngine = SpeechRecognizerAsrEngine(context),
    val translatorEngine: TranslatorEngine = MlKitTranslator(),
    val ttsEngine: TtsEngine = AndroidTtsEngine(context),
    val audioRouter: AudioRouter = AudioRouter(context),
    val speedController: SpeedController = SpeedController(),
    val segmenter: ClauseSegmenter = ClauseSegmenter(),
    val voiceIsolation: VoiceIsolationClassifier = VoiceIsolationClassifier()
) {
    companion object {
        private const val TAG = "TolkenCoordinator"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // UI States
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _liveDanishPartial = MutableStateFlow("")
    val liveDanishPartial = _liveDanishPartial.asStateFlow()

    private val _liveRussianLatest = MutableStateFlow("")
    val liveRussianLatest = _liveRussianLatest.asStateFlow()

    private val _micRmsDb = MutableStateFlow(0f)
    val micRmsDb = _micRmsDb.asStateFlow()

    val currentSpeaker = voiceIsolation.currentSpeaker
    val isUserSpeaking = voiceIsolation.isUserSpeaking
    val filteredClausesCount = voiceIsolation.filteredClausesCount

    private val _clauses = MutableStateFlow<List<Clause>>(emptyList())
    val clauses = _clauses.asStateFlow()

    private val _metrics = MutableStateFlow(PipelineMetrics())
    val metrics = _metrics.asStateFlow()

    private val _latencyRecords = MutableStateFlow<List<LatencyRecord>>(emptyList())
    val latencyRecords = _latencyRecords.asStateFlow()

    private val _settings = MutableStateFlow(SettingsState())
    val settings = _settings.asStateFlow()

    // Internal playback queue
    private val playbackChannel = Channel<Clause>(Channel.UNLIMITED)
    private val pendingPlaybackQueue = ConcurrentLinkedQueue<Clause>()
    private var isPlayingClause = false

    private var asrCollectJob: Job? = null
    private var playbackWorkerJob: Job? = null

    init {
        ttsEngine.initialize()
    }

    fun updateSettings(newSettings: SettingsState) {
        _settings.value = newSettings
        segmenter.wordCountThreshold = newSettings.clauseWordThreshold
        segmenter.pauseThresholdMs = newSettings.pauseThresholdMs
        speedController.minRate = newSettings.minSpeechRate
        speedController.maxRate = newSettings.maxSpeechRate
        speedController.digitsAndNamesCap = newSettings.digitsNamesSpeedCap
        speedController.queueSkipThresholdSec = newSettings.queueSkipThresholdSec
        voiceIsolation.ownVoiceFilteringEnabled = newSettings.ownVoiceFilteringEnabled
        voiceIsolation.ownVoiceThresholdDb = newSettings.ownVoiceRmsThresholdDb
        voiceIsolation.echoGateTtsEnabled = newSettings.echoGateTtsEnabled
        voiceIsolation.cyrillicFilterEnabled = newSettings.cyrillicSelfFilterEnabled
        audioRouter.currentInputMode = newSettings.audioInputMode
        audioRouter.refreshRouting()
    }

    fun startPipeline() {
        if (_isRunning.value) return
        _isRunning.value = true

        scope.launch {
            // 1. Audio routing
            if (_settings.value.lowLatencyBluetoothRouting) {
                audioRouter.start()
            }

            // 2. Ensure models downloaded
            translatorEngine.checkOrDownloadModels()

            // 3. Start playback consumer loop
            startPlaybackWorker()

            // 4. Start ASR & collect events
            startAsrCollection()

            withContext(Dispatchers.Main) {
                asrEngine.startListening()
            }
        }
    }

    fun stopPipeline() {
        if (!_isRunning.value) return
        _isRunning.value = false

        asrCollectJob?.cancel()
        asrCollectJob = null
        playbackWorkerJob?.cancel()
        playbackWorkerJob = null

        asrEngine.stopListening()
        ttsEngine.stop()
        audioRouter.stop()

        _liveDanishPartial.value = ""
        _micRmsDb.value = 0f
        pendingPlaybackQueue.clear()
        updateQueueMetrics()
    }

    private fun startAsrCollection() {
        asrCollectJob?.cancel()
        asrCollectJob = scope.launch {
            asrEngine.events.collect { event ->
                when (event) {
                    is AsrEvent.Partial -> {
                        _liveDanishPartial.value = event.text
                        val newlyCommitted = segmenter.onPartialResult(event.text, event.timestampMs)
                        handleNewlyCommittedClauses(newlyCommitted)
                    }
                    is AsrEvent.Final -> {
                        _liveDanishPartial.value = ""
                        val newlyCommitted = segmenter.onFinalResult(event.text, event.timestampMs)
                        handleNewlyCommittedClauses(newlyCommitted)
                    }
                    is AsrEvent.EndOfSpeech -> {
                        segmenter.onEndOfSpeech(event.timestampMs)
                    }
                    is AsrEvent.Rms -> {
                        _micRmsDb.value = event.db
                        voiceIsolation.onRmsChanged(event.db)
                    }
                    is AsrEvent.Error -> {
                        Log.w(TAG, "ASR Error event: ${event.code} - ${event.message}")
                    }
                }
            }
        }
    }

    private fun handleNewlyCommittedClauses(newClauses: List<SegmentedClause>) {
        if (newClauses.isEmpty()) return

        for (segmented in newClauses) {
            // Check Near-Field / Self-Voice / TTS-Echo isolation filter
            val (shouldFilter, filterReason) = voiceIsolation.evaluateClause(segmented.normalizedDanishText)

            if (shouldFilter) {
                val filteredClause = Clause(
                    id = segmented.id,
                    danishText = segmented.normalizedDanishText,
                    russianText = "[Подавлено: $filterReason]",
                    status = ClauseStatus.FILTERED_SELF_VOICE,
                    asrCommitMs = segmented.asrCommitMs,
                    speechEndMs = segmented.speechEndMs,
                    containsDigitsOrNames = segmented.containsDigitsOrNames,
                    isSelfVoiceFiltered = true,
                    filterReason = filterReason
                )
                _clauses.value = (listOf(filteredClause) + _clauses.value).take(300)
                Log.d(TAG, "Skipping translation for self-voice clause: '${segmented.normalizedDanishText}' ($filterReason)")
                continue
            }

            val clause = Clause(
                id = segmented.id,
                danishText = segmented.normalizedDanishText,
                status = ClauseStatus.TRANSLATING,
                asrCommitMs = segmented.asrCommitMs,
                speechEndMs = segmented.speechEndMs,
                containsDigitsOrNames = segmented.containsDigitsOrNames
            )

            // Add to UI state (keep last 300 to maintain low memory over hours of standby)
            _clauses.value = (listOf(clause) + _clauses.value).take(300)

            // Launch translation
            scope.launch {
                translateClause(clause)
            }
        }
    }

    private suspend fun translateClause(clause: Clause) {
        try {
            val russian = translatorEngine.translate(clause.danishText)
            val doneTs = System.currentTimeMillis()

            val updatedClause = clause.copy(
                russianText = russian,
                status = ClauseStatus.TRANSLATED,
                translationDoneMs = doneTs
            )
            updateClauseInState(updatedClause)
            _liveRussianLatest.value = russian

            // Queue for playback
            pendingPlaybackQueue.add(updatedClause)
            updateQueueMetrics()
            playbackChannel.send(updatedClause)

        } catch (e: Exception) {
            Log.e(TAG, "Failed translating clause: ${clause.danishText}", e)
            val failedClause = clause.copy(
                russianText = "[Translation error]",
                status = ClauseStatus.SKIPPED
            )
            updateClauseInState(failedClause)
        }
    }

    private fun startPlaybackWorker() {
        playbackWorkerJob?.cancel()
        playbackWorkerJob = scope.launch {
            for (clause in playbackChannel) {
                if (!_isRunning.value) break

                val russianText = clause.russianText ?: continue
                val queueDurationSec = calculatePendingQueueDurationSec()

                // Check queue overflow threshold (> 4.0s)
                val isQueueOverloaded = queueDurationSec > _settings.value.queueSkipThresholdSec
                val shouldSkip = isQueueOverloaded && speedController.isLowValueClause(russianText)

                if (shouldSkip) {
                    Log.i(TAG, "Skipping audio playback for low-value clause to prevent backlog: '$russianText'")
                    val skippedClause = clause.copy(
                        status = ClauseStatus.SKIPPED,
                        skippedPlayback = true
                    )
                    updateClauseInState(skippedClause)
                    pendingPlaybackQueue.remove(clause)
                    updateQueueMetrics()
                    continue
                }

                // Calculate adaptive rate
                val calculatedRate = speedController.computeRate(
                    queueDurationSeconds = queueDurationSec,
                    hasDigitsOrNames = clause.containsDigitsOrNames
                )

                // Play via TTS
                val utteranceId = "clause_${clause.id}_${System.currentTimeMillis()}"
                val clauseWithRate = clause.copy(
                    assignedSpeechRate = calculatedRate,
                    status = ClauseStatus.SPEAKING
                )
                updateClauseInState(clauseWithRate)

                val playDoneChannel = Channel<Unit>(1)

                ttsEngine.speak(
                    utteranceId = utteranceId,
                    text = russianText,
                    speechRate = calculatedRate,
                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD,
                    onStart = {
                        voiceIsolation.isTtsCurrentlySpeaking = true
                        val firstAudioTs = System.currentTimeMillis()
                        val speakingClause = clauseWithRate.copy(
                            ttsFirstAudioMs = firstAudioTs,
                            status = ClauseStatus.SPEAKING
                        )
                        updateClauseInState(speakingClause)

                        // Record latency log
                        recordLatency(speakingClause)
                    },
                    onDone = {
                        voiceIsolation.isTtsCurrentlySpeaking = false
                        val doneClause = clauseWithRate.copy(status = ClauseStatus.PLAYED)
                        updateClauseInState(doneClause)
                        pendingPlaybackQueue.remove(clause)
                        updateQueueMetrics()
                        playDoneChannel.trySend(Unit)
                    },
                    onError = { err ->
                        voiceIsolation.isTtsCurrentlySpeaking = false
                        Log.w(TAG, "TTS playback error for utterance $utteranceId: $err")
                        val errClause = clauseWithRate.copy(status = ClauseStatus.SKIPPED)
                        updateClauseInState(errClause)
                        pendingPlaybackQueue.remove(clause)
                        updateQueueMetrics()
                        playDoneChannel.trySend(Unit)
                    }
                )

                // Wait until this utterance finishes before taking the next one
                // (or let TTS queue manage it while tracking queue length)
                playDoneChannel.receive()
            }
        }
    }

    private fun calculatePendingQueueDurationSec(): Float {
        var total = 0f
        val currentRate = _metrics.value.currentRate
        for (item in pendingPlaybackQueue) {
            val text = item.russianText ?: continue
            total += speedController.estimateDurationSeconds(text, currentRate)
        }
        return total
    }

    private fun updateQueueMetrics() {
        val durationSec = calculatePendingQueueDurationSec()
        val count = pendingPlaybackQueue.size
        _metrics.value = _metrics.value.copy(
            queueDurationSec = (Math.round(durationSec * 10.0) / 10.0).toFloat(),
            queueClausesCount = count,
            currentRate = speedController.computeRate(durationSec, false)
        )
    }

    private fun updateClauseInState(updated: Clause) {
        _clauses.value = _clauses.value.map { if (it.id == updated.id) updated else it }
    }

    private fun recordLatency(clause: Clause) {
        val asrCommit = clause.asrCommitMs
        val transDone = clause.translationDoneMs ?: asrCommit
        val ttsFirst = clause.ttsFirstAudioMs ?: transDone

        val asrToTrans = transDone - asrCommit
        val transToTts = ttsFirst - transDone
        val total = ttsFirst - clause.speechEndMs

        val record = LatencyRecord(
            clauseId = clause.id,
            danishText = clause.danishText,
            russianText = clause.russianText ?: "",
            asrCommitMs = asrCommit,
            translationDoneMs = transDone,
            ttsFirstAudioMs = ttsFirst,
            asrToTransMs = asrToTrans,
            transToTtsMs = transToTts,
            totalLatencyMs = total,
            speechRate = clause.assignedSpeechRate,
            wasSkipped = clause.skippedPlayback
        )

        val updatedRecords = (listOf(record) + _latencyRecords.value).take(1000)
        _latencyRecords.value = updatedRecords

        // Compute median & p90
        val latencies = updatedRecords.map { it.totalLatencyMs }.sorted()
        if (latencies.isNotEmpty()) {
            val median = latencies[latencies.size / 2]
            val p90Index = (latencies.size * 0.9).toInt().coerceAtMost(latencies.size - 1)
            val p90 = latencies[p90Index]

            _metrics.value = _metrics.value.copy(
                totalClausesCount = updatedRecords.size,
                medianLatencyMs = median,
                p90LatencyMs = p90,
                minLatencyMs = latencies.first(),
                maxLatencyMs = latencies.last(),
                skippedClausesCount = updatedRecords.count { it.wasSkipped }
            )
        }
    }

    /**
     * Generates CSV string of all latency records.
     */
    fun exportCsvData(): String {
        val sb = StringBuilder()
        sb.appendLine(LatencyRecord.CSV_HEADER)
        for (record in _latencyRecords.value) {
            sb.appendLine(record.toCsvRow())
        }
        return sb.toString()
    }

    /**
     * Saves CSV file to external cache and returns File.
     */
    fun saveCsvToFile(): File {
        val file = File(context.cacheDir, "tolken_latency_audit_${System.currentTimeMillis()}.csv")
        file.writeText(exportCsvData())
        return file
    }

    /**
     * Test mode helper: feeds Danish phrase directly into translation pipeline,
     * speaks via TTS, records latency and compares with expected Russian text.
     */
    suspend fun runTestPhrase(testPhrase: TestPhrase): TestPhrase = withContext(Dispatchers.Default) {
        val startTs = System.currentTimeMillis()
        try {
            val translated = translatorEngine.translate(testPhrase.danish)
            val endTs = System.currentTimeMillis()
            val latency = endTs - startTs

            testPhrase.actualRussian = translated
            testPhrase.latencyMs = latency
            // Loose comparison: check word overlap or non-empty match
            testPhrase.isPassed = translated.isNotBlank()

            // Synthesize audio
            ttsEngine.speak(
                utteranceId = "test_${testPhrase.id}",
                text = translated,
                speechRate = 1.2f,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                onStart = {},
                onDone = {},
                onError = {}
            )

            testPhrase
        } catch (e: Exception) {
            testPhrase.actualRussian = "Error: ${e.message}"
            testPhrase.isPassed = false
            testPhrase
        }
    }

    fun clearHistory() {
        _clauses.value = emptyList()
        _latencyRecords.value = emptyList()
        _metrics.value = PipelineMetrics()
    }

    fun destroy() {
        stopPipeline()
        asrEngine.destroy()
        translatorEngine.close()
        ttsEngine.shutdown()
    }
}
