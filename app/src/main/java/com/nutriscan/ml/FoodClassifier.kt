package com.nutriscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit-based food classifier with vocabulary filtering.
 * 
 * ⚠️ TEMPORARY IMPLEMENTATION
 * 
 * This uses generic ML Kit Image Labeling which is NOT food-specific.
 * The FoodVocabulary filter is a safety layer, not a solution.
 * 
 * For production: Replace with TFLiteFoodClassifier using a food-trained model.
 * 
 * @see FoodClassificationService
 * @see TFLiteFoodClassifier (to be implemented)
 */
@Singleton
class MLKitFoodClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : FoodClassificationService {
    
    companion object {
        private const val TAG = "MLKitFoodClassifier"
        
        // Very low threshold to capture all labels, then filter
        private const val ML_KIT_THRESHOLD = 0.3f
        
        // Minimum food confidence to consider
        private const val FOOD_MIN_THRESHOLD = 0.5f
        
        // EXTREMELY conservative - only auto-select if very confident
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
    }
    
    override val classifierName = "ML Kit + FoodVocabulary Filter"
    
    // ML Kit is NOT food-trained
    override val isFoodTrained = false
    
    private val labeler: ImageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(ML_KIT_THRESHOLD)
            .build()
        ImageLabeling.getClient(options)
    }
    
    /**
     * Classify image and return ONLY food-related labels.
     * Non-food labels are filtered out. If none remain, returns NO_FOOD_DETECTED.
     */
    override suspend fun classifyFood(bitmap: Bitmap): FoodClassificationResult {
        return try {
            val rawResults = classifyRaw(bitmap, maxResults = 10)
            
            Log.d(TAG, "Raw ML labels: ${rawResults.map { "${it.label}(${it.confidencePercent}%)" }}")
            
            // Filter to food-only using vocabulary (temporary safety layer)
            val foodResults = FoodVocabulary.filterFoodOnly(rawResults)
                .filter { it.confidence >= FOOD_MIN_THRESHOLD }
                .take(5)
            
            Log.d(TAG, "Food-filtered: ${foodResults.map { "${it.label}(${it.confidencePercent}%)" }}")
            
            when {
                foodResults.isEmpty() -> {
                    // No food labels found - critical, trigger manual search
                    FoodClassificationResult.noFood(rawResults.map { it.label })
                }
                foodResults.first().confidence >= HIGH_CONFIDENCE_THRESHOLD -> {
                    // Very high confidence - can consider auto-select
                    FoodClassificationResult(
                        results = foodResults,
                        status = ClassificationStatus.HIGH_CONFIDENCE,
                        rawLabels = rawResults.map { it.label }
                    )
                }
                foodResults.size == 1 -> {
                    // Single food but not high confidence - show for confirmation
                    FoodClassificationResult(
                        results = foodResults,
                        status = ClassificationStatus.SINGLE_MATCH,
                        rawLabels = rawResults.map { it.label }
                    )
                }
                else -> {
                    // Multiple foods - show candidates
                    FoodClassificationResult(
                        results = foodResults,
                        status = ClassificationStatus.MULTIPLE_CANDIDATES,
                        rawLabels = rawResults.map { it.label }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            FoodClassificationResult.error()
        }
    }
    
    /**
     * Raw ML Kit classification without food filtering.
     */
    private suspend fun classifyRaw(bitmap: Bitmap, maxResults: Int): List<ClassificationResult> {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    val results = labels
                        .sortedByDescending { it.confidence }
                        .take(maxResults)
                        .map { label ->
                            ClassificationResult(
                                label = label.text,
                                confidence = label.confidence,
                                index = label.index
                            )
                        }
                    continuation.resume(results)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }
}

/**
 * Placeholder for future food-trained TFLite classifier.
 * 
 * To implement:
 * 1. Download food-trained .tflite model (Food-101, MobileNet-Food, etc.)
 * 2. Place in assets/ml/food_model.tflite
 * 3. Implement this class using TensorFlow Lite Interpreter
 * 4. Swap binding in Hilt module
 * 
 * @see FoodClassificationService
 */
// TODO: Implement when food-trained model is available
// class TFLiteFoodClassifier @Inject constructor(
//     @ApplicationContext private val context: Context
// ) : FoodClassificationService {
//     
//     override val classifierName = "TFLite Food-101"
//     override val isFoodTrained = true  // This is the key difference!
//     
//     override suspend fun classifyFood(bitmap: Bitmap): FoodClassificationResult {
//         // Load model from assets
//         // Run inference
//         // Labels are already food-specific - no filtering needed
//     }
// }
