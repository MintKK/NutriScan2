package com.nutriscan.ml

import com.nutriscan.data.local.entity.FoodItem

/**
 * Pre-processed in-memory index for O(1) food lookups.
 * Built once when foods are loaded from database.
 * 
 * Why: Database LIKE queries are slow and can't handle complex alias matching.
 * This index enables instant exact and alias lookups.
 */
class FoodAliasIndex(foods: List<FoodItem>) {
    
    // Map: normalized name → FoodItem
    private val nameMap: Map<String, FoodItem>
    
    // Map: normalized alias → FoodItem
    private val aliasMap: Map<String, FoodItem>
    
    // All foods for iteration when needed
    val allFoods: List<FoodItem> = foods
    
    init {
        val names = mutableMapOf<String, FoodItem>()
        val aliases = mutableMapOf<String, FoodItem>()
        
        foods.forEach { food ->
            // Index by normalized primary name
            val normalizedName = LabelNormalizer.normalize(food.name)
            if (normalizedName.isNotBlank()) {
                names[normalizedName] = food
            }
            
            // Index by each normalized alias
            food.aliases?.split(",")?.forEach { alias ->
                val normalizedAlias = LabelNormalizer.normalize(alias.trim())
                if (normalizedAlias.isNotBlank() && normalizedAlias != normalizedName) {
                    aliases[normalizedAlias] = food
                }
            }
        }
        
        nameMap = names
        aliasMap = aliases
    }
    
    /**
     * Find food by exact normalized name match.
     * "banana" → FoodItem(name="banana")
     */
    fun findByExactName(normalizedQuery: String): FoodItem? {
        return nameMap[normalizedQuery]
    }
    
    /**
     * Find food by alias match.
     * "plantain" → FoodItem(name="banana", aliases="bananas,plantain")
     */
    fun findByAlias(normalizedQuery: String): FoodItem? {
        return aliasMap[normalizedQuery]
    }
    
    /**
     * Find food by partial name match (contains).
     * Returns first match - use with caution, requires user confirmation.
     * Minimum query length enforced to prevent "ham" → "hamburger".
     */
    fun findByPartialName(normalizedQuery: String, minLength: Int = 4): FoodItem? {
        if (normalizedQuery.length < minLength) return null
        
        // Check names first
        nameMap.entries.firstOrNull { (name, _) ->
            name.contains(normalizedQuery) || normalizedQuery.contains(name)
        }?.let { return it.value }
        
        // Then check aliases
        aliasMap.entries.firstOrNull { (alias, _) ->
            alias.contains(normalizedQuery) || normalizedQuery.contains(alias)
        }?.let { return it.value }
        
        return null
    }
    
    /**
     * Check if index is empty (no foods loaded).
     */
    fun isEmpty(): Boolean = nameMap.isEmpty()
    
    /**
     * Get count of indexed foods.
     */
    fun size(): Int = nameMap.size
}
