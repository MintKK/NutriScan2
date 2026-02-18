package com.nutriscan.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Accelerometer-based step detector used as fallback when TYPE_STEP_COUNTER
 * hardware is unavailable (e.g. on emulators).
 *
 * Uses magnitude peak detection with timing constraints to identify steps.
 * This is a simplified algorithm suitable for walking; not as accurate as
 * a dedicated hardware pedometer, but functional for testing and basic use.
 */
class AccelerometerStepDetector(
    private val onStep: () -> Unit
) : SensorEventListener {

    companion object {
        /** Acceleration magnitude threshold to consider as a step peak (m/s²). */
        private const val STEP_THRESHOLD = 12.0f

        /** Minimum time between steps to avoid double-counting (ms). */
        private const val STEP_DELAY_MS = 300L

        /** Maximum time between steps — if exceeded, we require a stronger signal (ms). */
        private const val STEP_TIMEOUT_MS = 2000L

        /** Lower threshold — acceleration must dip below this to "reset" for next step. */
        private const val RESET_THRESHOLD = 9.0f
    }

    private var lastStepTime = 0L
    private var isPeakDetected = false

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        val now = System.currentTimeMillis()
        val elapsed = now - lastStepTime

        if (magnitude > STEP_THRESHOLD && !isPeakDetected && elapsed > STEP_DELAY_MS) {
            // Peak detected — register a step
            isPeakDetected = true
            lastStepTime = now
            onStep()
        } else if (magnitude < RESET_THRESHOLD && isPeakDetected) {
            // Magnitude dropped back below reset threshold — ready for next step
            isPeakDetected = false
        }

        // If too long since last step, reset peak detection state
        if (elapsed > STEP_TIMEOUT_MS) {
            isPeakDetected = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step detection
    }

    /** Reset detection state (e.g. on midnight rollover). */
    fun reset() {
        lastStepTime = 0L
        isPeakDetected = false
    }
}
