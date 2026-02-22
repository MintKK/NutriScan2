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

    private val ocrScannerService = OCRScannerService()

    private fun parseNutritionText(fullText: String): NutritionLabelResult {
        return ocrScannerService.parseNutritionText(fullText)
    }

    private fun extractNumber(text: String): Float? {
        return ocrScannerService.extractNumber(text)
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

    // ==================== OCR Edge Cases ====================

    @Test
    fun `number with comma is parsed correctly`() {
        // Thousands separator
        assertEquals(1200f, extractNumber("Calories 1,200"))
    }

    @Test
    fun `typos in calorie keywords are recognized`() {
        val text = "Calorles 250"
        val result = parseNutritionText(text)
        assertEquals(250, result.calories)
    }

    @Test
    fun `shortened carb keyword is recognized`() {
        val text = "Carb 15g"
        val result = parseNutritionText(text)
        assertEquals(15f, result.carbs)
    }

    @Test
    fun `word boundaries prevent incorrect prefix matching`() {
        // "nonfat" contains "fat" but shouldn't match "total fat" or "fat" as a nutrient line
        val text = "nonfat milk\nFat 5g"
        val result = parseNutritionText(text)
        assertEquals(5f, result.fat)
    }
}
