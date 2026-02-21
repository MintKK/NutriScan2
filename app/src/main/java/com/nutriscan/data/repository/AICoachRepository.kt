package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.dao.WaterLogDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// ============ DATA MODELS ============

enum class InsightType { SUCCESS, INFO, WARNING, TIP }

data class CoachInsight(
    val emoji: String,
    val message: String,
    val type: InsightType,
    val actionLabel: String? = null,
    val actionData: String? = null
)

// ============ REPOSITORY ============

@Singleton
class AICoachRepository @Inject constructor(
    private val mealRepository: MealRepository,
    private val waterLogDao: WaterLogDao,
    private val achievementRepository: AchievementRepository
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Generate a prioritized list of contextual insights based on the user's
     * current progress. Returns at most 3 insights, ordered by relevance.
     */
    suspend fun generateInsights(
        currentMacros: MacroTotals,
        calorieGoal: Int,
        currentCalories: Int,
        currentWaterMl: Int,
        waterGoalMl: Int,
        proteinGoalG: Float = 0f,
        carbGoalG: Float = 0f,
        fatGoalG: Float = 0f
    ): List<CoachInsight> {
        val insights = mutableListOf<CoachInsight>()
        val hour = LocalTime.now().hour

        // 1. Greeting based on time of day
        val greeting = when {
            hour < 12 -> generateMorningInsight(currentCalories, calorieGoal)
            hour < 17 -> generateAfternoonInsight(currentCalories, calorieGoal)
            else      -> generateEveningInsight(currentCalories, calorieGoal)
        }
        if (greeting != null) insights.add(greeting)

        // 2. Macro analysis — uses personalized targets if available
        val macroInsight = generateMacroInsight(currentMacros, calorieGoal, proteinGoalG, carbGoalG, fatGoalG)
        if (macroInsight != null) insights.add(macroInsight)

        // 3. Hydration comparison with yesterday
        val waterInsight = generateWaterInsight(currentWaterMl, waterGoalMl)
        if (waterInsight != null) insights.add(waterInsight)

        // 4. Streak celebration
        val streakInsight = generateStreakInsight(waterGoalMl)
        if (streakInsight != null) insights.add(streakInsight)

        // 5. Calorie deficit consistency
        val deficitInsight = generateDeficitInsight(calorieGoal)
        if (deficitInsight != null) insights.add(deficitInsight)

        // Return top 3 most relevant
        return insights.take(3)
    }

    // ============ INSIGHT GENERATORS ============

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

    private fun generateMacroInsight(
        macros: MacroTotals,
        calorieGoal: Int,
        userProteinGoalG: Float,
        userCarbGoalG: Float,
        userFatGoalG: Float
    ): CoachInsight? {
        if (calorieGoal <= 0) return null
        // Use personalized targets if available, otherwise fall back to generic split
        val proteinGoalG = if (userProteinGoalG > 0) userProteinGoalG else calorieGoal * 0.25f / 4f
        val carbGoalG = if (userCarbGoalG > 0) userCarbGoalG else calorieGoal * 0.50f / 4f
        val fatGoalG = if (userFatGoalG > 0) userFatGoalG else calorieGoal * 0.25f / 9f

        val proteinPct = if (proteinGoalG > 0) (macros.protein / proteinGoalG * 100).toInt() else 0
        val carbPct = if (carbGoalG > 0) (macros.carbs / carbGoalG * 100).toInt() else 0
        val fatPct = if (fatGoalG > 0) (macros.fat / fatGoalG * 100).toInt() else 0

        // Find the most deficient macro
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

    private suspend fun generateWaterInsight(currentWaterMl: Int, waterGoalMl: Int): CoachInsight? {
        if (waterGoalMl <= 0) return null

        // Get yesterday's total
        val yesterdayStart = LocalDate.now().minusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayTotals = waterLogDao.getDailyWaterTotals(yesterdayStart)
        val yesterdayStr = LocalDate.now().minusDays(1).format(formatter)
        val yesterdayTotal = yesterdayTotals.find { it.day == yesterdayStr }?.totalMl ?: 0

        val waterPct = (currentWaterMl * 100f / waterGoalMl).toInt()

        return when {
            currentWaterMl > yesterdayTotal && yesterdayTotal > 0 -> {
                val diff = currentWaterMl - yesterdayTotal
                CoachInsight(
                    emoji = "💧",
                    message = "You're ${diff}ml ahead of yesterday's water intake — great job staying hydrated!",
                    type = InsightType.SUCCESS
                )
            }
            waterPct >= 100 -> CoachInsight(
                emoji = "🎉",
                message = "You've hit your water goal for today! Stay consistent and earn the Water Warrior badge 💧",
                type = InsightType.SUCCESS
            )
            waterPct < 30 && LocalTime.now().hour > 14 -> CoachInsight(
                emoji = "💧",
                message = "Only $waterPct% of your water goal and it's past 2PM. Try keeping a bottle on your desk!",
                type = InsightType.WARNING
            )
            else -> null
        }
    }

    private suspend fun generateStreakInsight(waterGoalMl: Int): CoachInsight? {
        val state = achievementRepository.getAchievementState(
            waterGoalMl = waterGoalMl,
            proteinGoalG = 0f // We don't need protein for streak insight
        )
        val longestActive = state.streaks.maxByOrNull { it.currentStreak }
        return if (longestActive != null && longestActive.currentStreak >= 3) {
            val isNewBest = longestActive.currentStreak >= longestActive.bestStreak && longestActive.currentStreak > 0
            val message = if (isNewBest) {
                "🎉 NEW RECORD! You've reached a ${longestActive.currentStreak}-day ${longestActive.label} streak. You're at your all-time best!"
            } else {
                "${longestActive.currentStreak}-day ${longestActive.label} streak! Your consistency is paying off — keep going!"
            }
            CoachInsight(
                emoji = if (isNewBest) "🎊" else "🔥",
                message = message,
                type = InsightType.SUCCESS
            )
        } else null
    }

    private suspend fun generateDeficitInsight(calorieGoal: Int): CoachInsight? {
        if (calorieGoal <= 0) return null

        val sevenDaysAgoMs = LocalDate.now().minusDays(7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dailyCalories = mealRepository.getLast7DaysCalories().first()
        val deficitDays = dailyCalories.count { it.totalKcal in 1..(calorieGoal - 100) }

        return when {
            deficitDays >= 5 -> CoachInsight(
                emoji = "📉",
                message = "You've been in a calorie deficit for $deficitDays of the last 7 days. Your consistency is paying off!",
                type = InsightType.SUCCESS
            )
            deficitDays == 0 && dailyCalories.any { it.totalKcal > 0 } -> CoachInsight(
                emoji = "📊",
                message = "You haven't been under your calorie goal recently. If weight loss is your aim, try reducing portions slightly.",
                type = InsightType.INFO
            )
            else -> null
        }
    }

    // ============ SMART SWITCH SUGGESTIONS ============

    /**
     * Smart Swap Map: maps common high-calorie foods to healthier alternatives.
     * Key = lowercase keyword found in food name.
     * Value = (alternative name, reason string).
     */
    private val swapMap = mapOf(
        "pizza" to SwapSuggestion("Grilled Chicken Wrap", "~250 fewer kcal, +15g protein"),
        "donut" to SwapSuggestion("Greek Yogurt with Honey", "~200 fewer kcal, +12g protein"),
        "doughnut" to SwapSuggestion("Greek Yogurt with Honey", "~200 fewer kcal, +12g protein"),
        "burger" to SwapSuggestion("Turkey Lettuce Wrap", "~300 fewer kcal, less saturated fat"),
        "fries" to SwapSuggestion("Sweet Potato Wedges", "~100 fewer kcal, more fiber & vitamin A"),
        "french fries" to SwapSuggestion("Sweet Potato Wedges", "~100 fewer kcal, more fiber & vitamin A"),
        "fried chicken" to SwapSuggestion("Grilled Chicken Breast", "~150 fewer kcal, -10g fat"),
        "fried rice" to SwapSuggestion("Steamed Rice with Veggies", "~120 fewer kcal, less oil"),
        "ice cream" to SwapSuggestion("Frozen Yogurt", "~100 fewer kcal, +5g protein"),
        "chocolate" to SwapSuggestion("Dark Chocolate (85%)", "Less sugar, more antioxidants"),
        "cake" to SwapSuggestion("Protein Bar", "~150 fewer kcal, +15g protein"),
        "pasta" to SwapSuggestion("Zucchini Noodles with Sauce", "~200 fewer kcal, more fiber"),
        "soda" to SwapSuggestion("Sparkling Water with Lemon", "~140 fewer kcal, zero sugar"),
        "chips" to SwapSuggestion("Air-popped Popcorn", "~100 fewer kcal, whole grain fiber"),
        "nutella" to SwapSuggestion("Almond Butter", "Less sugar, healthy fats, +3g protein"),
        "hot dog" to SwapSuggestion("Chicken Sausage", "~80 fewer kcal, less sodium"),
        "pancake" to SwapSuggestion("Oat Pancakes", "More fiber, +5g protein, slower energy release"),
        "waffle" to SwapSuggestion("Oat Pancakes", "More fiber, +5g protein, slower energy release"),
        "bread" to SwapSuggestion("Whole Grain Bread", "More fiber, slower blood sugar rise"),
        "candy" to SwapSuggestion("Mixed Nuts (30g)", "Healthy fats, protein, and sustained energy"),
        "cookie" to SwapSuggestion("Apple Slices with Peanut Butter", "~100 fewer kcal, more fiber & protein")
    )

    /**
     * Analyze a food item and return a "Smart Switch" suggestion if applicable.
     * Triggers when:
     * 1. The food name matches a keyword in the swap map, OR
     * 2. The food is calorie-dense (>350 kcal/100g) AND protein-low (<8g/100g)
     */
    fun getSuggestionForFood(food: com.nutriscan.data.local.entity.FoodItem): CoachInsight? {
        val nameLower = food.name.lowercase()

        // 1. Check swap map by keyword
        for ((keyword, swap) in swapMap) {
            if (nameLower.contains(keyword)) {
                return CoachInsight(
                    emoji = "🔄",
                    message = "Try \"${swap.alternative}\" instead — ${swap.reason}",
                    type = InsightType.TIP,
                    actionLabel = "Swap it",
                    actionData = swap.alternative
                )
            }
        }

        // 2. Generic analysis: high calorie + low protein
        if (food.kcalPer100g > 350 && food.proteinPer100g < 8f) {
            return CoachInsight(
                emoji = "💡",
                message = "This is calorie-dense (${food.kcalPer100g} kcal/100g) with low protein. Consider a high-protein alternative!",
                type = InsightType.WARNING
            )
        }

        // 3. High fat warning
        if (food.fatPer100g > 25f && food.proteinPer100g < 10f) {
            return CoachInsight(
                emoji = "🫒",
                message = "High fat content (${food.fatPer100g}g/100g). A grilled or baked version would cut fat significantly.",
                type = InsightType.TIP
            )
        }

        return null
    }
}

private data class SwapSuggestion(
    val alternative: String,
    val reason: String
)

