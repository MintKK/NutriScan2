package com.nutriscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores daily step count records.
 * One row per calendar day, identified by a "yyyy-MM-dd" date string.
 */
@Entity(
    tableName = "step_logs",
    indices = [Index(value = ["date"], unique = true)]
)
data class StepLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Calendar date in "yyyy-MM-dd" format. */
    val date: String,

    /** Accumulated step count for this day. */
    val steps: Int = 0,

    /**
     * Last raw value from TYPE_STEP_COUNTER sensor (steps since boot).
     * Used to compute deltas between sensor events.
     * Set to -1 when unknown / using accelerometer fallback.
     */
    @ColumnInfo(name = "last_sensor_value")
    val lastSensorValue: Int = -1,

    /** Timestamp of last update (Unix millis). */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
