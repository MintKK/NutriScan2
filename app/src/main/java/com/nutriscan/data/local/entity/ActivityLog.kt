package com.nutriscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records activity transition events detected by the Activity Recognition API.
 * Each row represents an ENTER or EXIT transition for a specific activity type.
 */
@Entity(
    tableName = "activity_logs",
    indices = [Index("date"), Index("timestamp")]
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Activity type: "WALKING", "RUNNING", "CYCLING", "STILL", "UNKNOWN" */
    @ColumnInfo(name = "activity_type")
    val activityType: String,

    /** Transition type: "ENTER" or "EXIT" */
    @ColumnInfo(name = "transition_type")
    val transitionType: String,

    /** Calendar date in "yyyy-MM-dd" format. */
    val date: String,

    /** Timestamp of the event (Unix millis). */
    val timestamp: Long = System.currentTimeMillis()
)
