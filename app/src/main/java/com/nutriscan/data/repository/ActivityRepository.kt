package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.ActivityLogDao
import com.nutriscan.data.local.entity.ActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for activity recognition data.
 * Provides current activity state, timeline, and active duration calculations.
 */
@Singleton
class ActivityRepository @Inject constructor(
    private val activityLogDao: ActivityLogDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Log an activity transition event. */
    suspend fun logTransition(activityType: String, transitionType: String) {
        activityLogDao.insert(
            ActivityLog(
                activityType = activityType,
                transitionType = transitionType,
                date = todayDate()
            )
        )
    }

    /** Observe the current activity (most recent ENTER transition). */
    fun getCurrentActivity(): Flow<String> {
        return activityLogDao.getLatestActivity()
            .map { it?.activityType ?: "UNKNOWN" }
    }

    /** Get the current activity synchronously. */
    suspend fun getCurrentActivitySync(): String {
        return activityLogDao.getLatestActivitySync()?.activityType ?: "UNKNOWN"
    }

    /** Observe today's activity timeline. */
    fun getTodayTimeline(): Flow<List<ActivityLog>> {
        return activityLogDao.getActivitiesForDate(todayDate())
    }

    /** Observe activity timeline for a specific date. */
    fun getTimelineForDate(date: String): Flow<List<ActivityLog>> {
        return activityLogDao.getActivitiesForDate(date)
    }

    /**
     * Calculate total active minutes for today.
     * Active = any non-STILL, non-IN_VEHICLE activity.
     * Computes duration between ENTER and EXIT pairs (or now if still active).
     */
    fun getTodayActiveMinutes(): Flow<Long> {
        return activityLogDao.getActivitiesForDate(todayDate()).map { logs ->
            calculateActiveMinutes(logs)
        }
    }

    /**
     * Calculate total active minutes for a specific date.
     * For past dates, only counts completed ENTER/EXIT pairs.
     * For today, also counts in-progress active periods up to now.
     */
    fun getActiveMinutesForDate(date: String): Flow<Long> {
        return activityLogDao.getActivitiesForDate(date).map { logs ->
            calculateActiveMinutes(logs, isToday = date == todayDate())
        }
    }

    /** Get active minutes for a specific date synchronously. */
    suspend fun getActiveMinutesForDateSync(date: String): Long {
        val logs = activityLogDao.getActivitiesForDateSync(date)
        return calculateActiveMinutes(logs, isToday = date == todayDate())
    }

    /** Get all dates that have activity data. */
    fun getAllDatesWithActivity(): Flow<List<String>> {
        return activityLogDao.getAllDatesWithActivity()
    }

    /** Get activities for a date range. */
    fun getActivitiesForDateRange(startDate: String, endDate: String): Flow<List<ActivityLog>> {
        return activityLogDao.getActivitiesForDateRange(startDate, endDate)
    }

    // ============ CALCULATION ============

    private fun calculateActiveMinutes(logs: List<ActivityLog>, isToday: Boolean = true): Long {
        var totalMs = 0L
        var activeStartTime: Long? = null

        for (log in logs) {
            val isPhysicalActivity = log.activityType != "STILL" && log.activityType != "IN_VEHICLE"

            if (log.transitionType == "ENTER" && isPhysicalActivity) {
                // Started an active period
                activeStartTime = log.timestamp
            } else if (log.transitionType == "EXIT" && activeStartTime != null) {
                // Ended an active period
                totalMs += log.timestamp - activeStartTime
                activeStartTime = null
            } else if (log.transitionType == "ENTER" &&
                (log.activityType == "STILL" || log.activityType == "IN_VEHICLE") &&
                activeStartTime != null
            ) {
                // Became still or entered vehicle — end active period
                totalMs += log.timestamp - activeStartTime
                activeStartTime = null
            }
        }

        // If still in an active period and it's today, count until now
        if (activeStartTime != null && isToday) {
            totalMs += System.currentTimeMillis() - activeStartTime
        }

        return totalMs / 60_000 // convert to minutes
    }

    fun todayDate(): String = dateFormat.format(Date())
}
