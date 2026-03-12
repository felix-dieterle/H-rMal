package com.felix.hormal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the result of a full bilateral hearing test.
 * [leftEar] and [rightEar] are arrays of threshold values (dB HL) for each of the 6 standard frequencies.
 */
@Entity(tableName = "hearing_results")
data class HearingResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ageGroup: String,
    val leftEar: String,   // JSON-encoded IntArray, e.g. "[15,10,10,15,20,30]"
    val rightEar: String,  // JSON-encoded IntArray
    val timestamp: Long = System.currentTimeMillis()
) {
    fun leftEarValues(): IntArray = leftEar.trim('[', ']').split(",").map { it.trim().toInt() }.toIntArray()
    fun rightEarValues(): IntArray = rightEar.trim('[', ']').split(",").map { it.trim().toInt() }.toIntArray()

    companion object {
        fun encodeArray(arr: IntArray): String = "[${arr.joinToString(",")}]"
    }
}
