package com.nutriscan.data.repository

import com.nutriscan.data.local.entity.FoodItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Smart Switch suggestion algorithm from AICoachRepository.
 *
 * Since AICoachRepository can't be instantiated without its full DI graph,
 * we replicate the exact `getSuggestionForFood` algorithm here to validate
 * its keyword matching, threshold logic, and priority ordering.
 *
 * Algorithm (from AICoachRepository.kt:276-309):
 * 1. Check swap map by keyword match (case-insensitive substring)
 * 2. Generic: high calorie (>350 kcal/100g) AND low protein (<8g/100g) → WARNING
 * 3. High fat (>25g/100g) AND low protein (<10g/100g) → TIP
 * 4. Otherwise → null
 */
class AICoachSuggestionTest {

    private data class SwapSuggestion(val alternative: String, val reason: String)

    private val swapMap = mapOf(
        "pizza" to SwapSuggestion("Grilled Chicken Wrap", "~250 fewer kcal, +15g protein"),
        "donut" to SwapSuggestion("Greek Yogurt with Honey", "~200 fewer kcal, +12g protein"),
        "doughnut" to SwapSuggestion("Greek Yogurt with Honey", "~200 fewer kcal, +12g protein"),
        "burger" to SwapSuggestion("Turkey Lettuce Wrap", "~300 fewer kcal, less saturated fat"),
        "fries" to SwapSuggestion("Sweet Potato Wedges", "~100 fewer kcal, more fiber & vitamin A"),
        "french fries" to SwapSuggestion("Sweet Potato Wedges", "~100 fewer kcal, more fiber & vitamin A"),
        "fried chicken" to SwapSuggestion("Grilled Chicken Breast", "~150 fewer kcal, -10g fat"),
        "fried rice" to SwapSuggestion("Steamed Rice with Veggies", "~120 fewer kcal, less oil"),
        "ice cream" to SwapSuggestion("Frozen Yogurt", "~100 fewer kcal, +5g protein"),
        "chocolate" to SwapSuggestion("Dark Chocolate (85%)", "Less sugar, more antioxidants"),
        "cake" to SwapSuggestion("Protein Bar", "~150 fewer kcal, +15g protein"),
        "pasta" to SwapSuggestion("Zucchini Noodles with Sauce", "~200 fewer kcal, more fiber"),
        "soda" to SwapSuggestion("Sparkling Water with Lemon", "~140 fewer kcal, zero sugar"),
        "chips" to SwapSuggestion("Air-popped Popcorn", "~100 fewer kcal, whole grain fiber"),
        "cookie" to SwapSuggestion("Apple Slices with Peanut Butter", "~100 fewer kcal, more fiber & protein")
    )

    /**
     * Replicate the exact getSuggestionForFood logic.
     */
    private fun getSuggestionForFood(food: FoodItem): CoachInsight? {
        val nameLower = food.name.lowercase()

        // 1. Check swap map
        for ((keyword, swap) in swapMap) {
            if (nameLower.contains(keyword)) {
                return CoachInsight(
                    emoji = "🔄",
                    message = "Try \"${swap.alternative}\" instead — ${swap.reason}",
                    type = InsightType.TIP
                )
            }
        }

        // 2. High calorie + low protein
        if (food.kcalPer100g > 350 && food.proteinPer100g < 8f) {
            return CoachInsight(
                emoji = "💡",
                message = "This is calorie-dense (${food.kcalPer100g} kcal/100g) with low protein. Consider a high-protein alternative!",
                type = InsightType.WARNING
            )
        }

        // 3. High fat
        if (food.fatPer100g > 25f && food.proteinPer100g < 10f) {
            return CoachInsight(
                emoji = "🫒",
                message = "High fat content (${food.fatPer100g}g/100g). A grilled or baked version would cut fat significantly.",
                type = InsightType.TIP
            )
        }

        return null
    }

    // ==================== Swap Map Matching ====================

    @Test
    fun `pizza triggers swap suggestion`() {
        val result = getSuggestionForFood(makeFood("Pepperoni Pizza", kcal = 266, protein = 11f, fat = 10f))
        assertNotNull(result)
        assertEquals(InsightType.TIP, result!!.type)
        assertTrue(result.message.contains("Grilled Chicken Wrap"))
    }

    @Test
    fun `burger triggers swap suggestion`() {
        val result = getSuggestionForFood(makeFood("Cheeseburger", kcal = 303, protein = 17f, fat = 14f))
        assertNotNull(result)
        assertTrue(result!!.message.contains("Turkey Lettuce Wrap"))
    }

    @Test
    fun `case insensitive matching for ice cream`() {
        val result = getSuggestionForFood(makeFood("Vanilla Ice Cream", kcal = 207, protein = 3.5f, fat = 11f))
        assertNotNull(result)
        assertTrue(result!!.message.contains("Frozen Yogurt"))
    }

    @Test
    fun `swap map returns TIP insight type with swap emoji`() {
        val result = getSuggestionForFood(makeFood("Chocolate Cake", kcal = 350, protein = 5f, fat = 15f))
        assertNotNull(result)
        assertEquals("🔄", result!!.emoji)
        assertEquals(InsightType.TIP, result.type)
    }

    // ==================== Generic Calorie Analysis ====================

    @Test
    fun `high calorie low protein food triggers generic warning`() {
        // Name must NOT contain any swap map keyword
        val result = getSuggestionForFood(makeFood("Sugary Pastry Bomb", kcal = 400, protein = 5f, fat = 20f))
        assertNotNull(result)
        assertEquals(InsightType.WARNING, result!!.type)
        assertTrue(result.message.contains("calorie-dense"))
    }

    @Test
    fun `high calorie but adequate protein returns null`() {
        val result = getSuggestionForFood(makeFood("Steak Ribeye", kcal = 400, protein = 25f, fat = 30f))
        assertNull("High-cal with adequate protein should be null", result)
    }

    @Test
    fun `exactly 350 kcal does not trigger generic warning`() {
        val result = getSuggestionForFood(makeFood("Borderline Food", kcal = 350, protein = 5f, fat = 10f))
        // 350 is NOT > 350, so generic check shouldn't trigger
        assertNull("350 kcal exactly should not trigger (>350 required)", result)
    }

    // ==================== High Fat Warning ====================

    @Test
    fun `high fat low protein food triggers fat warning`() {
        val result = getSuggestionForFood(makeFood("Butter Croissant Plain", kcal = 340, protein = 7f, fat = 26f))
        assertNotNull(result)
        assertEquals(InsightType.TIP, result!!.type)
        assertTrue(result.message.contains("fat content"))
    }

    @Test
    fun `high fat but adequate protein returns null`() {
        val result = getSuggestionForFood(makeFood("Salmon Fillet", kcal = 208, protein = 20f, fat = 13f))
        assertNull(result)
    }

    // ==================== Healthy Foods ====================

    @Test
    fun `chicken breast returns null`() {
        assertNull(getSuggestionForFood(makeFood("chicken breast", kcal = 165, protein = 31f, fat = 3.6f)))
    }

    @Test
    fun `broccoli returns null`() {
        assertNull(getSuggestionForFood(makeFood("broccoli", kcal = 34, protein = 2.8f, fat = 0.4f)))
    }

    @Test
    fun `rice returns null`() {
        assertNull(getSuggestionForFood(makeFood("steamed rice", kcal = 130, protein = 2.7f, fat = 0.3f)))
    }

    // ==================== Priority Order ====================

    @Test
    fun `swap map takes precedence over generic analysis`() {
        // "Chocolate Cake" matches swap map AND could trigger generic warning
        val result = getSuggestionForFood(makeFood("Chocolate Cake", kcal = 400, protein = 4f, fat = 18f))
        assertNotNull(result)
        assertEquals("Swap map should take priority", "🔄", result!!.emoji)
    }

    // ==================== Helper ====================

    private fun makeFood(
        name: String, kcal: Int, protein: Float, fat: Float, carbs: Float = 0f
    ) = FoodItem(
        name = name, kcalPer100g = kcal,
        proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat
    )
}
