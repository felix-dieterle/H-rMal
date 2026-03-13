package com.felix.hormal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for false-click detection during inter-tone delays.
 *
 * An inter-tone delay is the silent pause between tones (1–4 s) while the
 * test is waiting before playing the next tone.  Previously these clicks were
 * silently ignored, allowing a user to click repeatedly at regular intervals
 * (e.g. every 500 ms) without any error being detected.
 *
 * With the fix, a click during [isInterToneDelay] == true is treated the same
 * way as a click during a counter-check or pre-confirmation pause: it is
 * counted as a false click and aborts the test after [MAX_FALSE_CLICKS].
 */
class InterToneDelayTest {

    companion object {
        private const val MAX_FALSE_CLICKS = 3
    }

    // ---------------------------------------------------------------------------
    // State machine helpers (mirror TestActivity logic without Android dependencies)
    // ---------------------------------------------------------------------------

    private data class TestState(
        val isInterToneDelay: Boolean = false,
        val waitingForResponse: Boolean = false,
        val isCounterCheckActive: Boolean = false,
        val isPreConfirmationPause: Boolean = false,
        val testRunning: Boolean = true,
        val falseClickCount: Int = 0,
        val aborted: Boolean = false
    )

    /**
     * Simulates the click-handler branching logic from TestActivity.
     *
     * A click is a "false click during inter-tone delay" only when:
     *   - testRunning == true
     *   - isInterToneDelay == true
     *   - no other active state (counter-check, pre-confirmation pause, waiting for response)
     */
    private fun simulateClick(state: TestState): TestState {
        return when {
            state.isCounterCheckActive -> {
                // onFalseClick() — counter-check false click (not under test here)
                val newCount = state.falseClickCount + 1
                state.copy(
                    isCounterCheckActive = false,
                    waitingForResponse = false,
                    falseClickCount = newCount,
                    aborted = newCount >= MAX_FALSE_CLICKS
                )
            }
            state.isPreConfirmationPause -> {
                // onFalseClickDuringPause() — not under test here
                val newCount = state.falseClickCount + 1
                state.copy(
                    isPreConfirmationPause = false,
                    falseClickCount = newCount,
                    aborted = newCount >= MAX_FALSE_CLICKS
                )
            }
            state.waitingForResponse -> {
                // onHeardResponse() or onConfirmationHeard() — valid click
                state.copy(waitingForResponse = false)
            }
            state.testRunning && state.isInterToneDelay -> {
                // onFalseClickDuringInterToneDelay() — THE NEW BEHAVIOUR
                val newCount = state.falseClickCount + 1
                state.copy(
                    isInterToneDelay = false,
                    falseClickCount = newCount,
                    aborted = newCount >= MAX_FALSE_CLICKS
                )
            }
            else -> state // click ignored
        }
    }

    // ---------------------------------------------------------------------------
    // New behaviour: clicks during inter-tone delay are counted as false clicks
    // ---------------------------------------------------------------------------

    @Test
    fun clickDuringInterToneDelay_isCountedAsFalseClick() {
        val state = TestState(isInterToneDelay = true)
        val result = simulateClick(state)
        assertEquals(1, result.falseClickCount)
    }

    @Test
    fun clickDuringInterToneDelay_clearsInterToneDelayFlag() {
        val state = TestState(isInterToneDelay = true)
        val result = simulateClick(state)
        assertFalse("isInterToneDelay must be cleared after false click", result.isInterToneDelay)
    }

    @Test
    fun clickDuringInterToneDelay_doesNotAbortBeforeMax() {
        val state = TestState(isInterToneDelay = true)
        val result = simulateClick(state)
        assertFalse("Test should not abort on first inter-tone false click", result.aborted)
    }

    @Test
    fun clickDuringInterToneDelay_atMaxThreshold_abortsTest() {
        // Start with MAX_FALSE_CLICKS - 1 prior false clicks, then click during delay
        var state = TestState(isInterToneDelay = true, falseClickCount = MAX_FALSE_CLICKS - 1)
        state = simulateClick(state)
        assertTrue("Test should abort when MAX_FALSE_CLICKS reached via inter-tone delay click", state.aborted)
        assertEquals(MAX_FALSE_CLICKS, state.falseClickCount)
    }

    @Test
    fun twentyRapidClicks_duringInterToneDelay_abortsAtMaxFalseClicks() {
        // Simulates the scenario from the bug report: user clicks 20 times at
        // regular intervals while isInterToneDelay is true.  The test must
        // abort as soon as MAX_FALSE_CLICKS is reached.
        var state = TestState(isInterToneDelay = true)
        var clicksProcessed = 0
        repeat(20) {
            if (state.aborted) return@repeat
            state = simulateClick(state.copy(isInterToneDelay = true))
            clicksProcessed++
        }
        assertTrue("Test should abort before all 20 clicks are processed", state.aborted)
        assertEquals("Exactly MAX_FALSE_CLICKS false clicks recorded", MAX_FALSE_CLICKS, state.falseClickCount)
        assertEquals(
            "Test aborted after exactly MAX_FALSE_CLICKS clicks",
            MAX_FALSE_CLICKS,
            clicksProcessed
        )
    }

    // ---------------------------------------------------------------------------
    // Old behaviour preserved: clicks are ignored when test is not in inter-tone delay
    // ---------------------------------------------------------------------------

    @Test
    fun click_whenTestNotRunning_isIgnored() {
        val state = TestState(testRunning = false, isInterToneDelay = true)
        val result = simulateClick(state)
        assertEquals("Click ignored when test is not running", 0, result.falseClickCount)
    }

    @Test
    fun click_whenNotInInterToneDelay_isIgnored() {
        // No active state at all (e.g. right after test starts before first scheduleNextTone)
        val state = TestState(
            isInterToneDelay = false,
            waitingForResponse = false,
            isCounterCheckActive = false,
            isPreConfirmationPause = false
        )
        val result = simulateClick(state)
        assertEquals("Click ignored when not in any active state", 0, result.falseClickCount)
    }

    @Test
    fun click_duringTonePlaying_isValidResponse_notFalseClick() {
        val state = TestState(waitingForResponse = true, isInterToneDelay = false)
        val result = simulateClick(state)
        assertEquals("Valid click during tone must not be a false click", 0, result.falseClickCount)
        assertFalse("waitingForResponse must be cleared after valid click", result.waitingForResponse)
    }

    // ---------------------------------------------------------------------------
    // Interaction: counter-check takes priority over inter-tone delay flag
    // ---------------------------------------------------------------------------

    @Test
    fun click_duringCounterCheck_usesCounterCheckHandler_notInterToneDelay() {
        // Both isCounterCheckActive and isInterToneDelay true (edge case):
        // counter-check handler must take priority.
        val state = TestState(isCounterCheckActive = true, isInterToneDelay = true)
        val result = simulateClick(state)
        assertFalse("Counter-check flag must be cleared", result.isCounterCheckActive)
        // The counter-check handler is what ran — inter-tone delay flag is irrelevant here
        assertEquals(1, result.falseClickCount)
    }
}
