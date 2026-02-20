package com.nutriscan.data.repository

/**
 * Aggregated per-day fitness summary for Person B's calorie calculations.
 *
 * This data class provides everything Person B needs to:
 * 1. Calculate calories burned using MET formula
 * 2. Sync with calorie intake data from MealLog for net calorie balance
 *
 * Usage:
 * ```kotlin
 * val summary = fitnessSummaryRepository.getSummaryForDate("2025-02-19")
 * val caloriesBurned = calculateCaloriesBurned(summary, userWeightKg)
 * ```
 */
data class DailyFitnessSummary(
    /** Date in "yyyy-MM-dd" format. Matches MealLog date grouping. */
    val date: String,

    /** Total steps taken on this date. */
    val totalSteps: Int,

    /** Total distance estimated in meters. */
    val distanceMeters: Double,

    /** Total active minutes (excludes STILL and IN_VEHICLE). */
    val activeMinutes: Long,

    /** Number of walking sessions (ENTER transitions) on this date. */
    val walkingSessions: Int,

    /** Number of running sessions on this date. */
    val runningSessions: Int,

    /** Number of cycling sessions on this date. */
    val cyclingSessions: Int,

    /** Timestamp of last update (Unix millis). */
    val lastUpdated: Long
)
