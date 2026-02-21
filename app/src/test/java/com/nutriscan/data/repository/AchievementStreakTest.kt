package com.nutriscan.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the streak counting algorithm used in AchievementRepository.
 *
 * Since AchievementRepository can't be instantiated without its full DI graph,
 * we replicate the exact `countConsecutiveDaysFromYesterday` algorithm here
 * to validate its mathematical properties.
 *
 * Algorithm (from AchievementRepository.kt:187-197):
 * - Count consecutive days backwards starting from yesterday
 * - If today also qualifies, add 1 to the streak
 * - A gap in qualifying days breaks the backward count
 */
class AchievementStreakTest {

    /**
     * Exact replica of AchievementRepository.countConsecutiveDaysFromYesterday().
     */
    private fun countConsecutiveDaysFromYesterday(qualifyingDays: Set<LocalDate>): Int {
        var streak = 0
        var checkDate = LocalDate.now().minusDays(1)
        while (qualifyingDays.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        // Also count today if it already qualifies
        if (qualifyingDays.contains(LocalDate.now())) streak++
        return streak
    }

    private val today: LocalDate get() = LocalDate.now()
    private val yesterday: LocalDate get() = today.minusDays(1)

    // ==================== Basic Behavior ====================

    @Test
    fun `empty set returns zero streak`() {
        assertEquals(0, countConsecutiveDaysFromYesterday(emptySet()))
    }

    @Test
    fun `only yesterday qualifies gives streak of 1`() {
        assertEquals(1, countConsecutiveDaysFromYesterday(setOf(yesterday)))
    }

    @Test
    fun `yesterday and day before gives streak of 2`() {
        assertEquals(2, countConsecutiveDaysFromYesterday(setOf(yesterday, yesterday.minusDays(1))))
    }

    @Test
    fun `5 consecutive days ending yesterday gives streak of 5`() {
        val days = (1L..5L).map { today.minusDays(it) }.toSet()
        assertEquals(5, countConsecutiveDaysFromYesterday(days))
    }

    // ==================== Today Bonus ====================

    @Test
    fun `today only (no yesterday) gives streak of 1`() {
        assertEquals(1, countConsecutiveDaysFromYesterday(setOf(today)))
    }

    @Test
    fun `today and yesterday gives streak of 2`() {
        assertEquals(2, countConsecutiveDaysFromYesterday(setOf(today, yesterday)))
    }

    @Test
    fun `today and 3 consecutive days gives streak of 4`() {
        val days = setOf(today, yesterday, yesterday.minusDays(1), yesterday.minusDays(2))
        assertEquals(4, countConsecutiveDaysFromYesterday(days))
    }

    // ==================== Gap Breaking ====================

    @Test
    fun `gap 2 days ago breaks streak`() {
        // Yesterday qualifies, 2 days ago does NOT, 3 days ago does
        val days = setOf(yesterday, yesterday.minusDays(2))
        assertEquals(1, countConsecutiveDaysFromYesterday(days))
    }

    @Test
    fun `gap yesterday breaks entire streak`() {
        // Today qualifies but yesterday does NOT
        val days = setOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, countConsecutiveDaysFromYesterday(days))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `very long streak of 30 days`() {
        val days = (0L..30L).map { today.minusDays(it) }.toSet()
        assertEquals(31, countConsecutiveDaysFromYesterday(days))
    }

    @Test
    fun `future dates are ignored`() {
        val days = setOf(today.plusDays(1), today.plusDays(2))
        assertEquals(0, countConsecutiveDaysFromYesterday(days))
    }

    @Test
    fun `old dates beyond the gap are ignored`() {
        val recentDays = setOf(yesterday)
        val oldDays = (10L..15L).map { today.minusDays(it) }.toSet()
        assertEquals(1, countConsecutiveDaysFromYesterday(recentDays + oldDays))
    }
}
