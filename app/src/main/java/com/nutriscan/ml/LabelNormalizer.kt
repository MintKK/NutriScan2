package com.nutriscan.ml

/**
 * Normalizes ML Kit output labels for database matching.
 * Handles plurals, stopwords, separators, and case normalization.
 */
object LabelNormalizer {
    
    // Generic labels that don't help identify specific foods
    private val STOPWORDS = setOf(
        "food", "dish", "meal", "cuisine", "snack", "produce",
        "fresh", "cooked", "raw", "fried", "baked", "grilled",
        "organic", "natural", "healthy", "delicious"
    )
    
    // Plural → singular mappings (checked in order)
    private val PLURAL_RULES = listOf(
        "ies" to "y",   // berries → berry
        "ves" to "f",   // leaves → leaf
        "oes" to "o",   // tomatoes → tomato
        "es" to "",     // dishes → dish
        "s" to ""       // apples → apple
    )
    
    /**
     * Normalize a single ML label for database matching.
     * "Granny Smith Apples" → "granny smith apple"
     */
    fun normalize(label: String): String {
        return label
            .lowercase()
            .replace(Regex("[_\\-]"), " ")      // Convert separators to spaces
            .replace(Regex("[^a-z\\s]"), "")    // Remove non-alpha characters
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOPWORDS }
            .map { singularize(it) }            // Singularize each token
            .joinToString(" ")
            .trim()
    }
    
    /**
     * Extract search tokens from a normalized label.
     * Prioritizes full phrase, then last word (usually the food type).
     * "granny smith apple" → ["granny smith apple", "apple", "granny smith"]
     */
    fun extractTokens(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()
        
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val tokens = mutableListOf<String>()
        
        // Full phrase first
        tokens.add(normalized)
        
        // Last word is usually the food type (e.g., "apple" in "granny smith apple")
        if (words.size > 1) {
            tokens.add(words.last())
            // Prefix without last word (e.g., "granny smith")
            tokens.add(words.dropLast(1).joinToString(" "))
        }
        
        return tokens.distinct()
    }
    
    /**
     * Convert plural word to singular.
     * Only applies to words with minimum length to avoid false positives.
     */
    private fun singularize(word: String): String {
        // Too short to reliably singularize
        if (word.length < 4) return word

        // Special case: words like "apples", "noodles", "pickles" where removing
        // "es" incorrectly yields "appl", "noodl", "pickl". If the word ends in
        // a consonant + "les", just drop the trailing "s".
        if (word.endsWith("les") && word.length >= 5) {
            val charBeforeLes = word[word.length - 4]
            if (charBeforeLes !in "aeiou") {
                return word.dropLast(1) // "apples" → "apple"
            }
        }
        
        for ((suffix, replacement) in PLURAL_RULES) {
            if (word.endsWith(suffix)) {
                val stem = word.dropLast(suffix.length)
                // Ensure we have a reasonable stem left
                if (stem.length >= 2) {
                    return stem + replacement
                }
            }
        }
        return word
    }
}
