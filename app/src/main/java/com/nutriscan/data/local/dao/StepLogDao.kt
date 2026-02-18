package com.nutriscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutriscan.data.local.entity.StepLog
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for step log records.
 */
@Dao
interface StepLogDao {

    /** Observe today's step record reactively. Returns null if no record yet. */
    @Query("SELECT * FROM step_logs WHERE date = :date LIMIT 1")
    fun getStepLogByDate(date: String): Flow<StepLog?>

    /** Get today's record synchronously (for service use). */
    @Query("SELECT * FROM step_logs WHERE date = :date LIMIT 1")
    suspend fun getStepLogByDateSync(date: String): StepLog?

    /** Insert or replace a step log (upsert by primary key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stepLog: StepLog)

    /**
     * Atomically increment steps for a given date.
     * If no row exists yet, the caller should insert first.
     */
    @Query("""
        UPDATE step_logs 
        SET steps = steps + :additionalSteps,
            last_sensor_value = :sensorValue,
            last_updated = :timestamp
        WHERE date = :date
    """)
    suspend fun incrementSteps(
        date: String,
        additionalSteps: Int,
        sensorValue: Int,
        timestamp: Long
    ): Int  // returns rows affected

    /** Get step logs for a date range (inclusive), ordered newest first. */
    @Query("SELECT * FROM step_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getStepsForDateRange(startDate: String, endDate: String): Flow<List<StepLog>>

    /** Get total steps for a specific date (returns 0 if no record). */
    @Query("SELECT COALESCE(steps, 0) FROM step_logs WHERE date = :date")
    fun getStepCountByDate(date: String): Flow<Int>
}
