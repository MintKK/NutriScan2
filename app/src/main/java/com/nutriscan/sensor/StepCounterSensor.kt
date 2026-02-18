package com.nutriscan.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core step counter sensor manager.
 *
 * Strategy:
 * 1. **Primary** — TYPE_STEP_COUNTER (hardware pedometer). Reports total steps since
 *    last device reboot. We compute deltas from the first value received in this session.
 * 2. **Fallback** — TYPE_ACCELEROMETER with [AccelerometerStepDetector] peak-detection
 *    algorithm. Used when TYPE_STEP_COUNTER is not available (e.g. emulators).
 *
 * Sensor events are delivered on a background [HandlerThread] to avoid blocking the UI.
 *
 * This class is lifecycle-unaware by design — it is managed by [StepCounterService].
 */
class StepCounterSensor(
    private val context: Context
) {
    companion object {
        private const val TAG = "StepCounterSensor"
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // ---- State ----

    private val _stepsSinceStart = MutableStateFlow(0)
    /** Steps counted during this session (since [start] was called). */
    val stepsSinceStart: StateFlow<Int> = _stepsSinceStart.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _usingHardwareSensor = MutableStateFlow(false)
    /** True if TYPE_STEP_COUNTER is in use; false for accelerometer fallback. */
    val usingHardwareSensor: StateFlow<Boolean> = _usingHardwareSensor.asStateFlow()

    // ---- Internals ----

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    // For TYPE_STEP_COUNTER delta calculation
    private var initialSensorValue: Int = -1
    private var lastRawSensorValue: Int = -1

    // For accelerometer fallback
    private var accelerometerDetector: AccelerometerStepDetector? = null
    private var accelerometerStepCount: Int = 0

    // The active listener (either step-counter or accelerometer)
    private var activeListener: SensorEventListener? = null

    // ---- Public API ----

    /** Check whether hardware TYPE_STEP_COUNTER is available on this device. */
    fun isStepCounterAvailable(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /** Check whether accelerometer is available. */
    fun isAccelerometerAvailable(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    /**
     * Start listening for step events.
     *
     * @param previousSteps  Steps already recorded for today (from persistence).
     *                       Used to set the initial offset so the flow emits the
     *                       cumulative count rather than just this session's steps.
     */
    fun start(previousSteps: Int = 0) {
        if (_isRunning.value) {
            Log.w(TAG, "Already running, ignoring start()")
            return
        }

        _stepsSinceStart.value = previousSteps

        // Create background handler thread for sensor callbacks
        handlerThread = HandlerThread("StepCounterThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        if (isStepCounterAvailable()) {
            startHardwareStepCounter()
        } else if (isAccelerometerAvailable()) {
            startAccelerometerFallback()
        } else {
            Log.e(TAG, "No step counter or accelerometer sensor available!")
            return
        }

        _isRunning.value = true
    }

    /** Stop listening for step events and release resources. */
    fun stop() {
        if (!_isRunning.value) return

        activeListener?.let { sensorManager.unregisterListener(it) }
        activeListener = null
        accelerometerDetector = null

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        _isRunning.value = false
        Log.d(TAG, "Stopped. Total steps this session: ${_stepsSinceStart.value}")
    }

    /** Get the last raw sensor value (for persistence). Returns -1 if using accelerometer. */
    fun getLastRawSensorValue(): Int = lastRawSensorValue

    /** Reset session counters (e.g. for midnight rollover). */
    fun resetSession() {
        initialSensorValue = -1
        lastRawSensorValue = -1
        accelerometerStepCount = 0
        _stepsSinceStart.value = 0
        accelerometerDetector?.reset()
        Log.d(TAG, "Session reset")
    }

    // ---- Private: Hardware Step Counter ----

    private fun startHardwareStepCounter() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!
        _usingHardwareSensor.value = true

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
                val rawValue = event.values[0].toInt()

                if (initialSensorValue == -1) {
                    // First reading — just store the baseline
                    initialSensorValue = rawValue
                    lastRawSensorValue = rawValue
                    Log.d(TAG, "Hardware step counter baseline: $rawValue")
                    return
                }

                val delta = rawValue - lastRawSensorValue
                if (delta > 0) {
                    lastRawSensorValue = rawValue
                    _stepsSinceStart.value += delta
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        activeListener = listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, handler)
        Log.d(TAG, "Started with hardware TYPE_STEP_COUNTER")
    }

    // ---- Private: Accelerometer Fallback ----

    private fun startAccelerometerFallback() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        _usingHardwareSensor.value = false

        val detector = AccelerometerStepDetector {
            accelerometerStepCount++
            _stepsSinceStart.value = _stepsSinceStart.value + 1
        }
        accelerometerDetector = detector

        activeListener = detector
        sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
        Log.i(TAG, "Started with accelerometer fallback (TYPE_STEP_COUNTER unavailable)")
    }
}
