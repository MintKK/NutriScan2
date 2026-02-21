package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.DailyNetCalories
import com.nutriscan.data.local.dao.DailyWaterTotal
import com.nutriscan.data.local.dao.MacroTotals

/**
 * Aggregated weekly data used to generate the PDF report.
 * Emphasises "results" — net calories, deviation from targets, streaks.
 */
data class WeeklyReportData(
    // ---------- Date range ----------
    val startDate: String,          // "yyyy-MM-dd"
    val endDate: String,

    // ---------- Calorie results (net = eaten − burned) ----------
    val dailyNet: List<DailyNetCalories>,   // per-day eaten / burned / net
    val avgNetCalories: Int,                // weekly average net
    val targetCalories: Int,                // user's daily target
    /** % the user deviated from target: positive = over, negative = under */
    val calorieDeviationPct: Double,

    // ---------- Macros ----------
    val avgMacros: MacroTotals,             // avg P / C / F per day

    // ---------- Activity ----------
    val totalSteps: Int,
    val totalDistanceKm: Double,
    val avgDailySteps: Int,

    // ---------- Water ----------
    val dailyWater: List<DailyWaterTotal>,
    val waterGoalMl: Int,
    val avgWaterMl: Int,

    // ---------- Streaks & badges ----------
    val streaks: List<StreakInfo>,
    val earnedBadges: List<Badge>,

    // ---------- User profile ----------
    val userWeightKg: Int,
    val userHeightCm: Int,
    val userAge: Int,
    val isFemale: Boolean
)
