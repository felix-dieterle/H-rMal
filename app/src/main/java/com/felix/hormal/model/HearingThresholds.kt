package com.felix.hormal.model

/**
 * Standard audiometric test frequencies in Hz.
 */
val FREQUENCIES = intArrayOf(250, 500, 1000, 2000, 4000, 8000)

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
