package com.nutriscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutriscan.data.local.entity.ActivityLog
import kotlinx.coroutines.flow.Flow

/**
 * DAO for activity transition logs.
 */
@Dao
interface ActivityLogDao {

    @Insert
    suspend fun insert(log: ActivityLog)

    /** Get all activity transitions for a given date, ordered by time. */
    @Query("SELECT * FROM activity_logs WHERE date = :date ORDER BY timestamp ASC")
    fun getActivitiesForDate(date: String): Flow<List<ActivityLog>>

    /** Get all activity transitions for a given date synchronously. */
    @Query("SELECT * FROM activity_logs WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getActivitiesForDateSync(date: String): List<ActivityLog>

    /** Get the most recent ENTER transition (i.e. what the user is currently doing). */
    @Query("""
        SELECT * FROM activity_logs 
        WHERE transition_type = 'ENTER' 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    fun getLatestActivity(): Flow<ActivityLog?>

    /** Get the most recent ENTER transition synchronously. */
    @Query("""
        SELECT * FROM activity_logs 
        WHERE transition_type = 'ENTER' 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLatestActivitySync(): ActivityLog?

    /** Count ENTER transitions for a specific activity type on a date. */
    @Query("""
        SELECT COUNT(*) FROM activity_logs 
        WHERE date = :date AND activity_type = :activityType AND transition_type = 'ENTER'
    """)
    fun countActivitySessions(date: String, activityType: String): Flow<Int>

    /** Get all unique dates that have activity logs. */
    @Query("SELECT DISTINCT date FROM activity_logs ORDER BY date DESC")
    fun getAllDatesWithActivity(): Flow<List<String>>

    /** Get activity transitions for a date range. */
    @Query("SELECT * FROM activity_logs WHERE date >= :startDate AND date <= :endDate ORDER BY timestamp ASC")
    fun getActivitiesForDateRange(startDate: String, endDate: String): Flow<List<ActivityLog>>
}
