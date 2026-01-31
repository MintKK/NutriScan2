package com.nutriscan.ml

import android.content.Context
import android.graphics.Bitmap
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
 * Wrapper for ML Kit Image Labeling.
 * Provides suspend functions for coroutine integration.
 */
@Singleton
class FoodClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val labeler: ImageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
        ImageLabeling.getClient(options)
    }
    
    /**
     * Classify a bitmap image and return top labels.
     * @param bitmap The captured image.
     * @param maxResults Maximum number of results to return.
     * @return List of ClassificationResult sorted by confidence.
     */
    suspend fun classify(bitmap: Bitmap, maxResults: Int = 5): List<ClassificationResult> {
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
    
    /**
     * Get the best food-related label from classification results.
     * Filters out non-food labels like "product", "material", etc.
     */
    fun getBestFoodLabel(results: List<ClassificationResult>): ClassificationResult? {
        val nonFoodLabels = setOf(
            "product", "material", "object", "device", "tool", 
            "building", "vehicle", "furniture", "text", "document"
        )
        
        return results.firstOrNull { result ->
            !nonFoodLabels.contains(result.label.lowercase())
        }
    }
}

/**
 * Result from ML classification.
 */
data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val index: Int
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
}
