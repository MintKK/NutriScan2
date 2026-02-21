package com.nutriscan.domain.usecase

import com.nutriscan.data.local.entity.FoodItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CalculateNutritionUseCase.
 *
 * Validates portion-based nutrition scaling from per-100g food data.
 */
class CalculateNutritionUseCaseTest {

    private lateinit var useCase: CalculateNutritionUseCase

    private val sampleFood = FoodItem(
        name = "chicken breast",
        kcalPer100g = 165,
        proteinPer100g = 31f,
        carbsPer100g = 0f,
        fatPer100g = 3.6f
    )

    @Before
    fun setUp() {
        useCase = CalculateNutritionUseCase()
    }

    // ==================== Basic Scaling ====================

    @Test
    fun `100g returns exact per-100g values`() {
        val result = useCase(sampleFood, 100)
        assertEquals(165, result.kcal)
        assertEquals(31f, result.protein, 0.01f)
        assertEquals(0f, result.carbs, 0.01f)
        assertEquals(3.6f, result.fat, 0.01f)
    }

    @Test
    fun `200g doubles all values`() {
        val result = useCase(sampleFood, 200)
        assertEquals(330, result.kcal)
        assertEquals(62f, result.protein, 0.01f)
        assertEquals(0f, result.carbs, 0.01f)
        assertEquals(7.2f, result.fat, 0.01f)
    }

    @Test
    fun `50g halves all values`() {
        val result = useCase(sampleFood, 50)
        assertEquals(82, result.kcal)   // 165 * 0.5 = 82.5, truncated to 82
        assertEquals(15.5f, result.protein, 0.01f)
        assertEquals(0f, result.carbs, 0.01f)
        assertEquals(1.8f, result.fat, 0.01f)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `zero grams returns zero nutrition`() {
        val result = useCase(sampleFood, 0)
        assertEquals(0, result.kcal)
        assertEquals(0f, result.protein, 0.01f)
        assertEquals(0f, result.carbs, 0.01f)
        assertEquals(0f, result.fat, 0.01f)
    }

    @Test
    fun `very small portion (10g) scales correctly`() {
        val result = useCase(sampleFood, 10)
        assertEquals(16, result.kcal)   // 165 * 0.1 = 16.5, truncated to 16
        assertEquals(3.1f, result.protein, 0.01f)
    }

    @Test
    fun `large portion (1000g) scales correctly`() {
        val result = useCase(sampleFood, 1000)
        assertEquals(1650, result.kcal)
        assertEquals(310f, result.protein, 0.01f)
        assertEquals(36f, result.fat, 0.01f)
    }

    // ==================== Different Food Profiles ====================

    @Test
    fun `high-carb food scales all macros`() {
        val rice = FoodItem(
            name = "rice",
            kcalPer100g = 130,
            proteinPer100g = 2.7f,
            carbsPer100g = 28f,
            fatPer100g = 0.3f
        )
        val result = useCase(rice, 250) // A typical bowl
        assertEquals(325, result.kcal)
        assertEquals(6.75f, result.protein, 0.01f)
        assertEquals(70f, result.carbs, 0.01f)
        assertEquals(0.75f, result.fat, 0.01f)
    }

    @Test
    fun `high-fat food scales correctly`() {
        val butter = FoodItem(
            name = "butter",
            kcalPer100g = 717,
            proteinPer100g = 0.85f,
            carbsPer100g = 0.06f,
            fatPer100g = 81.11f
        )
        val result = useCase(butter, 14) // 1 tablespoon
        assertEquals(100, result.kcal) // 717 * 0.14 = 100.38, truncated
        assertEquals(0.119f, result.protein, 0.01f)
    }
}
