package com.nutriscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.dao.WaterLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// ============ DATA MODELS ============

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val requiredStreak: Int,        // Days required to earn
    val isEarned: Boolean = false,
    val earnedDate: String? = null
)

data class StreakInfo(
    val type: String,        // e.g. "hydration", "macros", "early_bird"
    val label: String,
    val emoji: String,
    val currentStreak: Int,
    val bestStreak: Int
)

data class AchievementState(
    val streaks: List<StreakInfo>,
    val badges: List<Badge>
)

// ============ REPOSITORY ============

@Singleton
class AchievementRepository @Inject constructor(
    private val waterLogDao: WaterLogDao,
    private val mealLogDao: MealLogDao,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // DataStore keys for persisted best streaks and earned badges
        private val BEST_WATER_STREAK = intPreferencesKey("best_water_streak")
        private val BEST_MACRO_STREAK = intPreferencesKey("best_macro_streak")
        private val BEST_EARLYBIRD_STREAK = intPreferencesKey("best_earlybird_streak")

        private val BADGE_WATER_WARRIOR = booleanPreferencesKey("badge_water_warrior")
        private val BADGE_WATER_NOVICE = booleanPreferencesKey("badge_water_novice")
        private val BADGE_MACRO_MASTER = booleanPreferencesKey("badge_macro_master")
        private val BADGE_EARLY_BIRD = booleanPreferencesKey("badge_early_bird")

        private const val WATER_WARRIOR_DAYS = 5
        private const val WATER_NOVICE_DAYS = 1
        private const val MACRO_MASTER_DAYS = 7
        private const val EARLY_BIRD_DAYS = 3
    }

    /**
     * Calculate all streaks and badges, returning a complete AchievementState.
     */
    suspend fun getAchievementState(waterGoalMl: Int, proteinGoalG: Float): AchievementState {
        val thirtyDaysAgoMs = LocalDate.now().minusDays(30)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // --- HYDRATION STREAK ---
        val waterStreak = calculateWaterStreak(thirtyDaysAgoMs, waterGoalMl)

        // --- MACRO STREAK (protein within +/- 5g) ---
        val macroStreak = calculateMacroStreak(thirtyDaysAgoMs, proteinGoalG)

        // --- EARLY BIRD STREAK (breakfast before 9 AM) ---
        val earlyBirdStreak = calculateEarlyBirdStreak(thirtyDaysAgoMs)

        // Update best streaks in DataStore
        val prefs = dataStore.data.first()
        val bestWater = maxOf(waterStreak, prefs[BEST_WATER_STREAK] ?: 0)
        val bestMacro = maxOf(macroStreak, prefs[BEST_MACRO_STREAK] ?: 0)
        val bestEarlyBird = maxOf(earlyBirdStreak, prefs[BEST_EARLYBIRD_STREAK] ?: 0)

        // Check and award badges
        val waterWarriorEarned = prefs[BADGE_WATER_WARRIOR] ?: false || waterStreak >= WATER_WARRIOR_DAYS
        val waterNoviceEarned = prefs[BADGE_WATER_NOVICE] ?: false || waterStreak >= WATER_NOVICE_DAYS
        val macroMasterEarned = prefs[BADGE_MACRO_MASTER] ?: false || macroStreak >= MACRO_MASTER_DAYS
        val earlyBirdEarned = prefs[BADGE_EARLY_BIRD] ?: false || earlyBirdStreak >= EARLY_BIRD_DAYS

        // Persist
        dataStore.edit { p ->
            p[BEST_WATER_STREAK] = bestWater
            p[BEST_MACRO_STREAK] = bestMacro
            p[BEST_EARLYBIRD_STREAK] = bestEarlyBird
            p[BADGE_WATER_WARRIOR] = waterWarriorEarned
            p[BADGE_WATER_NOVICE] = waterNoviceEarned
            p[BADGE_MACRO_MASTER] = macroMasterEarned
            p[BADGE_EARLY_BIRD] = earlyBirdEarned
        }

        val streaks = listOf(
            StreakInfo("hydration", "Hydration", "💧", waterStreak, bestWater),
            StreakInfo("macros", "Protein", "🥩", macroStreak, bestMacro),
            StreakInfo("early_bird", "Early Bird", "🌅", earlyBirdStreak, bestEarlyBird)
        )

        val badges = listOf(
            Badge(
                id = "water_novice",
                title = "Water Novice",
                description = "Hit your water goal for $WATER_NOVICE_DAYS day",
                emoji = "💧",
                requiredStreak = WATER_NOVICE_DAYS,
                isEarned = waterNoviceEarned
            ),
            Badge(
                id = "water_warrior",
                title = "Water Warrior",
                description = "Hit your water goal for $WATER_WARRIOR_DAYS days straight",
                emoji = "🌊",
                requiredStreak = WATER_WARRIOR_DAYS,
                isEarned = waterWarriorEarned
            ),
            Badge(
                id = "macro_master",
                title = "Macro Master",
                description = "Hit protein target (±5g) for $MACRO_MASTER_DAYS days",
                emoji = "🏋️",
                requiredStreak = MACRO_MASTER_DAYS,
                isEarned = macroMasterEarned
            ),
            Badge(
                id = "early_bird",
                title = "Early Bird",
                description = "Log breakfast before 9 AM for $EARLY_BIRD_DAYS days",
                emoji = "🌅",
                requiredStreak = EARLY_BIRD_DAYS,
                isEarned = earlyBirdEarned
            )
        )

        return AchievementState(streaks = streaks, badges = badges)
    }

    // ============ STREAK CALCULATIONS ============

    private suspend fun calculateWaterStreak(startMs: Long, goalMl: Int): Int {
        if (goalMl <= 0) return 0
        val dailyTotals = waterLogDao.getDailyWaterTotals(startMs)
        val dateSet = dailyTotals.filter { it.totalMl >= goalMl }
            .map { LocalDate.parse(it.day, formatter) }
            .toSet()
        return countConsecutiveDaysFromYesterday(dateSet)
    }

    private suspend fun calculateMacroStreak(startMs: Long, proteinGoalG: Float): Int {
        if (proteinGoalG <= 0) return 0
        val dailyMacros = mealLogDao.getDailyMacrosTrend(startMs).first()
        val dateSet = dailyMacros
            .filter { kotlin.math.abs(it.protein - proteinGoalG) <= 5f }
            .map { LocalDate.parse(it.day, formatter) }
            .toSet()
        return countConsecutiveDaysFromYesterday(dateSet)
    }

    private suspend fun calculateEarlyBirdStreak(startMs: Long): Int {
        val dailyMacros = mealLogDao.getDailyMacrosTrend(startMs).first()
        val daysWithMeals = dailyMacros.map { LocalDate.parse(it.day, formatter) }.toSet()

        // For each day that had meals, check if any meal was before 9 AM
        val earlyDays = mutableSetOf<LocalDate>()
        for (day in daysWithMeals) {
            val meals = mealLogDao.getLogsForDate(day.format(formatter))
            val hadEarlyMeal = meals.any { meal ->
                val mealTime = java.time.Instant.ofEpochMilli(meal.timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalTime()
                mealTime.isBefore(LocalTime.of(9, 0))
            }
            if (hadEarlyMeal) earlyDays.add(day)
        }
        return countConsecutiveDaysFromYesterday(earlyDays)
    }

    /**
     * Count consecutive days going backwards from yesterday.
     * We use yesterday so that a streak isn't broken during the current day
     * (users haven't had the full day to complete their goal yet).
     */
    private fun countConsecutiveDaysFromYesterday(qualifyingDays: Set<LocalDate>): Int {
        var streak = 0
        var checkDate = LocalDate.now().minusDays(1)
        while (qualifyingDays.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        // Also count today if it already qualifies
        if (qualifyingDays.contains(LocalDate.now())) streak++
        return streak
    }
}
