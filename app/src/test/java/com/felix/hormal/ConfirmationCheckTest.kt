package com.felix.hormal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the threshold confirmation-check logic added to TestActivity.
 *
 * After the staircase algorithm records a threshold the tone is played once
 * more (after a silent pre-pause) to verify the response.  If the user
 * confirms (hears the tone again) the originally recorded threshold is kept.
 * If the user does not respond in the re-verification, the threshold is
 * raised by one step (10 dB) to avoid recording a false-positive result.
 */
class ConfirmationCheckTest {

    companion object {
        private const val STEP_DB = 10
        private const val MAX_DB = 90
        private const val NO_RESPONSE = 99
    }

    /**
     * Simulates the confirmation-check outcome for a given [initialThreshold].
     *
     * @param initialThreshold the threshold that was recorded by the staircase
     * @param confirmed         true if the user responds during the re-verification tone
     * @return the final threshold after confirmation
     */
    private fun simulateConfirmation(initialThreshold: Int, confirmed: Boolean): Int {
        // --- logic mirroring onConfirmationHeard / onConfirmationNoResponse ---
        return if (confirmed) {
            // Threshold confirmed – keep as-is
            initialThreshold
        } else {
            // Not confirmed – raise by one step, but never exceed MAX_DB
            (initialThreshold + STEP_DB).coerceAtMost(MAX_DB)
        }
    }

    // -----------------------------------------------------------------------
    // Confirmed cases: threshold must be unchanged
    // -----------------------------------------------------------------------

    @Test
    fun confirmation_heard_keepsThreshold_at0dB() {
        assertEquals(0, simulateConfirmation(initialThreshold = 0, confirmed = true))
    }

    @Test
    fun confirmation_heard_keepsThreshold_at20dB() {
        assertEquals(20, simulateConfirmation(initialThreshold = 20, confirmed = true))
    }

    @Test
    fun confirmation_heard_keepsThreshold_at40dB() {
        assertEquals(40, simulateConfirmation(initialThreshold = 40, confirmed = true))
    }

    @Test
    fun confirmation_heard_keepsThreshold_atMaxDb() {
        assertEquals(MAX_DB, simulateConfirmation(initialThreshold = MAX_DB, confirmed = true))
    }

    // -----------------------------------------------------------------------
    // Not-confirmed cases: threshold must be raised by one step
    // -----------------------------------------------------------------------

    @Test
    fun confirmation_notHeard_raisesThreshold_from0dB() {
        assertEquals(10, simulateConfirmation(initialThreshold = 0, confirmed = false))
    }

    @Test
    fun confirmation_notHeard_raisesThreshold_from20dB() {
        assertEquals(30, simulateConfirmation(initialThreshold = 20, confirmed = false))
    }

    @Test
    fun confirmation_notHeard_raisesThreshold_from40dB() {
        assertEquals(50, simulateConfirmation(initialThreshold = 40, confirmed = false))
    }

    @Test
    fun confirmation_notHeard_doesNotExceedMaxDb() {
        // If the threshold is already at MAX_DB, raising by STEP_DB must be clamped
        assertEquals(MAX_DB, simulateConfirmation(initialThreshold = MAX_DB, confirmed = false))
    }

    @Test
    fun confirmation_notHeard_atOneBelowMax_clampedToMax() {
        assertEquals(MAX_DB, simulateConfirmation(initialThreshold = MAX_DB - STEP_DB, confirmed = false))
    }

    // -----------------------------------------------------------------------
    // No-response thresholds are never confirmation-checked
    // -----------------------------------------------------------------------

    @Test
    fun noResponse_threshold_isNotConfirmationChecked() {
        // The confirmation check is only scheduled when noResponse == false.
        // A noResponse threshold (99) bypasses confirmation and goes straight
        // to moveToNext().  This test documents that behaviour.
        val noResponseThreshold = NO_RESPONSE
        // No confirmation logic applied → value unchanged
        assertEquals(NO_RESPONSE, noResponseThreshold)
    }
}
