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
        private val NUMBER_PATTERN = Regex("""(\d+(?:,\d+)*(?:\.\d+)?)""")

        // Keyword patterns (case-insensitive with word boundaries)
        private val CALORIE_REGEX = Regex("(?i)\\b(calories|calorles|energy|kcal|cal)\\b")
        private val PROTEIN_REGEX = Regex("(?i)\\b(protein)\\b")
        private val CARB_REGEX = Regex("(?i)\\b(carbohydrate|carbs|total carb|carb)\\b")
        private val FAT_REGEX = Regex("(?i)\\b(total fat|fat)\\b")
        private val SERVING_REGEX = Regex("(?i)\\b(serving size|per serving|portion)\\b")
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

    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val allLines = visionText.textBlocks.flatMap { it.lines }
                val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                
                for (line in allLines.sortedBy { it.boundingBox?.top ?: 0 }) {
                    val box = line.boundingBox ?: continue
                    
                    var added = false
                    for (row in rows) {
                        val rowBox = row.first().boundingBox ?: continue
                        val overlapTop = maxOf(box.top, rowBox.top)
                        val overlapBottom = minOf(box.bottom, rowBox.bottom)
                        val overlapHeight = overlapBottom - overlapTop
                        val minHeight = minOf(box.height(), rowBox.height())
                        
                        // If lines overlap vertically by at least 40%, they belong to the same visual row
                        if (minHeight > 0 && overlapHeight > minHeight * 0.4f) {
                            row.add(line)
                            added = true
                            break
                        }
                    }
                    if (!added) {
                        rows.add(mutableListOf(line))
                    }
                }
                
                val reconstructedText = rows.map { row ->
                    row.sortedBy { it.boundingBox?.left ?: 0 }
                       .joinToString("   ") { it.text }
                }.joinToString("\n")
                
                cont.resume(reconstructedText)
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
    @androidx.annotation.VisibleForTesting
    internal fun parseNutritionText(fullText: String): NutritionLabelResult {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var calories: Int? = null
        var protein: Float? = null
        var carbs: Float? = null
        var fat: Float? = null
        var servingSize: String? = null

        for (i in lines.indices) {
            val line = lines[i]
            val nextLine = lines.getOrNull(i + 1)

            // Calories
            if (calories == null && CALORIE_REGEX.containsMatchIn(line)) {
                calories = extractNumber(line)?.toInt()
                    ?: nextLine?.let { extractNumber(it)?.toInt() }
            }

            // Protein
            if (protein == null && PROTEIN_REGEX.containsMatchIn(line)) {
                protein = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Carbs
            if (carbs == null && CARB_REGEX.containsMatchIn(line)) {
                carbs = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Fat (check before protein to avoid "total fat" matching "fat" in protein line)
            if (fat == null && FAT_REGEX.containsMatchIn(line)) {
                fat = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            // Serving size
            if (servingSize == null && SERVING_REGEX.containsMatchIn(line)) {
                servingSize = line
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
    @androidx.annotation.VisibleForTesting
    internal fun extractNumber(text: String): Float? {
        val numberMatch = NUMBER_PATTERN.find(text)
        return numberMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toFloatOrNull()
    }
}
