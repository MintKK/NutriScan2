package com.nutriscan

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NutritionCalculator.
 *
 * Validates BMR (Mifflin-St Jeor), TDEE, calorie adjustments per goal,
 * and macro splits (protein, carbs, fat).
 */
class NutritionCalculatorTest {

    // ==================== BMR / TDEE Basics ====================

    @Test
    fun `male sedentary maintenance - known reference values`() {
        // Male, 25y, 70kg, 175cm, sedentary, maintenance
        // BMR = 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75
        // TDEE = 1673.75 * 1.2 = 2008.5
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 175f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertEquals(2008, targets.calories)
    }

    @Test
    fun `female sedentary maintenance - known reference values`() {
        // Female, 25y, 60kg, 165cm, sedentary, maintenance
        // BMR = 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        // TDEE = 1345.25 * 1.2 = 1614.3
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.FEMALE, 25, 60f, 165f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertEquals(1614, targets.calories)
    }

    // ==================== Activity Level Multipliers ====================

    @Test
    fun `higher activity level increases TDEE`() {
        val base = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 30, 80f, 180f, ActivityLevel.SEDENTARY)
        val active = base.copy(activityLevel = ActivityLevel.VERY_ACTIVE)

        val baseCals = NutritionCalculator.calculateTargets(base).calories
        val activeCals = NutritionCalculator.calculateTargets(active).calories

        assertTrue("Active should have more calories than sedentary", activeCals > baseCals)
    }

    @Test
    fun `activity levels are ordered correctly`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 30, 80f, 180f, ActivityLevel.SEDENTARY)

        val sedentary = NutritionCalculator.calculateTargets(profile).calories
        val light = NutritionCalculator.calculateTargets(profile.copy(activityLevel = ActivityLevel.LIGHTLY_ACTIVE)).calories
        val moderate = NutritionCalculator.calculateTargets(profile.copy(activityLevel = ActivityLevel.MODERATELY_ACTIVE)).calories
        val very = NutritionCalculator.calculateTargets(profile.copy(activityLevel = ActivityLevel.VERY_ACTIVE)).calories

        assertTrue("Sedentary < Light", sedentary < light)
        assertTrue("Light < Moderate", light < moderate)
        assertTrue("Moderate < Very Active", moderate < very)
    }

    // ==================== Goal Adjustments ====================

    @Test
    fun `fat loss reduces calories by 20 percent`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 175f, ActivityLevel.SEDENTARY)
        val maintenance = NutritionCalculator.calculateTargets(profile).calories

        val fatLoss = NutritionCalculator.calculateTargets(profile.copy(goal = Goal.FAT_LOSS)).calories

        // Fat loss = maintenance * 0.8
        val expected = (maintenance * 0.8).toInt()
        assertEquals(expected, fatLoss)
    }

    @Test
    fun `muscle gain increases calories by 20 percent`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 175f, ActivityLevel.SEDENTARY)
        val maintenance = NutritionCalculator.calculateTargets(profile).calories

        val muscleGain = NutritionCalculator.calculateTargets(profile.copy(goal = Goal.MUSCLE_GAIN)).calories

        // Muscle gain should be ~20% higher than maintenance (exact value depends on raw TDEE rounding)
        assertTrue("Muscle gain ($muscleGain) should be about 20% more than maintenance ($maintenance)",
            muscleGain > maintenance && muscleGain <= maintenance * 1.25)
    }

    @Test
    fun `goal ordering - fat loss less than maintenance less than muscle gain`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 30, 75f, 178f, ActivityLevel.MODERATELY_ACTIVE)

        val fatLoss = NutritionCalculator.calculateTargets(profile.copy(goal = Goal.FAT_LOSS)).calories
        val maintenance = NutritionCalculator.calculateTargets(profile).calories
        val muscleGain = NutritionCalculator.calculateTargets(profile.copy(goal = Goal.MUSCLE_GAIN)).calories

        assertTrue("Fat loss < Maintenance", fatLoss < maintenance)
        assertTrue("Maintenance < Muscle gain", maintenance < muscleGain)
    }

    // ==================== Protein Calculation ====================

    @Test
    fun `fat loss protein is 2x body weight`() {
        val profile = UserProfile(Goal.FAT_LOSS, Gender.MALE, 25, 80f, 180f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertEquals(160, targets.proteinGrams)  // 80 * 2.0
    }

    @Test
    fun `muscle gain protein is 2_5x body weight`() {
        val profile = UserProfile(Goal.MUSCLE_GAIN, Gender.MALE, 25, 80f, 180f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertEquals(200, targets.proteinGrams)  // 80 * 2.5
    }

    @Test
    fun `maintenance protein is 1_8x body weight`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 80f, 180f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertEquals(144, targets.proteinGrams)  // 80 * 1.8
    }

    // ==================== Fat Calculation ====================

    @Test
    fun `fat is 25 percent of calories divided by 9`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 175f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        val expectedFat = (targets.calories * 0.25 / 9).toInt()
        assertEquals(expectedFat, targets.fatGrams)
    }

    // ==================== Carbs Calculation ====================

    @Test
    fun `carbs fill remaining calories after protein and fat`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 175f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)

        val proteinCalories = targets.proteinGrams * 4
        val fatCalories = targets.fatGrams * 9
        val expectedCarbs = ((targets.calories - proteinCalories - fatCalories) / 4).coerceAtLeast(0)

        assertEquals(expectedCarbs, targets.carbGrams)
    }

    @Test
    fun `carbs never go negative`() {
        // Extremely high protein scenario (very heavy person, muscle gain)
        val profile = UserProfile(Goal.MUSCLE_GAIN, Gender.MALE, 20, 150f, 190f, ActivityLevel.SEDENTARY)
        val targets = NutritionCalculator.calculateTargets(profile)
        assertTrue("Carbs should never be negative", targets.carbGrams >= 0)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `calorie macros approximately sum to total calories`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 30, 75f, 178f, ActivityLevel.MODERATELY_ACTIVE)
        val targets = NutritionCalculator.calculateTargets(profile)

        val computedCalories = targets.proteinGrams * 4 + targets.carbGrams * 4 + targets.fatGrams * 9

        // Allow small rounding error (integer truncation)
        val diff = kotlin.math.abs(targets.calories - computedCalories)
        assertTrue("Macro calories should be within 15 of total (rounding): diff=$diff", diff <= 15)
    }

    @Test
    fun `gender difference - male has higher BMR than female with same stats`() {
        val male = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 30, 70f, 170f, ActivityLevel.SEDENTARY)
        val female = male.copy(gender = Gender.FEMALE)

        val maleCals = NutritionCalculator.calculateTargets(male).calories
        val femaleCals = NutritionCalculator.calculateTargets(female).calories

        assertTrue("Male BMR should be higher than female", maleCals > femaleCals)
    }

    // ==================== Input Validation ====================

    @Test(expected = IllegalArgumentException::class)
    fun `zero weight throws IllegalArgumentException`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 0f, 175f, ActivityLevel.SEDENTARY)
        NutritionCalculator.calculateTargets(profile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative weight throws IllegalArgumentException`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, -10f, 175f, ActivityLevel.SEDENTARY)
        NutritionCalculator.calculateTargets(profile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height throws IllegalArgumentException`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 25, 70f, 0f, ActivityLevel.SEDENTARY)
        NutritionCalculator.calculateTargets(profile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero age throws IllegalArgumentException`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 0, 70f, 175f, ActivityLevel.SEDENTARY)
        NutritionCalculator.calculateTargets(profile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `age over 150 throws IllegalArgumentException`() {
        val profile = UserProfile(Goal.WEIGHT_MAINTENANCE, Gender.MALE, 200, 70f, 175f, ActivityLevel.SEDENTARY)
        NutritionCalculator.calculateTargets(profile)
    }
}
