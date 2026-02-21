package com.nutriscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutriscan.data.local.entity.WaterLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterLogDao {
    
    @Insert
    suspend fun insert(log: WaterLog): Long
    
    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    /**
     * Reactive flow of today's total water intake in ml.
     */
    @Query("SELECT COALESCE(SUM(amount_ml), 0) FROM water_logs WHERE timestamp >= :startOfDay")
    fun getTodayTotal(startOfDay: Long): Flow<Int>
    
    /**
     * Today's water entries, most recent first (for undo).
     */
    @Query("SELECT * FROM water_logs WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayLogs(startOfDay: Long): Flow<List<WaterLog>>
    
    /**
     * Get the most recent water log (for undo).
     */
    @Query("SELECT * FROM water_logs WHERE timestamp >= :startOfDay ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentToday(startOfDay: Long): WaterLog?
}
