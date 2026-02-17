package com.nutriscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.local.entity.MealLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch


@Singleton
class MealRepository @Inject constructor(
    private val mealLogDao: MealLogDao,
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Log a meal with computed nutrition based on portion size.
     */
    suspend fun logMeal(
        foodItem: FoodItem,
        grams: Int,
        source: String = "ml"
    ): Long {
        val factor = grams / 100f
        val log = MealLog(
            foodItemId = foodItem.id,
            foodName = foodItem.name,
            grams = grams,
            kcalTotal = (foodItem.kcalPer100g * factor).toInt(),
            proteinTotal = foodItem.proteinPer100g * factor,
            carbsTotal = foodItem.carbsPer100g * factor,
            fatTotal = foodItem.fatPer100g * factor,
            source = source
        )
        return mealLogDao.insert(log)
    }

    fun getTodayLogs(): Flow<List<MealLog>> = mealLogDao.getTodayLogs(getStartOfDayTimestamp())

    fun getRecentLogs(limit: Int = 50): Flow<List<MealLog>> = mealLogDao.getRecentLogs(limit)

    suspend fun deleteLog(id: Int) = mealLogDao.deleteById(id)

    // ============ ANALYTICS ============

    fun getTodayTotalCalories(): Flow<Int> = mealLogDao.getTodayTotalCalories(getStartOfDayTimestamp())

    fun getTodayMacros(): Flow<MacroTotals> = mealLogDao.getTodayMacros(getStartOfDayTimestamp())

    fun getLast7DaysCalories(): Flow<List<DailyCalories>> {
        val sevenDaysAgo = getStartOfDayTimestamp() - (7 * 24 * 60 * 60 * 1000L)
        return mealLogDao.getDailyCaloriesTrend(sevenDaysAgo)
    }

    fun getWeeklyAverageCalories(): Flow<Float> {
        val sevenDaysAgo = getStartOfDayTimestamp() - (7 * 24 * 60 * 60 * 1000L)
        return mealLogDao.getWeeklyAverageCalories(sevenDaysAgo)
    }

    private object Keys {
        val TARGET_CALORIES = intPreferencesKey("target_calories")
    }

    fun getTargetCalories(): Flow<Int> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[Keys.TARGET_CALORIES] ?: 0
            }
    }


    suspend fun saveTargetCalories(targetCal: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.TARGET_CALORIES] = targetCal
        }
    }


    // ============ HELPERS ============
    
    private fun getStartOfDayTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
