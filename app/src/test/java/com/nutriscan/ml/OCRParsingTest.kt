package com.nutriscan.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the nutrition label parsing algorithm used in OCRScannerService.
 *
 * Since OCRScannerService.parseNutritionText is private, we replicate the exact
 * parsing logic here to validate keyword matching, number extraction, multi-line
 * fallback, and edge cases.
 *
 * Algorithm (from OCRScannerService.kt:93-151):
 * - Scans lines for nutrition keywords (calories, protein, carbs, fat, serving)
 * - Extracts the first numeric value from the matching line
 * - Falls back to the next line if no number found on the keyword line
 * - First match wins (prevents re-assignment)
 */
class OCRParsingTest {

    // ============ Replicated constants from OCRScannerService ============

    private val NUMBER_PATTERN = Regex("""(\d+\.?\d*)""")
    private val CALORIE_KEYWORDS = listOf("calories", "energy", "kcal", "cal")
    private val PROTEIN_KEYWORDS = listOf("protein")
    private val CARB_KEYWORDS = listOf("carbohydrate", "carbs", "total carb")
    private val FAT_KEYWORDS = listOf("total fat", "fat")
    private val SERVING_KEYWORDS = listOf("serving size", "per serving", "portion")

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
     * Exact replica of OCRScannerService.parseNutritionText().
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

            if (calories == null && CALORIE_KEYWORDS.any { line.contains(it) }) {
                calories = extractNumber(line)?.toInt()
                    ?: nextLine?.let { extractNumber(it)?.toInt() }
            }

            if (protein == null && PROTEIN_KEYWORDS.any { line.contains(it) }) {
                protein = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            if (carbs == null && CARB_KEYWORDS.any { line.contains(it) }) {
                carbs = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            if (fat == null && FAT_KEYWORDS.any { line.contains(it) }) {
                fat = extractNumber(line)
                    ?: nextLine?.let { extractNumber(it) }
            }

            if (servingSize == null && SERVING_KEYWORDS.any { line.contains(it) }) {
                val fullLine = lines[i]
                servingSize = fullLine
                    .replace(Regex("(?i)(serving size|per serving|portion)\\s*:?\\s*"), "")
                    .trim()
                    .ifBlank { null }
            }
        }

        return NutritionLabelResult(
            calories = calories, protein = protein, carbs = carbs,
            fat = fat, servingSize = servingSize, rawText = fullText
        )
    }

    private fun extractNumber(text: String): Float? {
        return NUMBER_PATTERN.find(text)?.groupValues?.get(1)?.toFloatOrNull()
    }

    // ==================== Standard Label Parsing ====================

    @Test
    fun `parses standard US nutrition label`() {
        val text = """
            Nutrition Facts
            Serving Size 1 cup (228g)
            Calories 230
            Total Fat 8g
            Total Carbohydrate 37g
            Protein 5g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(230, result.calories)
        assertEquals(8f, result.fat)
        assertEquals(37f, result.carbs)
        assertEquals(5f, result.protein)
    }

    @Test
    fun `parses label with colon-separated values`() {
        val text = """
            Calories: 150
            Protein: 12g
            Carbs: 20g
            Fat: 5g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(150, result.calories)
        assertEquals(12f, result.protein)
        assertEquals(20f, result.carbs)
        assertEquals(5f, result.fat)
    }

    @Test
    fun `parses label with decimal values`() {
        val text = """
            Calories 120
            Protein 3.5g
            Total Carbohydrate 15.2g
            Total Fat 4.8g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(120, result.calories)
        assertEquals(3.5f, result.protein)
        assertEquals(15.2f, result.carbs)
        assertEquals(4.8f, result.fat)
    }

    // ==================== Keyword Variations ====================

    @Test
    fun `recognizes energy keyword for calories`() {
        val text = "Energy 250 kcal"
        val result = parseNutritionText(text)
        assertEquals(250, result.calories)
    }

    @Test
    fun `recognizes kcal keyword for calories`() {
        val text = "Per serving: 180 kcal"
        val result = parseNutritionText(text)
        assertEquals(180, result.calories)
    }

    @Test
    fun `recognizes total carb keyword`() {
        val text = "Total Carb 45g"
        val result = parseNutritionText(text)
        assertEquals(45f, result.carbs)
    }

    @Test
    fun `recognizes carbohydrate keyword`() {
        val text = "Carbohydrate 30g"
        val result = parseNutritionText(text)
        assertEquals(30f, result.carbs)
    }

    // ==================== Multi-line Fallback ====================

    @Test
    fun `falls back to next line when number not on keyword line`() {
        val text = """
            Calories
            230
            Protein
            12g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(230, result.calories)
        assertEquals(12f, result.protein)
    }

    @Test
    fun `prefers same-line number over next-line`() {
        val text = """
            Calories 200
            300
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(200, result.calories)
    }

    // ==================== Serving Size ====================

    @Test
    fun `extracts serving size text`() {
        val text = "Serving Size 1 cup (228g)"
        val result = parseNutritionText(text)
        assertNotNull(result.servingSize)
        assertTrue(result.servingSize!!.contains("1"))
    }

    @Test
    fun `extracts per serving text`() {
        val text = "Per Serving: 100ml"
        val result = parseNutritionText(text)
        assertNotNull(result.servingSize)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `empty text returns all nulls`() {
        val result = parseNutritionText("")
        assertNull(result.calories)
        assertNull(result.protein)
        assertNull(result.carbs)
        assertNull(result.fat)
        assertFalse(result.hasAnyData)
    }

    @Test
    fun `text without nutrition keywords returns all nulls`() {
        val text = "This is just a random label with no nutrition info"
        val result = parseNutritionText(text)
        assertFalse(result.hasAnyData)
    }

    @Test
    fun `partial data is still valid`() {
        val text = "Calories 200"
        val result = parseNutritionText(text)
        assertEquals(200, result.calories)
        assertNull(result.protein)
        assertTrue(result.hasAnyData)
    }

    @Test
    fun `first match wins for duplicate keywords`() {
        val text = """
            Calories 200
            Calories 500
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(200, result.calories)
    }

    @Test
    fun `case insensitive keyword matching`() {
        val text = """
            CALORIES 300
            PROTEIN 25g
            TOTAL FAT 10g
            CARBOHYDRATE 40g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(300, result.calories)
        assertEquals(25f, result.protein)
        assertEquals(10f, result.fat)
        assertEquals(40f, result.carbs)
    }

    @Test
    fun `handles blank lines gracefully`() {
        val text = """
            Calories 150

            Protein 8g

            Fat 6g
        """.trimIndent()

        val result = parseNutritionText(text)
        assertEquals(150, result.calories)
        assertEquals(8f, result.protein)
        assertEquals(6f, result.fat)
    }

    // ==================== extractNumber ====================

    @Test
    fun `extractNumber finds integer`() {
        assertEquals(230f, extractNumber("Calories 230"))
    }

    @Test
    fun `extractNumber finds decimal`() {
        assertEquals(3.5f, extractNumber("Protein 3.5g"))
    }

    @Test
    fun `extractNumber finds first number in line`() {
        assertEquals(8f, extractNumber("Total Fat 8g  14%"))
    }

    @Test
    fun `extractNumber returns null for no numbers`() {
        assertNull(extractNumber("Nutrition Facts"))
    }
}
