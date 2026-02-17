package com.nutriscan.ml

import com.nutriscan.data.local.entity.FoodItem

/**
 * Match quality ranking. Higher score = better match.
 * Used to combine with ML confidence for final ranking.
 */
enum class MatchType(val score: Int) {
    EXACT(100),      // "banana" → name="banana"
    ALIAS(80),       // "plantain" → aliases contains "plantain"  
    TOKEN(60),       // Matched via extracted token (e.g., "apple" from "granny smith apple")
    PARTIAL(30),     // Contains match - risky, never auto-select
    NONE(0)          // No database match found
}

/**
 * Result of matching an ML classification against the food database.
 */
data class FoodMatchResult(
    val mlLabel: String,           // Original ML Kit label
    val normalizedLabel: String,   // After normalization
    val confidence: Float,         // ML confidence (0-1)
    val matchedFood: FoodItem?,    // Database match, null if none
    val matchType: MatchType
) {
    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.70f
        const val LOW_CONFIDENCE_THRESHOLD = 0.15f  // Show more candidates since user always confirms
    }
    
    /**
     * Combined score: ML confidence × match quality.
     * Prevents high-confidence partial matches from winning over
     * lower-confidence exact matches.
     */
    val combinedScore: Float 
        get() = confidence * (matchType.score / 100f)
    
    /**
     * Is this match safe for auto-selection?
     * Rules:
     * - Must be EXACT or ALIAS match (not PARTIAL)
     * - Must have high confidence
     * - Must have a matched food
     */
    val isSafeForAutoSelect: Boolean 
        get() = matchedFood != null &&
                matchType in setOf(MatchType.EXACT, MatchType.ALIAS, MatchType.TOKEN) &&
                confidence >= HIGH_CONFIDENCE_THRESHOLD
    
    /**
     * Should this result be shown as a candidate?
     * Even low-confidence matches can be shown for user selection.
     */
    val isValidCandidate: Boolean
        get() = matchedFood != null && confidence >= LOW_CONFIDENCE_THRESHOLD
}
