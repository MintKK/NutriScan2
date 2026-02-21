package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.DailyNetCalories
import com.nutriscan.data.local.dao.DailyWaterTotal
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.dao.StepLogDao
import com.nutriscan.data.local.dao.WaterLogDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Aggregates data from every feature into a single [WeeklyReportData]
 * snapshot used by [PdfReportGenerator].
 */
@Singleton
class ReportRepository @Inject constructor(
    private val mealLogDao: MealLogDao,
    private val stepLogDao: StepLogDao,
    private val waterLogDao: WaterLogDao,
    private val mealRepository: MealRepository,
    private val achievementRepository: AchievementRepository
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Gather all data for the last 7 days. Returns a complete snapshot.
     */
    suspend fun gatherWeeklyReport(): WeeklyReportData {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(6) // 7 days inclusive
        val endDate = today.format(fmt)
        val startDate = weekAgo.format(fmt)

        val sevenDaysAgoMs = weekAgo
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        // ---- Calories (eaten) ----
        val dailyCalories = mealLogDao.getDailyCaloriesTrend(sevenDaysAgoMs).first()

        // ---- Steps (for burned calculation) ----
        val stepLogs = stepLogDao.getStepsForDateRange(startDate, endDate).first()
        val stepsByDate = stepLogs.associateBy { it.date }

        // ---- User profile ----
        val weight = mealRepository.getWeight().first()
        val height = mealRepository.getHeight().first()
        val age = mealRepository.getAge().first()
        val isFemale = mealRepository.getIsFemale().first()
        val targetCal = mealRepository.getTargetCalories().first()

        // ---- Net calories (eaten - burned) ----
        val burnMultiplier = when {
            weight >= 86 -> 0.55
            weight >= 70 -> 0.45
            else -> 0.35
        }
        val dailyNet = dailyCalories.map { dc ->
            val stepsForDay = stepsByDate[dc.day]?.steps ?: 0
            val burned = (stepsForDay * burnMultiplier).toInt()
            DailyNetCalories(
                day = dc.day,
                eatenKcal = dc.totalKcal,
                burnedKcal = burned,
                netKcal = dc.totalKcal - burned
            )
        }
        val daysWithMeals = dailyNet.filter { it.eatenKcal > 0 }
        val avgNet = if (daysWithMeals.isNotEmpty())
            daysWithMeals.sumOf { it.netKcal } / daysWithMeals.size else 0
        val deviationPct = if (targetCal > 0)
            ((avgNet - targetCal).toDouble() / targetCal) * 100.0 else 0.0

        // ---- Macros ----
        val dailyMacros = mealLogDao.getDailyMacrosTrend(sevenDaysAgoMs).first()
        val macrosDays = dailyMacros.filter { it.protein > 0 || it.carbs > 0 || it.fat > 0 }
        val avgMacros = if (macrosDays.isNotEmpty()) MacroTotals(
            protein = macrosDays.sumOf { it.protein.toDouble() }.toFloat() / macrosDays.size,
            carbs = macrosDays.sumOf { it.carbs.toDouble() }.toFloat() / macrosDays.size,
            fat = macrosDays.sumOf { it.fat.toDouble() }.toFloat() / macrosDays.size
        ) else MacroTotals(0f, 0f, 0f)

        // ---- Steps/Distance totals ----
        val totalSteps = stepLogs.sumOf { it.steps }
        val totalDistKm = stepLogs.sumOf { it.distanceMeters } / 1000.0
        val avgSteps = if (stepLogs.isNotEmpty()) totalSteps / stepLogs.size else 0

        // ---- Water ----
        val dailyWater = waterLogDao.getDailyWaterTotals(sevenDaysAgoMs)
        val waterGoal = mealRepository.getWeight().first().let { w ->
            // Simple estimate: 30-35 ml per kg, default 2000
            maxOf(w * 33, 2000)
        }
        // Try to read actual goal from WaterRepository default (2000 ml)
        val avgWater = if (dailyWater.isNotEmpty())
            dailyWater.sumOf { it.totalMl } / dailyWater.size else 0

        // ---- Achievements ----
        val proteinGoal = mealRepository.getTargetProtein().first().toFloat()
        val achievements = achievementRepository.getAchievementState(waterGoal, proteinGoal)

        return WeeklyReportData(
            startDate = startDate,
            endDate = endDate,
            dailyNet = dailyNet,
            avgNetCalories = avgNet,
            targetCalories = targetCal,
            calorieDeviationPct = deviationPct,
            avgMacros = avgMacros,
            totalSteps = totalSteps,
            totalDistanceKm = totalDistKm,
            avgDailySteps = avgSteps,
            dailyWater = dailyWater,
            waterGoalMl = waterGoal,
            avgWaterMl = avgWater,
            streaks = achievements.streaks,
            earnedBadges = achievements.badges.filter { it.isEarned },
            userWeightKg = weight,
            userHeightCm = height,
            userAge = age,
            isFemale = isFemale
        )
    }
}
