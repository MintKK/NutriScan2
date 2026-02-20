package com.nutriscan.ui.caloriesburned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.repository.ActivityRepository
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.data.repository.StepRepository
import com.nutriscan.sensor.ActivityTransitionManager
import com.nutriscan.sensor.StepCounterService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Calories Burned landing screen.
 * Provides summary data — Person B will add calorie calculations here.
 */
@HiltViewModel
class CaloriesBurnedViewModel @Inject constructor(
    stepRepository: StepRepository,
    activityRepository: ActivityRepository,
    mealRepository: MealRepository
) : ViewModel() {

    /** Today's step count from DB. */
    val todaySteps: StateFlow<Int> = stepRepository.getTodaySteps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Live step count from service. */
    val liveSteps: StateFlow<Int> = StepCounterService.currentSteps

    /** Estimated distance in meters. */
    val distanceMeters: StateFlow<Double> = StepCounterService.currentDistanceMeters

    /** Current detected activity. */
    val currentActivity: StateFlow<String> = ActivityTransitionManager.currentActivity

    /** Active minutes today. */
    val activeMinutes: StateFlow<Long> = activityRepository.getTodayActiveMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Whether tracking is active. */
    val isTrackingActive: StateFlow<Boolean> = StepCounterService.isServiceRunning

    val userWeight: StateFlow<Int> = mealRepository.getWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70) // 70 as default weight

    /** Calories burnt from physical activity*/
    val caloriesBurned: StateFlow<Double> = combine(liveSteps, userWeight) { steps, weight ->
        val multiplier = when {
            weight >= 86 -> 0.55
            weight >= 70 -> 0.45
            else -> 0.35
        }
        steps * multiplier
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Calories from food*/
    val foodCalories: Flow<Int> = mealRepository.getTodayTotalCalories()

    /** Total calories today*/
    val netCalories: StateFlow<Int> = combine(foodCalories, caloriesBurned) { food, burned ->
        (food - burned).toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

//    val remainingCalories: StateFlow<Int> = combine()

}
