package com.nutriscan.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that combines step and activity data into a unified
 * [DailyFitnessSummary] for Person B's calorie calculations.
 *
 * This is the PRIMARY integration point for Person B.
 * It syncs with the calorie intake data format (per-date, "yyyy-MM-dd"),
 * matching MealLogDao's getDailyCaloriesTrend() grouping.
 */
@Singleton
class FitnessSummaryRepository @Inject constructor(
    private val stepRepository: StepRepository,
    private val activityRepository: ActivityRepository
) {

    /**
     * Get a live-updating fitness summary for a specific date.
     *
     * Person B can observe this alongside MealLogDao.getTodayTotalCalories()
     * to compute net calories:
     *   netCalories = caloriesConsumed - caloriesBurned
     *
     * @param date Date in "yyyy-MM-dd" format
     */
    fun getSummaryForDate(date: String): Flow<DailyFitnessSummary> {
        val stepsFlow = stepRepository.getStepsForDate(date)
        val distanceFlow = stepRepository.getDistanceForDate(date)
        val activeMinutesFlow = activityRepository.getActiveMinutesForDate(date)
        val timelineFlow = activityRepository.getTimelineForDate(date)

        return combine(stepsFlow, distanceFlow, activeMinutesFlow, timelineFlow) {
            steps, distance, minutes, timeline ->

            val walkingSessions = timeline.count {
                it.activityType == "WALKING" && it.transitionType == "ENTER"
            }
            val runningSessions = timeline.count {
                it.activityType == "RUNNING" && it.transitionType == "ENTER"
            }
            val cyclingSessions = timeline.count {
                it.activityType == "CYCLING" && it.transitionType == "ENTER"
            }

            DailyFitnessSummary(
                date = date,
                totalSteps = steps,
                distanceMeters = distance,
                activeMinutes = minutes,
                walkingSessions = walkingSessions,
                runningSessions = runningSessions,
                cyclingSessions = cyclingSessions,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /** Get today's fitness summary. */
    fun getTodaySummary(): Flow<DailyFitnessSummary> {
        return getSummaryForDate(stepRepository.todayDate())
    }
}
