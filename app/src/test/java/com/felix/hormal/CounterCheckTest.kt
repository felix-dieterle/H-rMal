package com.felix.hormal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the counter-check (silence interval) logic used in TestActivity.
 *
 * Counter-checks are silent periods inserted at regular frequency intervals.
 * If the user presses the button during a silent interval it counts as a
 * false click. After [MAX_FALSE_CLICKS] false clicks the test is aborted.
 */
class CounterCheckTest {

    companion object {
        private const val MAX_FALSE_CLICKS = 3
        private const val CHECK_INTERVAL = 1
        private const val TOTAL_FREQUENCIES = 6
    }

    // ---------------------------------------------------------------------------
    // False-click counting and abort logic
    // ---------------------------------------------------------------------------

    @Test
    fun noFalseClicks_doesNotAbort() {
        val result = simulateFalseClicks(clickCount = 0)
        assertFalse("Test should not abort with 0 false clicks", result.aborted)
        assertEquals(0, result.falseClickCount)
    }

    @Test
    fun oneFalseClick_doesNotAbort() {
        val result = simulateFalseClicks(clickCount = 1)
        assertFalse("Test should not abort after 1 false click", result.aborted)
        assertEquals(1, result.falseClickCount)
    }

    @Test
    fun twoFalseClicks_doesNotAbort() {
        val result = simulateFalseClicks(clickCount = 2)
        assertFalse("Test should not abort after 2 false clicks", result.aborted)
        assertEquals(2, result.falseClickCount)
    }

    @Test
    fun maxFalseClicks_abortsTest() {
        val result = simulateFalseClicks(clickCount = MAX_FALSE_CLICKS)
        assertTrue("Test should abort at MAX_FALSE_CLICKS ($MAX_FALSE_CLICKS)", result.aborted)
        assertEquals(MAX_FALSE_CLICKS, result.falseClickCount)
    }

    @Test
    fun moreThanMaxFalseClicks_abortsAtExactThreshold() {
        // Abort fires on the click that reaches the threshold, so extra clicks
        // beyond MAX should still report aborted with count == MAX_FALSE_CLICKS.
        val result = simulateFalseClicks(clickCount = MAX_FALSE_CLICKS + 2)
        assertTrue("Test should abort once MAX_FALSE_CLICKS is reached", result.aborted)
        assertEquals(MAX_FALSE_CLICKS, result.falseClickCount)
    }

    // ---------------------------------------------------------------------------
    // Counter-check insertion interval
    // ---------------------------------------------------------------------------

    @Test
    fun checksInserted_atCorrectInterval_bothEars() {
        // When freqIndex overflows (last frequency of each ear) the ear transition
        // resets completedSinceLastCheck without inserting a check, so each ear
        // contributes floor((TOTAL_FREQUENCIES - 1) / CHECK_INTERVAL) checks.
        val checksPerEar = (TOTAL_FREQUENCIES - 1) / CHECK_INTERVAL
        val expectedTotal = checksPerEar * 2
        assertEquals(
            "Expected $expectedTotal counter-checks for $TOTAL_FREQUENCIES frequencies with interval $CHECK_INTERVAL",
            expectedTotal,
            simulateCheckCount(TOTAL_FREQUENCIES, CHECK_INTERVAL)
        )
    }

    @Test
    fun checkInterval_1_insertsCheckAfterEveryFrequency() {
        // With interval=1 a check fires after every completed frequency except the
        // last one of each ear (the ear-transition resets without firing a check).
        val checksLeft = TOTAL_FREQUENCIES - 1
        val checksRight = TOTAL_FREQUENCIES - 1
        assertEquals(checksLeft + checksRight, simulateCheckCount(TOTAL_FREQUENCIES, interval = 1))
    }

    @Test
    fun checkInterval_greaterThanFrequencies_insertsNoCheck() {
        // If CHECK_INTERVAL > total frequencies, no counter-check fires during the test.
        assertEquals(0, simulateCheckCount(TOTAL_FREQUENCIES, interval = TOTAL_FREQUENCIES + 1))
    }

    // ---------------------------------------------------------------------------
    // Random delay range tests
    // ---------------------------------------------------------------------------

    /**
     * Mirrors randomInterToneDelayMs() from TestActivity.
     * Returns a random delay in the inter-tone range [1 000, 4 000] ms.
     */
    private fun randomInterToneDelayMs(): Long = (1000L..4000L).random()

    /**
     * Mirrors randomCounterCheckDurationMs() from TestActivity.
     * Returns a random delay in the counter-check range [3 000, 9 000] ms.
     */
    private fun randomCounterCheckDurationMs(): Long = (3000L..9000L).random()

    @Test
    fun randomInterToneDelay_isWithinExpectedRange() {
        repeat(200) {
            val delay = randomInterToneDelayMs()
            assertTrue("Inter-tone delay $delay ms is below minimum 1000 ms", delay >= 1000L)
            assertTrue("Inter-tone delay $delay ms exceeds maximum 4000 ms", delay <= 4000L)
        }
    }

    @Test
    fun randomCounterCheckDuration_isWithinExpectedRange() {
        repeat(200) {
            val duration = randomCounterCheckDurationMs()
            assertTrue("Counter-check duration $duration ms is below minimum 3000 ms", duration >= 3000L)
            assertTrue("Counter-check duration $duration ms exceeds maximum 9000 ms", duration <= 9000L)
        }
    }

    private data class FalseClickResult(val falseClickCount: Int, val aborted: Boolean)

    /** Simulates [clickCount] false clicks and returns the resulting state. */
    private fun simulateFalseClicks(clickCount: Int): FalseClickResult {
        var falseClickCount = 0
        var aborted = false
        repeat(clickCount) {
            if (aborted) return@repeat
            falseClickCount++
            if (falseClickCount >= MAX_FALSE_CLICKS) {
                aborted = true
            }
        }
        return FalseClickResult(falseClickCount, aborted)
    }

    /**
     * Simulates the counter-check insertion logic from TestActivity.moveToNext()
     * and returns how many counter-checks would be scheduled across a full test
     * (both ears, [totalFrequencies] frequencies each, [interval] as CHECK_INTERVAL).
     */
    private fun simulateCheckCount(totalFrequencies: Int, interval: Int): Int {
        var checksCount = 0

        // Simulate both ears
        repeat(2) { ear ->
            var completedSinceLastCheck = 0
            for (i in 0 until totalFrequencies) {
                completedSinceLastCheck++
                val freqIndexAfter = i + 1
                if (freqIndexAfter >= totalFrequencies) {
                    // End of this ear — reset for the ear switch (no check here)
                    completedSinceLastCheck = 0
                } else if (completedSinceLastCheck >= interval) {
                    checksCount++
                    completedSinceLastCheck = 0
                }
            }
        }
        return checksCount
    }
}
