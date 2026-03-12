package com.felix.hormal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the result of a full bilateral hearing test.
 * [leftEar] and [rightEar] are arrays of threshold values (dB HL) for each of the 6 standard frequencies.
 * [measurements] is the full measurement series, encoded as a semicolon-separated list of
 * "ear,freq,dB,heard" entries (e.g. "L,250,40,1;R,500,30,0").
 */
@Entity(tableName = "hearing_results")
data class HearingResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ageGroup: String,
    val leftEar: String,   // JSON-encoded IntArray, e.g. "[15,10,10,15,20,30]"
    val rightEar: String,  // JSON-encoded IntArray
    val measurements: String = "",  // semicolon-separated measurement series
    val timestamp: Long = System.currentTimeMillis()
) {
    fun leftEarValues(): IntArray = parseEncodedArray(leftEar)
    fun rightEarValues(): IntArray = parseEncodedArray(rightEar)
    fun measurementList(): ArrayList<String> =
        if (measurements.isBlank()) ArrayList()
        else ArrayList(measurements.split(";").filter { it.isNotBlank() })

    private fun parseEncodedArray(encoded: String): IntArray =
        try {
            encoded.trim('[', ']').split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: NumberFormatException) {
            IntArray(6) { 40 } // fallback to default thresholds if data is corrupt
        }

    companion object {
        fun encodeArray(arr: IntArray): String = "[${arr.joinToString(",")}]"
        fun encodeMeasurements(list: ArrayList<String>): String = list.joinToString(";")
    }
}
