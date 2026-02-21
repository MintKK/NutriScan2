package com.nutriscan.sensor

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for StepCounterService companion-object utility functions.
 *
 * Tests stride-length selection per activity type and activity pausing logic.
 */
class StepCounterServiceTest {

    // ==================== getStrideForActivity ====================

    @Test
    fun `walking returns standard stride of 0_762m`() {
        assertEquals(0.762, StepCounterService.getStrideForActivity("WALKING"), 0.001)
    }

    @Test
    fun `running returns stride of 1_0m`() {
        assertEquals(1.0, StepCounterService.getStrideForActivity("RUNNING"), 0.001)
    }

    @Test
    fun `cycling returns stride of 0_0m (not step based)`() {
        assertEquals(0.0, StepCounterService.getStrideForActivity("CYCLING"), 0.001)
    }

    @Test
    fun `unknown activity falls back to default stride`() {
        assertEquals(0.762, StepCounterService.getStrideForActivity("SWIMMING"), 0.001)
    }

    @Test
    fun `empty string falls back to default stride`() {
        assertEquals(0.762, StepCounterService.getStrideForActivity(""), 0.001)
    }

    // ==================== isActivityPaused ====================

    @Test
    fun `IN_VEHICLE is paused`() {
        assertTrue(StepCounterService.isActivityPaused("IN_VEHICLE"))
    }

    @Test
    fun `WALKING is not paused`() {
        assertFalse(StepCounterService.isActivityPaused("WALKING"))
    }

    @Test
    fun `RUNNING is not paused`() {
        assertFalse(StepCounterService.isActivityPaused("RUNNING"))
    }

    @Test
    fun `STILL is not paused`() {
        assertFalse(StepCounterService.isActivityPaused("STILL"))
    }

    @Test
    fun `empty string is not paused`() {
        assertFalse(StepCounterService.isActivityPaused(""))
    }

    // ==================== Stride-based distance calculation ====================

    @Test
    fun `1000 walking steps equals approximately 762 meters`() {
        val steps = 1000
        val stride = StepCounterService.getStrideForActivity("WALKING")
        val distance = steps * stride
        assertEquals(762.0, distance, 0.1)
    }

    @Test
    fun `1000 running steps equals approximately 1000 meters`() {
        val steps = 1000
        val stride = StepCounterService.getStrideForActivity("RUNNING")
        val distance = steps * stride
        assertEquals(1000.0, distance, 0.1)
    }

    @Test
    fun `cycling steps produce 0 distance (stride is 0)`() {
        val steps = 5000
        val stride = StepCounterService.getStrideForActivity("CYCLING")
        val distance = steps * stride
        assertEquals(0.0, distance, 0.001)
    }
}
