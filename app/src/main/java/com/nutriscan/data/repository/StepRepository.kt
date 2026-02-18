package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.StepLogDao
import com.nutriscan.data.local.entity.StepLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for step count data.
 * Handles date formatting and provides clean APIs for the sensor layer and UI.
 */
@Singleton
class StepRepository @Inject constructor(
    private val stepLogDao: StepLogDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ============ QUERIES ============

    /** Observe today's step count reactively (emits 0 if no record). */
    fun getTodaySteps(): Flow<Int> {
        return stepLogDao.getStepLogByDate(todayDate())
            .map { it?.steps ?: 0 }
    }

    /** Observe today's full step log record. */
    fun getTodayStepLog(): Flow<StepLog?> {
        return stepLogDao.getStepLogByDate(todayDate())
    }

    /** Get step logs for the last 7 days. */
    fun getStepsForWeek(): Flow<List<StepLog>> {
        val today = todayDate()
        val sevenDaysAgo = dateFormat.format(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -6)
            }.time
        )
        return stepLogDao.getStepsForDateRange(sevenDaysAgo, today)
    }

    // ============ WRITES ============

    /**
     * Record steps for today. Tries atomic increment first;
     * if no row exists yet, inserts a new record.
     *
     * @param totalSteps   The new total step count for today.
     * @param sensorValue  Raw TYPE_STEP_COUNTER value (or -1 for accelerometer).
     */
    suspend fun recordSteps(totalSteps: Int, sensorValue: Int = -1) {
        val date = todayDate()
        val now = System.currentTimeMillis()
        val existing = stepLogDao.getStepLogByDateSync(date)

        if (existing != null) {
            stepLogDao.upsert(
                existing.copy(
                    steps = totalSteps,
                    lastSensorValue = sensorValue,
                    lastUpdated = now
                )
            )
        } else {
            stepLogDao.upsert(
                StepLog(
                    date = date,
                    steps = totalSteps,
                    lastSensorValue = sensorValue,
                    lastUpdated = now
                )
            )
        }
    }

    /**
     * Add additional steps to today's total (used by sensor callbacks).
     * Creates a new record if none exists for today.
     *
     * @param additionalSteps  Number of new steps to add.
     * @param sensorValue      Raw TYPE_STEP_COUNTER value (or -1 for accelerometer).
     */
    suspend fun addSteps(additionalSteps: Int, sensorValue: Int = -1) {
        val date = todayDate()
        val now = System.currentTimeMillis()

        val rowsAffected = stepLogDao.incrementSteps(date, additionalSteps, sensorValue, now)
        if (rowsAffected == 0) {
            // No row for today yet — create one
            stepLogDao.upsert(
                StepLog(
                    date = date,
                    steps = additionalSteps,
                    lastSensorValue = sensorValue,
                    lastUpdated = now
                )
            )
        }
    }

    /**
     * Get the synchronous step log for a given date (used by the service
     * to read the last known sensor value after a restart).
     */
    suspend fun getStepLogForDate(date: String): StepLog? {
        return stepLogDao.getStepLogByDateSync(date)
    }

    // ============ HELPERS ============

    /** Returns today's date as "yyyy-MM-dd". */
    fun todayDate(): String = dateFormat.format(Date())
}
