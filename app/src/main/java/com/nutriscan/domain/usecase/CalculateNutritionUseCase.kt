package com.nutriscan.domain.usecase

import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.domain.model.NutritionResult
import javax.inject.Inject

/**
 * Use case for calculating nutrition based on food and portion size.
 */
class CalculateNutritionUseCase @Inject constructor() {
    
    /**
     * Calculate nutrition for a given food item and weight.
     * @param food The food item with per-100g values.
     * @param grams The portion weight in grams.
     * @return Computed nutrition values.
     */
    operator fun invoke(food: FoodItem, grams: Int): NutritionResult {
        val factor = grams / 100f
        return NutritionResult(
            kcal = (food.kcalPer100g * factor).toInt(),
            protein = food.proteinPer100g * factor,
            carbs = food.carbsPer100g * factor,
            fat = food.fatPer100g * factor
        )
    }
}
