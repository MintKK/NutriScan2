package com.nutriscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single water intake entry.
 */
@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "amount_ml")
    val amountMl: Int
)
