package com.felix.hormal.model

/**
 * Standard audiometric test frequencies in Hz.
 */
val FREQUENCIES = intArrayOf(250, 500, 1000, 2000, 4000, 8000)

/**
 * Calculates an overall hearing score (0–100) for the given bilateral thresholds relative
 * to the age-group norms. Higher is better.
 *
 * For each tested frequency / ear combination the per-point score is:
 *   - 100  if the threshold is at or below the "very good" norm for that frequency
 *   -   0  if the threshold is ≥ the "critical" norm or no response (99 dB) was recorded
 *   - linear interpolation between 0 and 100 for values between the two norms
 *
 * The final score is the integer average over all included data points.
 * Frequencies with threshold == -1 (not tested in short mode) are excluded.
 */
fun calculateHearingScore(
    leftThresholds: IntArray,
    rightThresholds: IntArray,
    ageGroup: AgeGroup
): Int {
    val norms = AGE_THRESHOLDS[ageGroup] ?: return 0
    var totalScore = 0
    var count = 0

    for (i in FREQUENCIES.indices) {
        val left = leftThresholds.getOrElse(i) { -1 }
        val right = rightThresholds.getOrElse(i) { -1 }

        if (left == -1 || right == -1) continue  // not tested in short mode

        val vg = norms.veryGood[i]
        val crit = norms.critical[i]
        val range = crit - vg

        for (threshold in listOf(left, right)) {
            val freqScore = when {
                threshold == 99 -> 0
                threshold <= vg -> 100
                threshold >= crit || range <= 0 -> 0
                else -> 100 * (crit - threshold) / range
            }
            totalScore += freqScore
            count++
        }
    }

    return if (count > 0) totalScore / count else 0
}

/**
 * Resolves an [AgeGroup] from its persisted [name] string.
 * Falls back to [AgeGroup.YOUNG_ADULT_18_35] if the name cannot be matched
 * (e.g. after a database migration where an old enum value was renamed).
 */
fun resolveAgeGroup(name: String?): AgeGroup =
    AgeGroup.values().firstOrNull { it.name == name } ?: AgeGroup.YOUNG_ADULT_18_35

/**
 * Returns the emoji that represents the given [score] quality band:
 * - 🏆  80–100  (excellent)
 * - ✅  60–79   (good)
 * - ⚠️  40–59   (fair)
 * - 🔴   0–39   (poor)
 *
 * Centralised here so the thresholds are defined in exactly one place.
 *
 * @param score The overall hearing score in the range 0–100.
 */
fun scoreEmoji(score: Int): String = when {
    score >= 80 -> "🏆"
    score >= 60 -> "✅"
    score >= 40 -> "⚠️"
    else        -> "🔴"
}

enum class AgeGroup(val label: String) {
    CHILDREN_5_12("Children (5–12)"),
    TEEN_13_17("Teenagers (13–17)"),
    YOUNG_ADULT_18_35("Young Adults (18–35)"),
    ADULT_36_60("Adults (36–60)"),
    SENIOR_61_PLUS("Seniors (61+)")
}

/**
 * Thresholds in dB HL for each frequency in [FREQUENCIES] (250, 500, 1k, 2k, 4k, 8k Hz).
 * "veryGood"  = best-case hearing for the age group
 * "average"   = typical/median hearing for age
 * "critical"  = borderline significant hearing loss for age
 *
 * Sources: ISO 7029, clinical audiometry guidelines (simplified for app use).
 */
data class ThresholdLine(
    val veryGood: IntArray,   // dB HL – threshold line for "very good" hearing
    val average: IntArray,    // dB HL – threshold line for "average" hearing
    val critical: IntArray    // dB HL – threshold line for "critical" (hearing loss threshold)
)

val AGE_THRESHOLDS: Map<AgeGroup, ThresholdLine> = mapOf(
    AgeGroup.CHILDREN_5_12 to ThresholdLine(
        veryGood = intArrayOf(5, 5, 5, 5, 5, 5),
        average  = intArrayOf(15, 15, 15, 15, 15, 15),
        critical = intArrayOf(25, 25, 25, 25, 25, 25)
    ),
    AgeGroup.TEEN_13_17 to ThresholdLine(
        veryGood = intArrayOf(5, 5, 5, 5, 5, 5),
        average  = intArrayOf(15, 15, 15, 15, 15, 15),
        critical = intArrayOf(25, 25, 25, 25, 25, 25)
    ),
    AgeGroup.YOUNG_ADULT_18_35 to ThresholdLine(
        veryGood = intArrayOf(5, 5, 5, 5, 5, 5),
        average  = intArrayOf(15, 15, 15, 15, 20, 20),
        critical = intArrayOf(25, 25, 25, 25, 30, 35)
    ),
    AgeGroup.ADULT_36_60 to ThresholdLine(
        veryGood = intArrayOf(5,  5, 10, 10, 15, 20),
        average  = intArrayOf(15, 15, 15, 20, 30, 40),
        critical = intArrayOf(25, 25, 30, 35, 50, 65)
    ),
    AgeGroup.SENIOR_61_PLUS to ThresholdLine(
        veryGood = intArrayOf(10, 10, 15, 20, 30, 40),
        average  = intArrayOf(20, 20, 25, 35, 50, 65),
        critical = intArrayOf(35, 35, 45, 55, 70, 85)
    )
)
