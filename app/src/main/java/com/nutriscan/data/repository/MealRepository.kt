package com.nutriscan.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.util.MealImageStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.catch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
private val formatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

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
        source: String = "ml",
        imagePath: String? = null
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
            source = source,
            imagePath = imagePath
        )
        return mealLogDao.insert(log)
    }

    fun getTodayLogs(): Flow<List<MealLog>> = mealLogDao.getTodayLogs(getStartOfDayTimestamp())

    fun getRecentLogs(limit: Int = 50): Flow<List<MealLog>> = mealLogDao.getRecentLogs(limit)

    suspend fun deleteLog(id: Int) = mealLogDao.deleteById(id)
    
    /**
     * Delete a meal and its associated image file (if any).
     */
    suspend fun deleteLogWithImage(id: Int) {
        val meal = mealLogDao.getById(id)
        meal?.imagePath?.let { MealImageStorage.deleteMealImage(it) }
        mealLogDao.deleteById(id)
    }

    // ============ ANALYTICS ============

    fun getTodayTotalCalories(): Flow<Int> = mealLogDao.getTodayTotalCalories(getStartOfDayTimestamp())

    fun getTodayMacros(): Flow<MacroTotals> = mealLogDao.getTodayMacros(getStartOfDayTimestamp())

    fun getLast7DaysCalories(): Flow<List<DailyCalories>> {
        val sevenDaysAgo = getStartOfDayTimestamp() - (7 * 24 * 60 * 60 * 1000L)
        return mealLogDao.getDailyCaloriesTrend(sevenDaysAgo)
            .map { rawList ->
                fillMissingDays(rawList, daysCount = 7)
            }
    }

    // To fill in missing days so that last 7 days chart displays properly
    fun fillMissingDays(data: List<DailyCalories>, daysCount: Int = 7): List<DailyCalories> {
        val today = LocalDate.now()
        val daysList = (0 until daysCount).map { today.minusDays((daysCount - 1 - it).toLong()) }

        val dataByDay = data.associateBy { LocalDate.parse(it.day, formatter) }

        return daysList.map { day ->
            dataByDay[day] ?: DailyCalories(day.format(formatter), 0)
        }
    }

    fun getWeeklyAverageCalories(): Flow<Float> {
        val sevenDaysAgo = getStartOfDayTimestamp() - (7 * 24 * 60 * 60 * 1000L)
        return mealLogDao.getWeeklyAverageCalories(sevenDaysAgo)
    }

    private object Keys {
        val TARGET_CALORIES = intPreferencesKey("target_calories")

        val USER_IS_FEMALE = booleanPreferencesKey("user_isFemale")
        val USER_WEIGHT = intPreferencesKey("user_weight")
        val USER_HEIGHT = intPreferencesKey("user_height")
        val USER_AGE = intPreferencesKey("user_age")
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

    //------------------ GENDER
    fun getIsFemale(): Flow<Boolean> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[Keys.USER_IS_FEMALE] ?: false
            }
    }
    suspend fun saveIsFemale(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_IS_FEMALE] = value
        }
    }

    //------------------ WEIGHT
    fun getWeight(): Flow<Int> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[Keys.USER_WEIGHT] ?: 50
            }
    }
    suspend fun saveWeight(value: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_WEIGHT] = value
        }
    }
    //------------------ HEIGHT
    fun getHeight(): Flow<Int> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[Keys.USER_HEIGHT] ?: 150
            }
    }
    suspend fun saveHeight(value: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_HEIGHT] = value
        }
    }
    //------------------ AGE
    fun getAge(): Flow<Int> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[Keys.USER_AGE] ?: 21
            }
    }
    suspend fun saveAge(value: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_AGE] = value
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
