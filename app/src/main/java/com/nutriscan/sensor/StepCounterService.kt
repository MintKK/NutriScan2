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
 * - Manages [StepCounterSensor] lifecycle.
 * - Persists steps to [StepRepository] periodically.
 * - Updates a persistent notification with the live step count.
 * - Handles midnight date rollover.
 *
 * Uses a companion [MutableStateFlow] so the dashboard can observe the step
 * count without binding to this service.
 */
@AndroidEntryPoint
class StepCounterService : LifecycleService() {

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PERSIST_INTERVAL_MS = 10_000L // save every 10s

        /** Observable step count — readable from anywhere (ViewModel, etc.). */
        private val _currentSteps = MutableStateFlow(0)
        val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()

        /** Whether the service is currently running. */
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

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
    }

    @Inject lateinit var stepRepository: StepRepository

    private var stepCounterSensor: StepCounterSensor? = null
    private var persistJob: Job? = null
    private var observeJob: Job? = null
    private var currentDate: String = ""

    // ============ Lifecycle ============

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Start as foreground immediately
        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isServiceRunning.value = true
        startTracking()

        return START_STICKY // restart if killed
    }

    override fun onDestroy() {
        stopTracking()
        _isServiceRunning.value = false
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // ============ Tracking ============

    private fun startTracking() {
        currentDate = stepRepository.todayDate()

        val sensor = StepCounterSensor(this)
        stepCounterSensor = sensor

        // Load persisted steps for today and start the sensor
        lifecycleScope.launch {
            val existingLog = stepRepository.getStepLogForDate(currentDate)
            val previousSteps = existingLog?.steps ?: 0
            Log.d(TAG, "Starting sensor. Previous steps today: $previousSteps")
            sensor.start(previousSteps)
        }

        // Observe sensor steps and update the companion StateFlow + notification
        observeJob = lifecycleScope.launch {
            sensor.stepsSinceStart.collectLatest { steps ->
                _currentSteps.value = steps
                updateNotification(steps)
            }
        }

        // Periodically persist steps and check for midnight rollover
        persistJob = lifecycleScope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                persistSteps()
                checkMidnightRollover()
            }
        }
    }

    private fun stopTracking() {
        // Final persist before stopping
        lifecycleScope.launch {
            persistSteps()
        }

        observeJob?.cancel()
        persistJob?.cancel()
        stepCounterSensor?.stop()
        stepCounterSensor = null
    }

    private suspend fun persistSteps() {
        val sensor = stepCounterSensor ?: return
        val steps = sensor.stepsSinceStart.value
        val rawValue = sensor.getLastRawSensorValue()

        try {
            stepRepository.recordSteps(steps, rawValue)
            Log.d(TAG, "Persisted steps: $steps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist steps", e)
        }
    }

    private suspend fun checkMidnightRollover() {
        val today = stepRepository.todayDate()
        if (today != currentDate) {
            Log.i(TAG, "Midnight rollover: $currentDate -> $today")

            // Persist final count for the old date
            persistSteps()

            // Reset everything for the new day
            currentDate = today
            stepCounterSensor?.resetSession()
            _currentSteps.value = 0
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
                description = "Shows your current step count"
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
            "Hardware pedometer"
        } else {
            "Accelerometer"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚶 Steps Today: $steps")
            .setContentText("Tracking via $sensorType")
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
