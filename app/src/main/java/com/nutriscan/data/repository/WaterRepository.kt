package com.nutriscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nutriscan.data.local.dao.WaterLogDao
import com.nutriscan.data.local.entity.WaterLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterRepository @Inject constructor(
    private val waterLogDao: WaterLogDao,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val WATER_GOAL_KEY = intPreferencesKey("water_goal_ml")
        private const val DEFAULT_GOAL = 2000
    }
    
    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
    
    suspend fun logWater(amountMl: Int) {
        waterLogDao.insert(WaterLog(amountMl = amountMl))
    }
    
    fun getTodayTotal(): Flow<Int> = waterLogDao.getTodayTotal(todayStartMillis())
    
    fun getTodayLogs(): Flow<List<WaterLog>> = waterLogDao.getTodayLogs(todayStartMillis())
    
    suspend fun undoLastEntry() {
        val mostRecent = waterLogDao.getMostRecentToday(todayStartMillis())
        mostRecent?.let { waterLogDao.deleteById(it.id) }
    }
    
    fun getWaterGoal(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[WATER_GOAL_KEY] ?: DEFAULT_GOAL
    }
    
    suspend fun setWaterGoal(goalMl: Int) {
        dataStore.edit { prefs ->
            prefs[WATER_GOAL_KEY] = goalMl
        }
    }
}
