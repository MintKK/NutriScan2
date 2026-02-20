package com.nutriscan.ui.activity

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.entity.ActivityLog
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
 * ViewModel for the Physical Activity Tracker screen.
 * Uses AndroidViewModel because starting/stopping the service needs Context.
 */
@HiltViewModel
class ActivityTrackerViewModel @Inject constructor(
    private val application: Application,
    stepRepository: StepRepository,
    activityRepository: ActivityRepository
) : AndroidViewModel(application) {

    // ---- Step data ----
    val liveSteps: StateFlow<Int> = StepCounterService.currentSteps
    val todaySteps: StateFlow<Int> = stepRepository.getTodaySteps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---- Distance ----
    val distanceMeters: StateFlow<Double> = StepCounterService.currentDistanceMeters

    // ---- Activity recognition ----
    val currentActivity: StateFlow<String> = ActivityTransitionManager.currentActivity
    val activityTimeline: StateFlow<List<ActivityLog>> = activityRepository.getTodayTimeline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeMinutes: StateFlow<Long> = activityRepository.getTodayActiveMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val activeSeconds: StateFlow<Long> = StepCounterService.activeSeconds

    // ---- Service state ----
    val isTrackingActive: StateFlow<Boolean> = StepCounterService.isServiceRunning
    
    /** Whether tracking is paused (e.g., in vehicle). */
    val isPaused: StateFlow<Boolean> = StepCounterService.isPaused

    /** Start the step counter + activity recognition service. */
    fun startTracking() {
        StepCounterService.start(application)
    }

    /** Stop the service. */
    fun stopTracking() {
        StepCounterService.stop(application)
    }

    /** Check if the app is exempt from battery optimization. */
    fun isBatteryOptimized(): Boolean {
        val pm = application.getSystemService(PowerManager::class.java)
        return !pm.isIgnoringBatteryOptimizations(application.packageName)
    }

    /** Launch the system dialog to request battery optimization exemption. */
    fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }
}
