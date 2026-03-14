package com.felix.hormal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates the adaptive staircase algorithm used in TestActivity to verify
 * that the test keeps descending until the user genuinely cannot hear,
 * rather than stopping after two consecutive correct responses.
 *
 * The algorithm uses a large initial ascending step ([INITIAL_ASCENDING_STEP_DB] = 20 dB)
 * when still searching for the first audible level, then a standard 10 dB descending step.
 * This reduces the total tone count by up to 50% for high hearing thresholds (≥ 60 dB HL).
 */
class StaircaseAlgorithmTest {

    companion object {
        private const val MIN_DB = -10
        private const val MAX_DB = 90
        private const val START_DB = 40
        private const val STEP_DB = 10
        /** Must match INITIAL_ASCENDING_STEP_DB in TestActivity. */
        private const val INITIAL_ASCENDING_STEP_DB = 20
        private const val NO_RESPONSE_THRESHOLD = 99
    }

    /** Result of simulating the staircase algorithm. */
    private data class StaircaseResult(val threshold: Int, val toneCount: Int)

    /**
     * Simulates running the staircase algorithm with a predefined sequence of
     * heard/not-heard responses and returns the recorded threshold in dB together
     * with the number of tones that were presented.
     *
     * @param trueThresholdDb the dB level below which the simulated listener cannot hear
     */
    private fun simulateThreshold(trueThresholdDb: Int): StaircaseResult {
        var currentDb = START_DB
        var correctResponses = 0
        var recordedThreshold: Int? = null   // null = not yet recorded
        var toneCount = 0

        fun heard() = currentDb >= trueThresholdDb

        // Run up to 100 iterations to prevent infinite loops in tests
        repeat(100) {
            if (recordedThreshold != null) return@repeat
            toneCount++

            if (heard()) {
                // --- onHeardResponse() ---
                correctResponses++
                val nextDb = (currentDb - STEP_DB).coerceAtLeast(MIN_DB)
                if (nextDb == currentDb) {
                    // Already at minimum; record threshold
                    recordedThreshold = currentDb
                } else {
                    currentDb = nextDb
                }
            } else {
                // --- onNoResponse() ---
                val hadPriorResponse = correctResponses > 0
                correctResponses = 0
                when {
                    hadPriorResponse -> {
                        // Restore to last heard level
                        currentDb = (currentDb + STEP_DB).coerceAtMost(MAX_DB)
                        recordedThreshold = currentDb
                    }
                    currentDb >= MAX_DB -> recordedThreshold = NO_RESPONSE_THRESHOLD
                    else -> {
                        // Initial search phase: use the larger ascending step to reach the
                        // audible range faster (mirrors TestActivity.onNoResponse() else branch).
                        currentDb = (currentDb + INITIAL_ASCENDING_STEP_DB).coerceAtMost(MAX_DB)
                    }
                }
            }
        }

        return StaircaseResult(recordedThreshold ?: NO_RESPONSE_THRESHOLD, toneCount)
    }

    // ---------------------------------------------------------------------------
    // Threshold accuracy: all levels should still be found correctly
    // ---------------------------------------------------------------------------

    @Test
    fun threshold_atFloor_recordsMinimumDb() {
        // A listener who can hear at -10 dB should reach and record the floor
        assertEquals(MIN_DB, simulateThreshold(MIN_DB).threshold)
    }

    @Test
    fun threshold_at0dB_recordsCorrectly() {
        assertEquals(0, simulateThreshold(0).threshold)
    }

    @Test
    fun threshold_at10dB_recordsCorrectly() {
        assertEquals(10, simulateThreshold(10).threshold)
    }

    @Test
    fun threshold_at20dB_recordsCorrectly() {
        // Previously the bug caused this to stop at 30 dB after two consecutive
        // correct responses; it should now continue down and record 20 dB.
        assertEquals(20, simulateThreshold(20).threshold)
    }

    @Test
    fun threshold_at30dB_recordsCorrectly() {
        // Previously the test would "find" a 30 dB threshold even if the listener
        // could hear better; now it only stops here when they truly cannot go lower.
        assertEquals(30, simulateThreshold(30).threshold)
    }

    @Test
    fun threshold_atStartLevel_recordsCorrectly() {
        assertEquals(40, simulateThreshold(40).threshold)
    }

    @Test
    fun threshold_aboveStart_ascendsAndRecordsCorrectly() {
        assertEquals(50, simulateThreshold(50).threshold)
    }

    @Test
    fun threshold_at60dB_recordsCorrectly() {
        assertEquals(60, simulateThreshold(60).threshold)
    }

    @Test
    fun threshold_at70dB_recordsCorrectly() {
        assertEquals(70, simulateThreshold(70).threshold)
    }

    @Test
    fun threshold_at80dB_recordsCorrectly() {
        assertEquals(80, simulateThreshold(80).threshold)
    }

    @Test
    fun threshold_at90dB_recordsCorrectly() {
        assertEquals(MAX_DB, simulateThreshold(MAX_DB).threshold)
    }

    @Test
    fun threshold_atMax_recordsNoResponse() {
        // Listener cannot hear even at 90 dB → no-response sentinel value
        assertEquals(NO_RESPONSE_THRESHOLD, simulateThreshold(MAX_DB + 10).threshold)
    }

    // ---------------------------------------------------------------------------
    // Efficiency: the 20 dB initial ascending step reduces tone count for high
    // thresholds compared to the old 10 dB ascending-only approach.
    // ---------------------------------------------------------------------------

    @Test
    fun highThreshold_60dB_fewerTonesThanOldAlgorithm() {
        // With the previous 10 dB ascending step, a 60 dB threshold needed 4 tones.
        // With 20 dB ascending, it needs only 3 tones (40→60→50→record 60).
        val result = simulateThreshold(60)
        assertEquals(60, result.threshold)
        assertTrue("Expected ≤ 3 tones for 60 dB threshold; got ${result.toneCount}", result.toneCount <= 3)
    }

    @Test
    fun highThreshold_80dB_fewerTonesThanOldAlgorithm() {
        // With the previous 10 dB ascending step, an 80 dB threshold needed 6 tones.
        // With 20 dB ascending, it needs only 4 tones (40→60→80→70→record 80).
        val result = simulateThreshold(80)
        assertEquals(80, result.threshold)
        assertTrue("Expected ≤ 4 tones for 80 dB threshold; got ${result.toneCount}", result.toneCount <= 4)
    }

    @Test
    fun noResponse_fewerAscendingTones() {
        // With the previous 10 dB ascending step, confirming no-response needed 6 tones.
        // With 20 dB ascending, it needs only 4 tones (40→60→80→90 no-resp).
        val result = simulateThreshold(MAX_DB + 10)
        assertEquals(NO_RESPONSE_THRESHOLD, result.threshold)
        assertTrue("Expected ≤ 4 tones for no-response case; got ${result.toneCount}", result.toneCount <= 4)
    }

    @Test
    fun normalHearing_unaffectedByInitialAscendingStep() {
        // For thresholds ≤ 40 dB (normal/good hearing) the first tone at 40 dB is
        // always heard, so the ascending step is never used. Tone count is unchanged.
        listOf(-10, 0, 10, 20, 30, 40).forEach { threshold ->
            val result = simulateThreshold(threshold)
            assertEquals("Threshold $threshold dB should be found correctly", threshold, result.threshold)
        }
    }
}
