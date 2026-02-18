package com.nutriscan.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nutriscan.MainActivity
import com.nutriscan.R
import com.nutriscan.data.repository.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the step counter sensor alive in the background.
 *
 * Key features:
 * - Manages [StepCounterSensor] lifecycle (hardware pedometer + accelerometer fallback).
 * - Manages [ActivityTransitionManager] for automatic ML-based activity detection.
 * - **Filters out IN_VEHICLE** — pauses step/distance accumulation during vehicle travel.
 * - Persists steps to [StepRepository] periodically.
 * - Estimates distance from steps based on detected activity type.
 * - Holds a partial **WakeLock** to prevent CPU sleep during tracking.
 * - Uses **START_STICKY** to auto-restart if killed by the system.
 * - Handles midnight date rollover.
 *
 * Uses companion [MutableStateFlow] fields so ViewModels can observe
 * tracking data without binding to this service.
 */
@AndroidEntryPoint
class StepCounterService : LifecycleService() {

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PERSIST_INTERVAL_MS = 10_000L // save every 10s
        private const val WAKE_LOCK_TAG = "NutriScan:StepCounterWakeLock"

        // ---- Stride lengths (meters) ----
        private const val STRIDE_WALKING = 0.762
        private const val STRIDE_RUNNING = 1.0
        private const val STRIDE_CYCLING = 0.0   // not step-based
        private const val STRIDE_DEFAULT = 0.762

        // Activities where step/distance counting should be PAUSED
        private val PAUSED_ACTIVITIES = setOf("IN_VEHICLE")

        // ---- Observable state ----

        /** Live step count. */
        private val _currentSteps = MutableStateFlow(0)
        val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()

        /** Estimated distance in meters. */
        private val _currentDistanceMeters = MutableStateFlow(0.0)
        val currentDistanceMeters: StateFlow<Double> = _currentDistanceMeters.asStateFlow()

        /** Whether the service is currently running. */
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        /** Whether step counting is paused (e.g., in vehicle). */
        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        /** Start the service. */
        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop the service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }

        /** Get stride length based on current detected activity. */
        fun getStrideForActivity(activity: String): Double = when (activity) {
            "RUNNING" -> STRIDE_RUNNING
            "CYCLING" -> STRIDE_CYCLING
            "WALKING" -> STRIDE_WALKING
            else -> STRIDE_DEFAULT
        }

        /** Check if the given activity should pause step counting. */
        fun isActivityPaused(activity: String): Boolean = activity in PAUSED_ACTIVITIES
    }

    @Inject lateinit var stepRepository: StepRepository

    private var stepCounterSensor: StepCounterSensor? = null
    private var activityManager: ActivityTransitionManager? = null
    private var persistJob: Job? = null
    private var observeJob: Job? = null
    private var activityObserveJob: Job? = null
    private var currentDate: String = ""
    private var lastStepsForDistance: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null

    // Tracks the step count when vehicle mode was entered, to discard vehicle steps
    private var stepsAtVehicleEntry: Int? = null

    // ============ Lifecycle ============

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isServiceRunning.value = true
        startTracking()

        return START_STICKY // auto-restart if killed
    }

    override fun onDestroy() {
        stopTracking()
        releaseWakeLock()
        _isServiceRunning.value = false
        _isPaused.value = false
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // ============ Wake Lock ============

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                acquire() // Held until service dies or explicit release
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    // ============ Tracking ============

    private fun startTracking() {
        currentDate = stepRepository.todayDate()

        // Start step counter sensor
        val sensor = StepCounterSensor(this)
        stepCounterSensor = sensor

        lifecycleScope.launch {
            val existingLog = stepRepository.getStepLogForDate(currentDate)
            val previousSteps = existingLog?.steps ?: 0
            lastStepsForDistance = previousSteps
            Log.d(TAG, "Starting sensor. Previous steps today: $previousSteps")
            sensor.start(previousSteps)
        }

        // Start activity recognition
        val actMgr = ActivityTransitionManager(this)
        activityManager = actMgr
        actMgr.start()

        // Observe detected activity changes for IN_VEHICLE filtering
        activityObserveJob = lifecycleScope.launch {
            ActivityTransitionManager.currentActivity.collectLatest { activity ->
                val wasPaused = _isPaused.value
                val shouldPause = isActivityPaused(activity)
                _isPaused.value = shouldPause

                if (shouldPause && !wasPaused) {
                    // Entering vehicle — record current steps to discard any sensor noise
                    stepsAtVehicleEntry = stepCounterSensor?.stepsSinceStart?.value
                    Log.i(TAG, "IN_VEHICLE detected — pausing step/distance counting at ${stepsAtVehicleEntry} steps")
                } else if (!shouldPause && wasPaused) {
                    // Exiting vehicle — adjust lastStepsForDistance to skip any steps
                    // that the sensor may have registered during the drive
                    val currentSensorSteps = stepCounterSensor?.stepsSinceStart?.value ?: 0
                    lastStepsForDistance = currentSensorSteps
                    stepsAtVehicleEntry = null
                    Log.i(TAG, "Exited vehicle — resuming at $currentSensorSteps steps")
                }

                updateNotification(_currentSteps.value)
            }
        }

        // Observe sensor steps → update companion StateFlow + notification + distance
        observeJob = lifecycleScope.launch {
            sensor.stepsSinceStart.collectLatest { steps ->
                // If in a vehicle, don't update step count or distance
                if (_isPaused.value) {
                    return@collectLatest
                }

                _currentSteps.value = steps

                // Compute distance from step delta
                val stepDelta = steps - lastStepsForDistance
                if (stepDelta > 0) {
                    val activity = ActivityTransitionManager.currentActivity.value
                    val stride = getStrideForActivity(activity)
                    _currentDistanceMeters.value += stepDelta * stride
                    lastStepsForDistance = steps
                }

                updateNotification(steps)
            }
        }

        // Periodically persist steps and check midnight rollover
        persistJob = lifecycleScope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                persistSteps()
                checkMidnightRollover()
            }
        }
    }

    private fun stopTracking() {
        lifecycleScope.launch { persistSteps() }

        activityObserveJob?.cancel()
        observeJob?.cancel()
        persistJob?.cancel()
        stepCounterSensor?.stop()
        stepCounterSensor = null
        activityManager?.stop()
        activityManager = null
    }

    private suspend fun persistSteps() {
        val sensor = stepCounterSensor ?: return
        if (_isPaused.value) return // Don't persist if in vehicle

        val steps = sensor.stepsSinceStart.value
        val rawValue = sensor.getLastRawSensorValue()
        val distance = _currentDistanceMeters.value

        try {
            stepRepository.recordSteps(steps, distance, rawValue)
            Log.d(TAG, "Persisted steps: $steps, distance: ${String.format("%.1f", distance)}m")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist steps", e)
        }
    }

    private suspend fun checkMidnightRollover() {
        val today = stepRepository.todayDate()
        if (today != currentDate) {
            Log.i(TAG, "Midnight rollover: $currentDate -> $today")
            persistSteps()

            currentDate = today
            stepCounterSensor?.resetSession()
            _currentSteps.value = 0
            _currentDistanceMeters.value = 0.0
            lastStepsForDistance = 0
            stepsAtVehicleEntry = null
        }
    }

    // ============ Notification ============

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your current step count and activity"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sensorType = if (stepCounterSensor?.usingHardwareSensor?.value == true) {
            "Pedometer"
        } else {
            "Accelerometer"
        }

        val activity = ActivityTransitionManager.currentActivity.value
        val distanceKm = _currentDistanceMeters.value / 1000.0
        val distanceText = String.format("%.2f km", distanceKm)
        val isPausedNow = _isPaused.value

        val title = if (isPausedNow) {
            "⏸ Paused · $steps steps"
        } else {
            "🚶 $steps steps · $distanceText"
        }

        val subtitle = if (isPausedNow) {
            "In vehicle — step counting paused"
        } else {
            "Activity: $activity · Sensor: $sensorType"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(steps: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(steps))
    }
}
