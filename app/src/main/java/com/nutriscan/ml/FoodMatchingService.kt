package com.nutriscan.ml

import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.repository.FoodRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for matching ML classification results against the food database.
 * Handles normalization, indexing, and ranked matching.
 */
@Singleton
class FoodMatchingService @Inject constructor(
    private val foodRepository: FoodRepository
) {
    // Lazy-initialized index, built on first use
    private var aliasIndex: FoodAliasIndex? = null
    
    /**
     * Initialize the alias index from database.
     * Should be called once at app startup or before first classification.
     */
    suspend fun initialize() {
        if (aliasIndex == null) {
            val foods = foodRepository.getAllFoods().first()
            aliasIndex = FoodAliasIndex(foods)
        }
    }
    
    /**
     * Ensure index is ready, initializing if needed.
     */
    private suspend fun ensureIndexReady(): FoodAliasIndex {
        if (aliasIndex == null) {
            initialize()
        }
        return aliasIndex ?: throw IllegalStateException("Failed to initialize food index")
    }
    
    /**
     * Match ML classification results against the food database.
     * 
     * For Food-11 categories (e.g., "Meat", "Bread"), expands to specific
     * foods using Food11CategoryMapper before matching.
     * 
     * @param results Classification results (may be categories or specific foods)
     * @return Ranked list of FoodMatchResult, deduplicated by food
     */
    suspend fun matchClassifications(
        results: List<ClassificationResult>
    ): List<FoodMatchResult> {
        val index = ensureIndexReady()
        
        return results
            .flatMap { result -> 
                // Check if this is a Food-11 category
                val searchTerms = Food11CategoryMapper.getSearchTerms(result.label)
                
                if (searchTerms.size > 1) {
                    // It's a category → expand to specific foods
                    matchCategoryLabel(result.label, result.confidence, searchTerms, index)
                } else {
                    // It's a specific label → match directly
                    matchSingleLabel(result.label, result.confidence, index)
                }
            }
            .sortedByDescending { it.combinedScore }
            .distinctBy { it.matchedFood?.id ?: it.mlLabel }
            .take(10)  // More candidates for categories
    }
    
    /**
     * Match a Food-11 category by searching for expanded terms.
     */
    private fun matchCategoryLabel(
        category: String,
        confidence: Float,
        searchTerms: List<String>,
        index: FoodAliasIndex
    ): List<FoodMatchResult> {
        val matches = mutableListOf<FoodMatchResult>()
        
        for (term in searchTerms) {
            // Try exact name match
            index.findByExactName(term)?.let { food ->
                matches.add(FoodMatchResult(
                    mlLabel = category,  // Keep original category as ML label
                    normalizedLabel = term,  // But use specific term for display
                    confidence = confidence * 0.9f,  // Slight penalty for expansion
                    matchedFood = food,
                    matchType = MatchType.ALIAS  // Category → food is like alias
                ))
            }
            
            // Try alias match
            index.findByAlias(term)?.let { food ->
                if (matches.none { it.matchedFood?.id == food.id }) {
                    matches.add(FoodMatchResult(
                        mlLabel = category,
                        normalizedLabel = term,
                        confidence = confidence * 0.85f,
                        matchedFood = food,
                        matchType = MatchType.ALIAS
                    ))
                }
            }
        }
        
        return if (matches.isNotEmpty()) {
            matches.sortedByDescending { it.combinedScore }
        } else {
            listOf(createNoMatchResult(category, category.lowercase(), confidence))
        }
    }
    
    /**
     * Match a single ML label against the database.
     * Tries matching in order: exact → alias → token → partial
     */
    private fun matchSingleLabel(
        label: String,
        confidence: Float,
        index: FoodAliasIndex
    ): List<FoodMatchResult> {
        val normalized = LabelNormalizer.normalize(label)
        
        // Skip empty or very short labels
        if (normalized.length < 2) {
            return listOf(createNoMatchResult(label, normalized, confidence))
        }
        
        val tokens = LabelNormalizer.extractTokens(normalized)
        val matches = mutableListOf<FoodMatchResult>()
        
        // Try each token for matching
        for (token in tokens) {
            // 1. Exact name match (highest priority)
            index.findByExactName(token)?.let { food ->
                return listOf(FoodMatchResult(
                    mlLabel = label,
                    normalizedLabel = normalized,
                    confidence = confidence,
                    matchedFood = food,
                    matchType = if (token == normalized) MatchType.EXACT else MatchType.TOKEN
                ))
            }
            
            // 2. Alias match
            index.findByAlias(token)?.let { food ->
                matches.add(FoodMatchResult(
                    mlLabel = label,
                    normalizedLabel = normalized,
                    confidence = confidence,
                    matchedFood = food,
                    matchType = MatchType.ALIAS
                ))
            }
        }
        
        // Return alias matches if found
        if (matches.isNotEmpty()) {
            return matches
        }
        
        // 3. Fallback: partial match (never auto-selected)
        index.findByPartialName(normalized)?.let { food ->
            return listOf(FoodMatchResult(
                mlLabel = label,
                normalizedLabel = normalized,
                confidence = confidence,
                matchedFood = food,
                matchType = MatchType.PARTIAL
            ))
        }
        
        // 4. No match found
        return listOf(createNoMatchResult(label, normalized, confidence))
    }
    
    private fun createNoMatchResult(
        label: String,
        normalized: String,
        confidence: Float
    ) = FoodMatchResult(
        mlLabel = label,
        normalizedLabel = normalized,
        confidence = confidence,
        matchedFood = null,
        matchType = MatchType.NONE
    )
    
    /**
     * Get the best auto-selectable match from results.
     * Returns null if no match is safe for auto-selection.
     */
    fun getBestAutoSelectMatch(results: List<FoodMatchResult>): FoodMatchResult? {
        return results.firstOrNull { it.isSafeForAutoSelect }
    }
    
    /**
     * Get all valid candidates for user selection.
     */
    fun getValidCandidates(results: List<FoodMatchResult>): List<FoodMatchResult> {
        return results.filter { it.isValidCandidate }
    }
    
    /**
     * Check if index is initialized and ready.
     */
    fun isReady(): Boolean = aliasIndex != null && !aliasIndex!!.isEmpty()
}
