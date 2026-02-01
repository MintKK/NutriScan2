package com.nutriscan.ml

/**
 * Food vocabulary and ontology for filtering ML classification results.
 * 
 * CRITICAL: Generic ML Kit Image Labeling returns ImageNet-style labels
 * which include non-food objects (shoe, furniture, etc). This filter
 * ensures only food-related labels pass through to the matching pipeline.
 */
object FoodVocabulary {
    
    /**
     * Comprehensive list of food-related terms that ML Kit might return.
     * Labels not in this set (or matching patterns) are rejected.
     */
    private val FOOD_WHITELIST = setOf(
        // Fruits
        "fruit", "apple", "banana", "orange", "lemon", "lime", "grape", "grapes",
        "strawberry", "strawberries", "blueberry", "blueberries", "raspberry",
        "blackberry", "cherry", "cherries", "peach", "pear", "plum", "mango",
        "pineapple", "watermelon", "melon", "cantaloupe", "kiwi", "coconut",
        "pomegranate", "fig", "date", "apricot", "nectarine", "papaya", "guava",
        "passion fruit", "dragon fruit", "lychee", "avocado", "tomato",
        
        // Vegetables
        "vegetable", "vegetables", "carrot", "broccoli", "cauliflower", "cabbage",
        "lettuce", "spinach", "kale", "cucumber", "zucchini", "squash", "pumpkin",
        "potato", "sweet potato", "onion", "garlic", "pepper", "bell pepper",
        "chili", "jalapeno", "celery", "asparagus", "artichoke", "eggplant",
        "mushroom", "corn", "peas", "beans", "green beans", "beet", "radish",
        "turnip", "leek", "scallion", "ginger", "brussels sprouts",
        
        // Proteins
        "meat", "beef", "steak", "pork", "lamb", "chicken", "turkey", "duck",
        "fish", "salmon", "tuna", "shrimp", "prawn", "lobster", "crab", "oyster",
        "clam", "mussel", "scallop", "squid", "octopus", "sausage", "bacon",
        "ham", "hot dog", "burger", "hamburger", "cheeseburger", "meatball",
        "egg", "eggs", "tofu", "tempeh",
        
        // Grains & Bakery
        "bread", "toast", "bagel", "croissant", "muffin", "biscuit", "roll",
        "baguette", "pretzel", "rice", "pasta", "noodles", "spaghetti", "macaroni",
        "ramen", "udon", "pho", "cereal", "oatmeal", "granola", "pancake",
        "waffle", "tortilla", "wrap", "pita", "naan", "flatbread",
        
        // Dairy
        "dairy", "milk", "cheese", "yogurt", "butter", "cream", "ice cream",
        "cottage cheese", "mozzarella", "cheddar", "parmesan", "brie",
        
        // Prepared Foods & Dishes
        "food", "dish", "meal", "cuisine", "pizza", "sushi", "tacos", "taco",
        "burrito", "quesadilla", "enchilada", "nachos", "salad", "soup", "stew",
        "curry", "stir fry", "fried rice", "sandwich", "sub", "wrap",
        "lasagna", "risotto", "paella", "casserole", "pot pie", "dumpling",
        "spring roll", "egg roll", "dim sum", "tempura", "teriyaki",
        "kebab", "falafel", "hummus", "guacamole", "salsa",
        
        // Fast Food
        "fast food", "french fries", "fries", "onion rings", "chicken nuggets",
        "chicken wings", "fried chicken", "fish and chips", "corn dog",
        
        // Desserts & Sweets
        "dessert", "cake", "pie", "cookie", "cookies", "brownie", "donut",
        "doughnut", "pastry", "tart", "cupcake", "cheesecake", "pudding",
        "mousse", "tiramisu", "eclair", "macaron", "candy", "chocolate",
        "candy bar", "lollipop", "gummy", "jelly", "jam", "honey",
        
        // Beverages
        "beverage", "drink", "juice", "smoothie", "coffee", "espresso",
        "latte", "cappuccino", "tea", "soda", "cola", "lemonade", "milkshake",
        "cocktail", "wine", "beer", "water",
        
        // Snacks
        "snack", "chips", "popcorn", "crackers", "pretzel", "nuts", "peanuts",
        "almonds", "cashews", "walnuts", "trail mix", "granola bar",
        
        // Generic food terms ML Kit might use
        "produce", "baked goods", "confectionery", "seafood", "poultry",
        "breakfast", "lunch", "dinner", "appetizer", "entree", "side dish"
    )
    
    /**
     * Non-food labels that should be immediately rejected.
     * These are common ImageNet categories that are NOT food.
     */
    private val NON_FOOD_BLACKLIST = setOf(
        // Common misclassifications
        "shoe", "shoes", "sneaker", "boot", "sandal", "footwear",
        "bag", "handbag", "backpack", "purse", "wallet", "luggage",
        "furniture", "chair", "table", "desk", "sofa", "couch", "bed",
        "clothing", "shirt", "pants", "dress", "jacket", "coat", "hat",
        "car", "vehicle", "truck", "bus", "motorcycle", "bicycle",
        "phone", "computer", "laptop", "keyboard", "mouse", "monitor",
        "building", "house", "tower", "bridge", "road", "street",
        "animal", "dog", "cat", "bird", "horse", "cow", "sheep", // live animals, not meat
        "person", "people", "face", "hand", "body",
        "plant", "tree", "flower", "grass", "leaf", // non-edible plants
        "ball", "toy", "game", "sports",
        "tool", "hammer", "screwdriver", "wrench",
        "book", "paper", "document", "text",
        "bottle", "container", "box", "package", // containers, not contents
        "clock", "watch", "electronics", "appliance",
        "sky", "cloud", "water", "ocean", "mountain", "landscape"
    )
    
    /**
     * Food-related prefixes/patterns that indicate edible items.
     */
    private val FOOD_PATTERNS = listOf(
        "fried", "grilled", "baked", "roasted", "steamed", "boiled",
        "raw", "fresh", "cooked", "sliced", "chopped", "diced",
        "stuffed", "filled", "topped", "glazed", "breaded", "marinated"
    )
    
    /**
     * Check if a label is likely food-related.
     * @return true if the label should be considered for food matching
     */
    fun isFoodRelated(label: String): Boolean {
        val normalized = label.lowercase().trim()
        
        // Immediate rejection for blacklisted terms
        if (NON_FOOD_BLACKLIST.any { normalized.contains(it) }) {
            return false
        }
        
        // Check whitelist
        if (FOOD_WHITELIST.any { normalized.contains(it) || it.contains(normalized) }) {
            return true
        }
        
        // Check food preparation patterns
        if (FOOD_PATTERNS.any { normalized.startsWith(it) }) {
            return true
        }
        
        return false
    }
    
    /**
     * Filter a list of classification results to only food-related labels.
     */
    fun filterFoodOnly(results: List<ClassificationResult>): List<ClassificationResult> {
        return results.filter { isFoodRelated(it.label) }
    }
    
    /**
     * Get confidence that a label is food-related.
     * Returns 0.0 for non-food, original confidence for food.
     */
    fun getFoodConfidence(result: ClassificationResult): Float {
        return if (isFoodRelated(result.label)) result.confidence else 0f
    }
}
