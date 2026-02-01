package com.nutriscan.ml

import android.graphics.Bitmap

/**
 * Interface for food classification services.
 * 
 * This abstraction allows swapping between:
 * - MLKitFoodClassifier (current, generic + filter)
 * - TFLiteFoodClassifier (future, food-trained model)
 * 
 * IMPORTANT: Implementations should only return food-related labels.
 * Non-food labels must be filtered out or never returned.
 */
interface FoodClassificationService {
    
    /**
     * Classify an image and return food-only results.
     * 
     * @param bitmap The image to classify
     * @return FoodClassificationResult with status and food-only labels
     */
    suspend fun classifyFood(bitmap: Bitmap): FoodClassificationResult
    
    /**
     * Human-readable name for logging/debugging.
     */
    val classifierName: String
    
    /**
     * Whether this classifier uses a food-trained model.
     * When true, results can be trusted more for auto-selection.
     */
    val isFoodTrained: Boolean
}

/**
 * Result from food classification.
 */
data class FoodClassificationResult(
    val results: List<ClassificationResult>,
    val status: ClassificationStatus,
    val rawLabels: List<String> = emptyList()  // For debugging only
) {
    val isEmpty: Boolean get() = results.isEmpty()
    val bestResult: ClassificationResult? get() = results.firstOrNull()
    val isHighConfidence: Boolean get() = status == ClassificationStatus.HIGH_CONFIDENCE
    
    companion object {
        fun noFood(rawLabels: List<String> = emptyList()) = FoodClassificationResult(
            results = emptyList(),
            status = ClassificationStatus.NO_FOOD_DETECTED,
            rawLabels = rawLabels
        )
        
        fun error() = FoodClassificationResult(
            results = emptyList(),
            status = ClassificationStatus.ERROR
        )
    }
}

/**
 * Classification outcome status.
 */
enum class ClassificationStatus {
    HIGH_CONFIDENCE,      // Single high-confidence food match (safe for auto-select)
    SINGLE_MATCH,         // One food found but confidence not high enough
    MULTIPLE_CANDIDATES,  // Multiple possible foods detected
    NO_FOOD_DETECTED,     // No food-related labels found (show manual search)
    ERROR                 // Classification failed
}

/**
 * Single classification result.
 */
data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val index: Int = 0
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
}
