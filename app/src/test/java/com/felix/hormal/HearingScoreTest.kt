package com.felix.hormal

import com.felix.hormal.model.AgeGroup
import com.felix.hormal.model.AGE_THRESHOLDS
import com.felix.hormal.model.FREQUENCIES
import com.felix.hormal.model.calculateHearingScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [calculateHearingScore].
 *
 * Key invariants checked:
 *  - Perfect hearing (at or below "very good" for every frequency) → 100
 *  - No response on every frequency (threshold == 99) → 0
 *  - Thresholds at the "critical" level still yield a meaningful, positive score
 *    (i.e. the score does NOT collapse to 0 at the clinical "critical" boundary)
 *  - Monotonicity: a worse threshold (higher dB) never yields a better score
 */
class HearingScoreTest {

    // ---------------------------------------------------------------------------
    // Helper: build a uniform threshold array filled with the given value.
    // ---------------------------------------------------------------------------
    private fun uniform(value: Int) = IntArray(FREQUENCIES.size) { value }

    // ---------------------------------------------------------------------------
    // Perfect hearing → 100
    // ---------------------------------------------------------------------------

    @Test
    fun perfectHearing_youngAdult_returns100() {
        val vg = AGE_THRESHOLDS[AgeGroup.YOUNG_ADULT_18_35]!!.veryGood
        // thresholds equal to veryGood → should score 100
        assertEquals(100, calculateHearingScore(vg, vg, AgeGroup.YOUNG_ADULT_18_35))
    }

    @Test
    fun belowVeryGood_youngAdult_returns100() {
        // thresholds well below the veryGood line
        val thresholds = uniform(0)
        assertEquals(100, calculateHearingScore(thresholds, thresholds, AgeGroup.YOUNG_ADULT_18_35))
    }

    // ---------------------------------------------------------------------------
    // No response → 0
    // ---------------------------------------------------------------------------

    @Test
    fun noResponse_allFrequencies_returns0() {
        val noResp = uniform(99)
        assertEquals(0, calculateHearingScore(noResp, noResp, AgeGroup.YOUNG_ADULT_18_35))
    }

    // ---------------------------------------------------------------------------
    // At the "critical" boundary the score must be > 0 (lenient scoring).
    // ---------------------------------------------------------------------------

    @Test
    fun atCriticalThreshold_youngAdult_scoreIsPositive() {
        val crit = AGE_THRESHOLDS[AgeGroup.YOUNG_ADULT_18_35]!!.critical
        val score = calculateHearingScore(crit, crit, AgeGroup.YOUNG_ADULT_18_35)
        assertTrue("Score at critical threshold should be > 0, was $score", score > 0)
    }

    @Test
    fun atCriticalThreshold_senior_scoreIsPositive() {
        val crit = AGE_THRESHOLDS[AgeGroup.SENIOR_61_PLUS]!!.critical
        val score = calculateHearingScore(crit, crit, AgeGroup.SENIOR_61_PLUS)
        assertTrue("Score at critical threshold for seniors should be > 0, was $score", score > 0)
    }

    // ---------------------------------------------------------------------------
    // Monotonicity: higher thresholds (worse hearing) → lower or equal score.
    // ---------------------------------------------------------------------------

    @Test
    fun monotonicity_youngAdult_worseThresholdNeverBetter() {
        val ageGroup = AgeGroup.YOUNG_ADULT_18_35
        var previousScore = 100
        for (db in intArrayOf(5, 15, 25, 40, 55, 70, 85, 99)) {
            val thresholds = uniform(db)
            val score = calculateHearingScore(thresholds, thresholds, ageGroup)
            assertTrue(
                "Score at $db dB ($score) should be ≤ score at previous level ($previousScore)",
                score <= previousScore
            )
            previousScore = score
        }
    }

    // ---------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------

    @Test
    fun mixedEars_oneEarBetter_returnsAverage() {
        val ageGroup = AgeGroup.YOUNG_ADULT_18_35
        val vg = AGE_THRESHOLDS[ageGroup]!!.veryGood
        val noResp = uniform(99)
        // One ear perfect, one ear no-response → average should be strictly between 0 and 100
        val score = calculateHearingScore(vg, noResp, ageGroup)
        assertTrue("Mixed ear score should be between 0 and 100, was $score", score in 1..99)
    }

    @Test
    fun allAgeGroups_atVeryGood_return100() {
        for (ageGroup in AgeGroup.values()) {
            val vg = AGE_THRESHOLDS[ageGroup]!!.veryGood
            val score = calculateHearingScore(vg, vg, ageGroup)
            assertEquals("Age group $ageGroup at veryGood should return 100", 100, score)
        }
    }

    @Test
    fun allAgeGroups_noResponse_return0() {
        val noResp = uniform(99)
        for (ageGroup in AgeGroup.values()) {
            val score = calculateHearingScore(noResp, noResp, ageGroup)
            assertEquals("Age group $ageGroup with no response should return 0", 0, score)
        }
    }
}
