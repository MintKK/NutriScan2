package com.nutriscan.ml

/**
 * Mapping from Food-11 categories to database search terms.
 * 
 * Food-11 returns coarse categories. This mapper expands categories
 * to specific food terms for database matching.
 * 
 * Example: "Meat" category → search for beef, chicken, pork, etc.
 */
object Food11CategoryMapper {
    
    /**
     * Map Food-11 category to list of database search terms.
     * These terms are used for fuzzy matching against food names/aliases.
     */
    private val CATEGORY_TO_FOODS: Map<String, List<String>> = mapOf(
        "Bread" to listOf(
            "bread", "toast", "bagel", "croissant", "baguette", 
            "roll", "bun", "muffin", "pretzel"
        ),
        "Dairy product" to listOf(
            "milk", "cheese", "yogurt", "butter", "cream",
            "ice cream", "cottage cheese", "mozzarella"
        ),
        "Dessert" to listOf(
            "cake", "pie", "cookie", "brownie", "donut", "doughnut",
            "pastry", "cupcake", "cheesecake", "pudding", "chocolate"
        ),
        "Egg" to listOf(
            "egg", "eggs", "omelette", "omelet", "scrambled eggs",
            "fried egg", "boiled egg", "poached egg"
        ),
        "Fried food" to listOf(
            "french fries", "fries", "fried chicken", "chicken nuggets",
            "onion rings", "tempura", "fried", "crispy"
        ),
        "Meat" to listOf(
            "beef", "steak", "pork", "lamb", "chicken", "turkey",
            "sausage", "bacon", "ham", "burger", "hamburger", "meatball"
        ),
        "Noodles-Pasta" to listOf(
            "pasta", "spaghetti", "noodles", "ramen", "udon", "pho",
            "lasagna", "macaroni", "fettuccine", "penne"
        ),
        "Rice" to listOf(
            "rice", "fried rice", "risotto", "sushi", "paella",
            "biryani", "pilaf"
        ),
        "Seafood" to listOf(
            "fish", "salmon", "tuna", "shrimp", "prawn", "lobster",
            "crab", "oyster", "squid", "clam", "mussel"
        ),
        "Soup" to listOf(
            "soup", "broth", "stew", "chowder", "bisque",
            "pho", "ramen", "miso"
        ),
        "Vegetable-Fruit" to listOf(
            "apple", "banana", "orange", "grape", "strawberry",
            "carrot", "broccoli", "tomato", "salad", "vegetable", "fruit"
        )
    )
    
    /**
     * Get search terms for a Food-11 category.
     * Returns empty list if category is unknown.
     */
    fun getSearchTerms(category: String): List<String> {
        // Try exact match first
        CATEGORY_TO_FOODS[category]?.let { return it }
        
        // Try normalized match
        val normalized = category.trim()
        for ((key, terms) in CATEGORY_TO_FOODS) {
            if (key.equals(normalized, ignoreCase = true)) {
                return terms
            }
            // Handle variations like "Noodles/Pasta" vs "Noodles-Pasta"
            if (key.replace("-", "/").equals(normalized.replace("-", "/"), ignoreCase = true)) {
                return terms
            }
        }
        
        // Fallback: return category itself as search term
        return listOf(category.lowercase())
    }
    
    /**
     * Check if a category is known.
     */
    fun isKnownCategory(category: String): Boolean {
        return getSearchTerms(category).size > 1
    }
    
    /**
     * Get all known categories.
     */
    fun getAllCategories(): List<String> = CATEGORY_TO_FOODS.keys.toList()
}
