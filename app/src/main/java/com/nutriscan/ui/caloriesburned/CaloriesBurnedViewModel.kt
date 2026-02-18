package com.nutriscan.ui.caloriesburned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.repository.ActivityRepository
import com.nutriscan.data.repository.StepRepository
import com.nutriscan.sensor.ActivityTransitionManager
import com.nutriscan.sensor.StepCounterService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Calories Burned landing screen.
 * Provides summary data — Person B will add calorie calculations here.
 */
@HiltViewModel
class CaloriesBurnedViewModel @Inject constructor(
    stepRepository: StepRepository,
    activityRepository: ActivityRepository
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
}
