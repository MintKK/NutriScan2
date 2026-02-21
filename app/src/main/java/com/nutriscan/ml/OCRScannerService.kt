package com.nutriscan.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Parsed nutrition facts from a label photo.
 */
data class NutritionLabelResult(
    val calories: Int? = null,
    val protein: Float? = null,
    val carbs: Float? = null,
    val fat: Float? = null,
    val servingSize: String? = null,
    val rawText: String = ""
) {
    val hasAnyData: Boolean get() = calories != null || protein != null || carbs != null || fat != null
}

/**
 * Service that uses ML Kit Text Recognition to extract nutrition facts
 * from a photo of a food package label.
 *
 * Parsing strategy:
 * 1. Run OCR on the bitmap to get all recognized text lines.
 * 2. Iterate through lines looking for nutrition keywords.
 * 3. Extract numeric values from the same or adjacent lines.
 */
@Singleton
class OCRScannerService @Inject constructor() {

    companion object {
        private const val TAG = "OCRScannerService"

        // Regex patterns for extracting numeric values
        private val NUMBER_PATTERN = Regex("""(\d+\.?\d*)""")

        // Keyword patterns (case-insensitive)
        private val CALORIE_KEYWORDS = listOf("calories", "energy", "kcal", "cal")
        private val PROTEIN_KEYWORDS = listOf("protein")
        private val CARB_KEYWORDS = listOf("carbohydrate", "carbs", "total carb")
        private val FAT_KEYWORDS = listOf("total fat", "fat")
        private val SERVING_KEYWORDS = listOf("serving size", "per serving", "portion")
    }

    /**
     * Process a bitmap image and extract nutrition label data.
     */
    suspend fun extractNutrition(bitmap: Bitmap): NutritionLabelResult {
        val text = recognizeText(bitmap)

        if (text.isBlank()) {
            Log.w(TAG, "No text recognized in image")
            return NutritionLabelResult(rawText = "")
        }

        Log.d(TAG, "Recognized text:\n$text")

        return parseNutritionText(text)
    }

    /**
     * Run ML Kit text recognition and return the full text.
     */
    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                cont.resume("")
            }
    }

    /**
     * Parse recognized text to extract nutrition values.
     * Handles various label formats:
     * - "Calories 230"
     * - "Total Fat 8g"
     * - "Protein: 5g"
     */
    private fun parseNutritionText(fullText: String): NutritionLabelResult {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var calories: Int? = null
        var protein: Float? = null
        var carbs: Float? = null
        var fat: Float? = null
        var servingSize: String? = null

        for (i in lines.indices) {
            val line = lines[i].lowercase()
            val nextLine = lines.getOrNull(i + 1)?.lowercase()

            // Calories
            if (calories == null && CALORIE_KEYWORDS.any { line.contains(it) }) {
                calories = extractNumber(line)?.toInt()
                    ?: nextLine?.let { extractNumber(it)?.toInt() }
            }

            // Protein
            if (protein == null && PROTEIN_KEYWORDS.any { line.contains(it) }) {
                protein = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Carbs
            if (carbs == null && CARB_KEYWORDS.any { line.contains(it) }) {
                carbs = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Fat (check before protein to avoid "total fat" matching "fat" in protein line)
            if (fat == null && FAT_KEYWORDS.any { line.contains(it) }) {
                fat = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Serving size
            if (servingSize == null && SERVING_KEYWORDS.any { line.contains(it) }) {
                val fullLine = lines[i] // Use original case
                servingSize = fullLine
                    .replace(Regex("(?i)(serving size|per serving|portion)\\s*:?\\s*"), "")
                    .trim()
                    .ifBlank { null }
            }
        }

        val result = NutritionLabelResult(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            servingSize = servingSize,
            rawText = fullText
        )

        Log.d(TAG, "Parsed result: kcal=$calories, protein=$protein, carbs=$carbs, fat=$fat, serving=$servingSize")
        return result
    }

    /**
     * Extract the first numeric value from a text line.
     * Handles: "230", "8g", "5.2 g", "12.0mg", etc.
     */
    private fun extractNumber(text: String): Float? {
        return NUMBER_PATTERN.find(text)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
