package com.nutriscan.domain.model

/**
 * Computed nutrition values for a portion.
 */
data class NutritionResult(
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float
) {
    companion object {
        val ZERO = NutritionResult(0, 0f, 0f, 0f)
    }
}

/**
 * Portion preset options.
 */
enum class PortionPreset(val grams: Int, val displayName: String) {
    SMALL(150, "Small (150g)"),
    BOWL(250, "Bowl (250g)"),
    PLATE(400, "Plate (400g)"),
    LARGE(500, "Large (500g)");
    
    companion object {
        fun fromGrams(grams: Int): PortionPreset? = entries.find { it.grams == grams }
    }
}
