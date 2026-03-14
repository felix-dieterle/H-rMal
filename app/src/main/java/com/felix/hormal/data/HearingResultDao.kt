package com.felix.hormal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HearingResultDao {
    @Query("SELECT * FROM hearing_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<HearingResult>>

    @Query("SELECT * FROM hearing_results ORDER BY timestamp DESC")
    suspend fun getAllResultsOnce(): List<HearingResult>

    @Insert
    suspend fun insert(result: HearingResult): Long

    @Delete
    suspend fun delete(result: HearingResult)

    @Query("SELECT * FROM hearing_results WHERE id = :id")
    suspend fun getById(id: Long): HearingResult?
}
