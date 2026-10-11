package com.example

import com.example.core.engine.ClauseSegmenter
import com.example.core.engine.SpeedController
import com.example.model.LatencyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClauseSegmenterTest {

    private val segmenter = ClauseSegmenter(wordCountThreshold = 5, pauseThresholdMs = 250L)

    @Test
    fun testFillerStripping() {
        val words = listOf("øh", "jeg", "tror", "altså", "det", "virker")
        val normalized = segmenter.normalizeDanish(words)
        assertEquals("jeg tror det virker", normalized)
    }

    @Test
    fun testCollapseRepeats() {
        val words = listOf("vi", "vi", "skal", "starte", "starte", "mødet")
        val normalized = segmenter.normalizeDanish(words)
        assertEquals("vi skal starte mødet", normalized)
    }

    @Test
    fun testDigitsAndNamesDetection() {
        assertFalse(segmenter.checkContainsDigitsOrNames("vi skal mødes i morgen"))
        assertTrue(segmenter.checkContainsDigitsOrNames("vi skal mødes klokken 10"))
        assertTrue(segmenter.checkContainsDigitsOrNames("jeg mødte Thomas i går"))
    }

    @Test
    fun testCommitOnComma() {
        segmenter.resetUtteranceState()
        // First partial
        segmenter.onPartialResult("hej med dig,", 1000L)
        // Second partial (confirms prefix)
        val committed = segmenter.onPartialResult("hej med dig, hvordan går det", 1100L)

        assertTrue(committed.isNotEmpty())
        assertEquals("hej med dig", committed.first().normalizedDanishText)
    }
}

class SpeedControllerTest {

    private val controller = SpeedController(
        minRate = 1.15f,
        maxRate = 2.0f,
        digitsAndNamesCap = 1.3f,
        queueSkipThresholdSec = 4.0f
    )

    @Test
    fun testShortQueueReturnsOptimalRate() {
        val rate = controller.computeRate(queueDurationSeconds = 0.3f, hasDigitsOrNames = false)
        assertTrue(rate in 1.1f..1.25f)
    }

    @Test
    fun testMediumQueueAcceleratesRate() {
        controller.computeRate(queueDurationSeconds = 1.0f, hasDigitsOrNames = false)
        val rate = controller.computeRate(queueDurationSeconds = 1.0f, hasDigitsOrNames = false)
        assertTrue(rate in 1.25f..1.65f)
    }

    @Test
    fun testDigitsAndNamesCappedAt1_3() {
        // High queue duration that would normally yield >1.8x
        val rate = controller.computeRate(queueDurationSeconds = 3.0f, hasDigitsOrNames = true)
        assertTrue("Rate was $rate, should be <= 1.3f", rate <= 1.301f)
    }

    @Test
    fun testLowValueClauseDetection() {
        assertTrue(controller.isLowValueClause("да"))
        assertTrue(controller.isLowValueClause("хорошо, ладно"))
        assertFalse(controller.isLowValueClause("Мы должны начать встречу прямо сейчас"))
    }

    @Test
    fun testCsvFormatting() {
        val record = LatencyRecord(
            clauseId = 1L,
            danishText = "Kan du høre mig?",
            russianText = "Ты слышишь меня?",
            asrCommitMs = 1000L,
            translationDoneMs = 1200L,
            ttsFirstAudioMs = 1350L,
            asrToTransMs = 200L,
            transToTtsMs = 150L,
            totalLatencyMs = 350L,
            speechRate = 1.2f,
            wasSkipped = false
        )
        val csv = record.toCsvRow()
        assertTrue(csv.contains("Kan du høre mig?"))
        assertTrue(csv.contains("Ты слышишь меня?"))
        assertTrue(csv.contains("350"))
    }
}
