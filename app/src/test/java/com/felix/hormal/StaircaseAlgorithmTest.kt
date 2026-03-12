package com.felix.hormal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Simulates the adaptive staircase algorithm used in TestActivity to verify
 * that the test keeps descending until the user genuinely cannot hear,
 * rather than stopping after two consecutive correct responses.
 */
class StaircaseAlgorithmTest {

    companion object {
        private const val MIN_DB = -10
        private const val MAX_DB = 90
        private const val START_DB = 40
        private const val STEP_DB = 10
        private const val NO_RESPONSE_THRESHOLD = 99
    }

    /**
     * Simulates running the staircase algorithm with a predefined sequence of
     * heard/not-heard responses and returns the recorded threshold in dB.
     *
     * @param trueThresholdDb the dB level below which the simulated listener cannot hear
     */
    private fun simulateThreshold(trueThresholdDb: Int): Int {
        var currentDb = START_DB
        var correctResponses = 0
        var recordedThreshold = NO_RESPONSE_THRESHOLD

        fun heard() = currentDb >= trueThresholdDb

        // Run up to 100 iterations to prevent infinite loops in tests
        repeat(100) {
            if (recordedThreshold != NO_RESPONSE_THRESHOLD) return@repeat

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
                    else -> currentDb = (currentDb + STEP_DB).coerceAtMost(MAX_DB)
                }
            }
        }

        return recordedThreshold
    }

    @Test
    fun threshold_atFloor_recordsMinimumDb() {
        // A listener who can hear at -10 dB should reach and record the floor
        assertEquals(MIN_DB, simulateThreshold(MIN_DB))
    }

    @Test
    fun threshold_at0dB_recordsCorrectly() {
        assertEquals(0, simulateThreshold(0))
    }

    @Test
    fun threshold_at10dB_recordsCorrectly() {
        assertEquals(10, simulateThreshold(10))
    }

    @Test
    fun threshold_at20dB_recordsCorrectly() {
        // Previously the bug caused this to stop at 30 dB after two consecutive
        // correct responses; it should now continue down and record 20 dB.
        assertEquals(20, simulateThreshold(20))
    }

    @Test
    fun threshold_at30dB_recordsCorrectly() {
        // Previously the test would "find" a 30 dB threshold even if the listener
        // could hear better; now it only stops here when they truly cannot go lower.
        assertEquals(30, simulateThreshold(30))
    }

    @Test
    fun threshold_atStartLevel_recordsCorrectly() {
        assertEquals(40, simulateThreshold(40))
    }

    @Test
    fun threshold_aboveStart_ascendsAndRecordsCorrectly() {
        assertEquals(50, simulateThreshold(50))
    }

    @Test
    fun threshold_atMax_recordsNoResponse() {
        // Listener cannot hear even at 90 dB → no-response sentinel value
        assertEquals(NO_RESPONSE_THRESHOLD, simulateThreshold(MAX_DB + 10))
    }
}
