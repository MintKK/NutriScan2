package com.nutriscan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.nutriscan.data.local.entity.MealLog
import kotlinx.coroutines.flow.Flow

/**
 * Data class for daily calorie aggregation.
 */
data class DailyCalories(
    val day: String,      // Format: "YYYY-MM-DD"
    val totalKcal: Int
)
// For net kcal
data class DailyNetCalories(
    val day: String,      // Format: "YYYY-MM-DD"
    val eatenKcal: Int,
    val burnedKcal: Int,
    val netKcal: Int
)


/**
 * Data class for macro totals.
 */
data class MacroTotals(
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

@Dao
interface MealLogDao {
    
    @Insert
    suspend fun insert(log: MealLog): Long
    
    @Delete
    suspend fun delete(log: MealLog)
    
    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    @Query("SELECT * FROM meal_logs WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayLogs(startOfDay: Long): Flow<List<MealLog>>
    
    @Query("SELECT * FROM meal_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<MealLog>>
    
    // ============ ANALYTICS QUERIES ============
    
    /**
     * Get total calories for today.
     */
    @Query("SELECT COALESCE(SUM(kcal_total), 0) FROM meal_logs WHERE timestamp >= :startOfDay")
    fun getTodayTotalCalories(startOfDay: Long): Flow<Int>
    
    /**
     * Get macro totals for today.
     */
    @Query("""
        SELECT 
            COALESCE(SUM(protein_total), 0) as protein,
            COALESCE(SUM(carbs_total), 0) as carbs,
            COALESCE(SUM(fat_total), 0) as fat
        FROM meal_logs 
        WHERE timestamp >= :startOfDay
    """)
    fun getTodayMacros(startOfDay: Long): Flow<MacroTotals>
    
    /**
     * Get daily calorie trend for last N days.
     * Groups by date and sums calories.
     */
    @Query("""
        SELECT 
            date(timestamp / 1000, 'unixepoch', 'localtime') as day,
            SUM(kcal_total) as totalKcal
        FROM meal_logs
        WHERE timestamp >= :startTimestamp
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getDailyCaloriesTrend(startTimestamp: Long): Flow<List<DailyCalories>>
    
    /**
     * Get weekly average calories.
     */
    @Query("""
        SELECT COALESCE(AVG(daily_total), 0) FROM (
            SELECT SUM(kcal_total) as daily_total
            FROM meal_logs
            WHERE timestamp >= :startOfWeek
            GROUP BY date(timestamp / 1000, 'unixepoch', 'localtime')
        )
    """)
    fun getWeeklyAverageCalories(startOfWeek: Long): Flow<Float>
    
    @Query("DELETE FROM meal_logs")
    suspend fun deleteAll()
}
