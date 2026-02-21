package com.nutriscan.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AICoachRepository's insight generation algorithms.
 *
 * Since AICoachRepository can't be instantiated without its full DI graph,
 * we replicate the exact insight generator logic here to validate
 * percentage thresholds, message types, and edge cases.
 *
 * Covers:
 * - Morning insight: breakfast reminder when calories = 0
 * - Afternoon insight: pacing feedback (<30%, 40-60%)
 * - Evening insight: over/under/on-track feedback (>110%, 85-105%, <60%)
 * - Macro insight: protein/carbs/fat balance analysis
 */
class AICoachInsightTest {

    // ============ Algorithm replicas from AICoachRepository ============

    private fun generateMorningInsight(currentCalories: Int, calorieGoal: Int): CoachInsight? {
        if (currentCalories == 0) {
            return CoachInsight(
                emoji = "🌅",
                message = "Good morning! Start your day right — a balanced breakfast sets the tone for hitting your ${calorieGoal} kcal goal.",
                type = InsightType.TIP
            )
        }
        return null
    }

    private fun generateAfternoonInsight(currentCalories: Int, calorieGoal: Int): CoachInsight? {
        if (calorieGoal <= 0) return null
        val pct = (currentCalories * 100f / calorieGoal).toInt()
        return when {
            pct < 30 -> CoachInsight(
                emoji = "🍽️",
                message = "You're only at $pct% of your daily goal. Don't skip lunch — your body needs fuel!",
                type = InsightType.WARNING
            )
            pct in 40..60 -> CoachInsight(
                emoji = "👍",
                message = "Nice pacing! You're at $pct% by afternoon — right on track.",
                type = InsightType.SUCCESS
            )
            else -> null
        }
    }

    private fun generateEveningInsight(currentCalories: Int, calorieGoal: Int): CoachInsight? {
        if (calorieGoal <= 0) return null
        val pct = (currentCalories * 100f / calorieGoal).toInt()
        return when {
            pct > 110 -> CoachInsight(
                emoji = "⚠️",
                message = "You're ${pct - 100}% over your calorie goal. Consider a lighter dinner or a walk!",
                type = InsightType.WARNING
            )
            pct in 85..105 -> CoachInsight(
                emoji = "🎯",
                message = "Almost perfect! You're at $pct% of your daily goal — great discipline today!",
                type = InsightType.SUCCESS
            )
            pct < 60 -> CoachInsight(
                emoji = "🍽️",
                message = "Only $pct% of your goal with the day nearly done. Make sure you're eating enough!",
                type = InsightType.WARNING
            )
            else -> null
        }
    }

    private data class MacroTotals(val protein: Float, val carbs: Float, val fat: Float)

    private fun generateMacroInsight(macros: MacroTotals, calorieGoal: Int): CoachInsight? {
        if (calorieGoal <= 0) return null
        val proteinGoalG = calorieGoal * 0.25f / 4f
        val carbGoalG = calorieGoal * 0.50f / 4f
        val fatGoalG = calorieGoal * 0.25f / 9f

        val proteinPct = if (proteinGoalG > 0) (macros.protein / proteinGoalG * 100).toInt() else 0
        val carbPct = if (carbGoalG > 0) (macros.carbs / carbGoalG * 100).toInt() else 0
        val fatPct = if (fatGoalG > 0) (macros.fat / fatGoalG * 100).toInt() else 0

        return when {
            proteinPct < 40 && macros.protein > 0 -> CoachInsight(
                emoji = "🥩",
                message = "Protein is at $proteinPct% of your target. Try adding Greek yogurt, eggs, or chicken to your next meal!",
                type = InsightType.TIP
            )
            carbPct < 40 && macros.carbs > 0 -> CoachInsight(
                emoji = "🍚",
                message = "Carbs are at $carbPct% — consider some oatmeal, rice, or fruit to fuel your energy.",
                type = InsightType.TIP
            )
            fatPct > 120 -> CoachInsight(
                emoji = "🫒",
                message = "Fat intake is at $fatPct% of your goal. Watch out for fried or oily foods in your remaining meals.",
                type = InsightType.WARNING
            )
            proteinPct in 90..110 && carbPct in 80..120 -> CoachInsight(
                emoji = "⚖️",
                message = "Your macros are beautifully balanced today! Keep it up 💪",
                type = InsightType.SUCCESS
            )
            else -> null
        }
    }

    // ==================== Morning Insight ====================

    @Test
    fun `morning with zero calories shows breakfast reminder`() {
        val result = generateMorningInsight(0, 2000)
        assertNotNull(result)
        assertEquals(InsightType.TIP, result!!.type)
        assertTrue(result.message.contains("breakfast"))
    }

    @Test
    fun `morning with some calories returns null`() {
        assertNull(generateMorningInsight(500, 2000))
    }

    // ==================== Afternoon Insight ====================

    @Test
    fun `afternoon at less than 30 percent shows warning`() {
        val result = generateAfternoonInsight(400, 2000) // 20%
        assertNotNull(result)
        assertEquals(InsightType.WARNING, result!!.type)
        assertTrue(result.message.contains("20%"))
    }

    @Test
    fun `afternoon at 50 percent shows success`() {
        val result = generateAfternoonInsight(1000, 2000) // 50%
        assertNotNull(result)
        assertEquals(InsightType.SUCCESS, result!!.type)
        assertTrue(result.message.contains("on track"))
    }

    @Test
    fun `afternoon at 80 percent returns null`() {
        assertNull(generateAfternoonInsight(1600, 2000)) // 80%
    }

    @Test
    fun `afternoon at exactly 30 pct returns null (range is strictly lt 30)`() {
        assertNull(generateAfternoonInsight(600, 2000)) // 30% → not < 30
    }

    @Test
    fun `afternoon with zero calorie goal returns null`() {
        assertNull(generateAfternoonInsight(500, 0))
    }

    // ==================== Evening Insight ====================

    @Test
    fun `evening over 110 percent shows over-eating warning`() {
        val result = generateEveningInsight(2300, 2000) // 115%
        assertNotNull(result)
        assertEquals(InsightType.WARNING, result!!.type)
        assertTrue(result.message.contains("15%"))
    }

    @Test
    fun `evening at 95 percent shows near-perfect success`() {
        val result = generateEveningInsight(1900, 2000) // 95%
        assertNotNull(result)
        assertEquals(InsightType.SUCCESS, result!!.type)
    }

    @Test
    fun `evening under 60 percent shows under-eating warning`() {
        val result = generateEveningInsight(1000, 2000) // 50%
        assertNotNull(result)
        assertEquals(InsightType.WARNING, result!!.type)
        assertTrue(result.message.contains("eating enough"))
    }

    @Test
    fun `evening at 70 percent returns null (no trigger)`() {
        assertNull(generateEveningInsight(1400, 2000)) // 70% → no range
    }

    @Test
    fun `evening at exactly 110 percent returns null (range is strictly gt 110)`() {
        assertNull(generateEveningInsight(2200, 2000)) // 110% → not > 110
    }

    // ==================== Macro Insight ====================

    @Test
    fun `low protein intake triggers protein tip`() {
        // protein goal = 125g, eating 30g → 24% < 40%
        val result = generateMacroInsight(MacroTotals(30f, 100f, 20f), 2000)
        assertNotNull(result)
        assertEquals(InsightType.TIP, result!!.type)
        assertTrue(result.message.contains("Protein"))
    }

    @Test
    fun `low carb intake triggers carb tip`() {
        // carb goal = 250g, eating 80g → 32% < 40%
        // protein goal = 125g, eating 60g → 48% > 40%
        val result = generateMacroInsight(MacroTotals(60f, 80f, 20f), 2000)
        assertNotNull(result)
        assertEquals(InsightType.TIP, result!!.type)
        assertTrue(result.message.contains("Carbs"))
    }

    @Test
    fun `high fat intake triggers fat warning`() {
        // fat goal ≈ 55.6g, eating 70g → 126% > 120%
        val result = generateMacroInsight(MacroTotals(80f, 200f, 70f), 2000)
        assertNotNull(result)
        assertEquals(InsightType.WARNING, result!!.type)
    }

    @Test
    fun `balanced macros trigger success message`() {
        val result = generateMacroInsight(MacroTotals(125f, 250f, 50f), 2000)
        assertNotNull(result)
        assertEquals(InsightType.SUCCESS, result!!.type)
        assertTrue(result.message.contains("balanced"))
    }

    @Test
    fun `zero calorie goal returns null`() {
        assertNull(generateMacroInsight(MacroTotals(50f, 100f, 30f), 0))
    }

    @Test
    fun `all zeros macros returns null`() {
        assertNull(generateMacroInsight(MacroTotals(0f, 0f, 0f), 2000))
    }
}
