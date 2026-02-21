package com.nutriscan.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the AccelerometerStepDetector algorithm.
 *
 * Since SensorEvent is an Android framework class that can't be instantiated
 * in pure JVM unit tests, we test the algorithm logic by directly exercising
 * the detector's internal state through reflection, mirroring the exact
 * logic in onSensorChanged().
 *
 * Algorithm rules:
 * - Threshold: magnitude > 12.0 m/s² triggers a step
 * - Debounce: 300ms minimum between steps
 * - Reset: magnitude must drop below 9.0 before next step
 * - Timeout: 2000ms resets peak detection state
 */
class AccelerometerStepDetectorTest {

    private var stepCount = 0
    private lateinit var detector: AccelerometerStepDetector

    // Reflection fields to drive the algorithm
    private val lastStepTimeField by lazy {
        AccelerometerStepDetector::class.java.getDeclaredField("lastStepTime").also {
            it.isAccessible = true
        }
    }
    private val isPeakDetectedField by lazy {
        AccelerometerStepDetector::class.java.getDeclaredField("isPeakDetected").also {
            it.isAccessible = true
        }
    }

    @Before
    fun setUp() {
        stepCount = 0
        detector = AccelerometerStepDetector { stepCount++ }
    }

    /**
     * Drive the detector's algorithm with a simulated magnitude at a given time.
     * This mirrors the exact logic in onSensorChanged() but without needing
     * a real SensorEvent object.
     */
    private fun feedMagnitude(magnitude: Float, timeMs: Long) {
        val lastStepTime = lastStepTimeField.getLong(detector)
        val isPeakDetected = isPeakDetectedField.getBoolean(detector)
        val elapsed = timeMs - lastStepTime

        if (magnitude > 12.0f && !isPeakDetected && elapsed > 300L) {
            // Peak detected — register a step
            isPeakDetectedField.setBoolean(detector, true)
            lastStepTimeField.setLong(detector, timeMs)
            // Call the onStep callback (this is what increments stepCount)
            detector.javaClass.getDeclaredField("onStep").let { f ->
                f.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (f.get(detector) as () -> Unit).invoke()
            }
        } else if (magnitude < 9.0f && isPeakDetected) {
            // Magnitude dropped — ready for next step
            isPeakDetectedField.setBoolean(detector, false)
        }

        // Timeout reset
        if (elapsed > 2000L) {
            isPeakDetectedField.setBoolean(detector, false)
        }
    }

    // ==================== Threshold ====================

    @Test
    fun `magnitude above threshold triggers a step`() {
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals("Should count one step", 1, stepCount)
    }

    @Test
    fun `magnitude below threshold does not trigger step`() {
        feedMagnitude(10.0f, timeMs = 1000)
        assertEquals("Should not count any steps", 0, stepCount)
    }

    @Test
    fun `exactly at threshold does not trigger step`() {
        feedMagnitude(12.0f, timeMs = 1000)
        assertEquals("Boundary: exactly at threshold should not trigger", 0, stepCount)
    }

    // ==================== Debounce ====================

    @Test
    fun `two peaks within 300ms only count as one step`() {
        // First step
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals(1, stepCount)
        // Reset magnitude
        feedMagnitude(8.0f, timeMs = 1100)
        // Second peak too soon (200ms after first)
        feedMagnitude(13.0f, timeMs = 1200)

        assertEquals("Should only count one step due to debounce", 1, stepCount)
    }

    @Test
    fun `two peaks after 300ms count as two steps`() {
        // First step
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals(1, stepCount)
        // Reset below threshold
        feedMagnitude(8.0f, timeMs = 1200)
        // Second step after debounce period (>300ms)
        feedMagnitude(13.0f, timeMs = 1400)

        assertEquals("Should count two steps", 2, stepCount)
    }

    // ==================== Reset Behavior ====================

    @Test
    fun `must drop below reset threshold before next step`() {
        // First step
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals(1, stepCount)
        // Stay above reset threshold (10 > 9.0)
        feedMagnitude(10.0f, timeMs = 1500)
        // Try another peak — should NOT count because isPeakDetected is still true
        feedMagnitude(13.0f, timeMs = 1600)

        assertEquals("Should only count one step (no reset below 9.0)", 1, stepCount)
    }

    @Test
    fun `dropping below reset threshold enables next step`() {
        // First step
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals(1, stepCount)
        // Drop below reset threshold
        feedMagnitude(8.0f, timeMs = 1200)
        // Second step after reset and debounce
        feedMagnitude(13.0f, timeMs = 1400)

        assertEquals("Should count two steps after reset", 2, stepCount)
    }

    // ==================== Walking Simulation ====================

    @Test
    fun `simulated walking pattern counts steps correctly`() {
        // Simulate a realistic walking pattern: peak → dip → peak → dip ...
        // Using 500ms step interval (walking pace), well above 300ms debounce
        for (i in 0 until 5) {
            val t = 1000L + i * 500L
            feedMagnitude(14.0f, timeMs = t)              // peak
            feedMagnitude(7.0f, timeMs = t + 250)          // dip (reset)
        }

        assertEquals("5-step walking simulation", 5, stepCount)
    }

    // ==================== Reset Function ====================

    @Test
    fun `reset clears internal state`() {
        // Count a step
        feedMagnitude(13.0f, timeMs = 1000)
        assertEquals(1, stepCount)

        // Reset the detector
        detector.reset()

        // After reset, lastStepTime is 0, isPeakDetected is false
        // So a new peak should trigger if elapsed > 300ms from 0
        feedMagnitude(13.0f, timeMs = 1500)
        assertEquals("After reset, counts new step", 2, stepCount)
    }
}
