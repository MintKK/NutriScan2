package com.nutriscan.ml

import com.nutriscan.data.local.entity.FoodItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FoodMatchResult.
 * Verifies scoring logic, auto-selection safety, and candidate validity.
 */
class FoodMatchResultTest {

    private val sampleFood = FoodItem(
        id = 1, name = "banana", kcalPer100g = 89,
        proteinPer100g = 1.1f, carbsPer100g = 22.8f, fatPer100g = 0.3f
    )

    // ==================== combinedScore ====================

    @Test
    fun `exact match with high confidence produces top score`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.95f, matchedFood = sampleFood, matchType = MatchType.EXACT
        )
        // 0.95 * (100/100) = 0.95
        assertEquals(0.95f, result.combinedScore, 0.01f)
    }

    @Test
    fun `partial match penalizes combined score`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.95f, matchedFood = sampleFood, matchType = MatchType.PARTIAL
        )
        // 0.95 * (30/100) = 0.285
        assertEquals(0.285f, result.combinedScore, 0.01f)
    }

    @Test
    fun `alias match with moderate confidence`() {
        val result = FoodMatchResult(
            mlLabel = "plantain", normalizedLabel = "plantain",
            confidence = 0.60f, matchedFood = sampleFood, matchType = MatchType.ALIAS
        )
        // 0.60 * (80/100) = 0.48
        assertEquals(0.48f, result.combinedScore, 0.01f)
    }

    @Test
    fun `token match scoring`() {
        val result = FoodMatchResult(
            mlLabel = "granny smith apple", normalizedLabel = "granny smith apple",
            confidence = 0.80f, matchedFood = sampleFood, matchType = MatchType.TOKEN
        )
        // 0.80 * (60/100) = 0.48
        assertEquals(0.48f, result.combinedScore, 0.01f)
    }

    @Test
    fun `no match produces zero score`() {
        val result = FoodMatchResult(
            mlLabel = "unknown", normalizedLabel = "unknown",
            confidence = 0.50f, matchedFood = null, matchType = MatchType.NONE
        )
        assertEquals(0.0f, result.combinedScore, 0.01f)
    }

    @Test
    fun `exact match outranks high-confidence partial match`() {
        val exactLow = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.60f, matchedFood = sampleFood, matchType = MatchType.EXACT
        )
        val partialHigh = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.95f, matchedFood = sampleFood, matchType = MatchType.PARTIAL
        )
        // exact: 0.60 * 1.0 = 0.60, partial: 0.95 * 0.30 = 0.285
        assertTrue(
            "Exact match (${exactLow.combinedScore}) should outrank partial match (${partialHigh.combinedScore})",
            exactLow.combinedScore > partialHigh.combinedScore
        )
    }

    // ==================== isSafeForAutoSelect ====================

    @Test
    fun `high confidence exact match is safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.85f, matchedFood = sampleFood, matchType = MatchType.EXACT
        )
        assertTrue(result.isSafeForAutoSelect)
    }

    @Test
    fun `partial match is never safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.95f, matchedFood = sampleFood, matchType = MatchType.PARTIAL
        )
        assertFalse(result.isSafeForAutoSelect)
    }

    @Test
    fun `low confidence exact match is not safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.50f, matchedFood = sampleFood, matchType = MatchType.EXACT
        )
        assertFalse(result.isSafeForAutoSelect)
    }

    @Test
    fun `null food is not safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "unknown", normalizedLabel = "unknown",
            confidence = 0.95f, matchedFood = null, matchType = MatchType.EXACT
        )
        assertFalse(result.isSafeForAutoSelect)
    }

    @Test
    fun `alias match with high confidence is safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "plantain", normalizedLabel = "plantain",
            confidence = 0.80f, matchedFood = sampleFood, matchType = MatchType.ALIAS
        )
        assertTrue(result.isSafeForAutoSelect)
    }

    @Test
    fun `token match with high confidence is safe for auto-select`() {
        val result = FoodMatchResult(
            mlLabel = "granny smith apple", normalizedLabel = "granny smith apple",
            confidence = 0.75f, matchedFood = sampleFood, matchType = MatchType.TOKEN
        )
        assertTrue(result.isSafeForAutoSelect)
    }

    // ==================== isValidCandidate ====================

    @Test
    fun `result above low threshold is valid candidate`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.20f, matchedFood = sampleFood, matchType = MatchType.PARTIAL
        )
        assertTrue(result.isValidCandidate)
    }

    @Test
    fun `result below low threshold is not valid candidate`() {
        val result = FoodMatchResult(
            mlLabel = "banana", normalizedLabel = "banana",
            confidence = 0.10f, matchedFood = sampleFood, matchType = MatchType.PARTIAL
        )
        assertFalse(result.isValidCandidate)
    }

    @Test
    fun `result with null food is not valid candidate`() {
        val result = FoodMatchResult(
            mlLabel = "unknown", normalizedLabel = "unknown",
            confidence = 0.90f, matchedFood = null, matchType = MatchType.NONE
        )
        assertFalse(result.isValidCandidate)
    }
}
