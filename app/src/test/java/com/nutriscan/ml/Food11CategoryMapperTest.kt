package com.nutriscan.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Food11CategoryMapper.
 * Verifies that Food-11 coarse categories correctly expand to specific food search terms.
 */
class Food11CategoryMapperTest {

    // ==================== getSearchTerms() ====================

    @Test
    fun `Meat category maps to common meats`() {
        val terms = Food11CategoryMapper.getSearchTerms("Meat")
        assertTrue("beef" in terms)
        assertTrue("chicken" in terms)
        assertTrue("pork" in terms)
        assertTrue("steak" in terms)
        assertTrue("sausage" in terms)
    }

    @Test
    fun `Rice category maps to rice dishes`() {
        val terms = Food11CategoryMapper.getSearchTerms("Rice")
        assertTrue("rice" in terms)
        assertTrue("fried rice" in terms)
        assertTrue("sushi" in terms)
    }

    @Test
    fun `Noodles-Pasta category maps to noodle and pasta types`() {
        val terms = Food11CategoryMapper.getSearchTerms("Noodles-Pasta")
        assertTrue("pasta" in terms)
        assertTrue("spaghetti" in terms)
        assertTrue("ramen" in terms)
        assertTrue("noodles" in terms)
    }

    @Test
    fun `Vegetable-Fruit category maps to produce`() {
        val terms = Food11CategoryMapper.getSearchTerms("Vegetable-Fruit")
        assertTrue("apple" in terms)
        assertTrue("banana" in terms)
        assertTrue("broccoli" in terms)
    }

    @Test
    fun `Dessert category maps to sweets`() {
        val terms = Food11CategoryMapper.getSearchTerms("Dessert")
        assertTrue("cake" in terms)
        assertTrue("cookie" in terms)
        assertTrue("chocolate" in terms)
    }

    @Test
    fun `Bread category maps to baked goods`() {
        val terms = Food11CategoryMapper.getSearchTerms("Bread")
        assertTrue("bread" in terms)
        assertTrue("toast" in terms)
        assertTrue("croissant" in terms)
    }

    @Test
    fun `Dairy product maps to dairy items`() {
        val terms = Food11CategoryMapper.getSearchTerms("Dairy product")
        assertTrue("milk" in terms)
        assertTrue("cheese" in terms)
        assertTrue("yogurt" in terms)
    }

    @Test
    fun `Egg maps to egg dishes`() {
        val terms = Food11CategoryMapper.getSearchTerms("Egg")
        assertTrue("egg" in terms)
        assertTrue("omelette" in terms)
    }

    @Test
    fun `Fried food maps to fried items`() {
        val terms = Food11CategoryMapper.getSearchTerms("Fried food")
        assertTrue("french fries" in terms)
        assertTrue("fried chicken" in terms)
    }

    @Test
    fun `Seafood maps to seafood items`() {
        val terms = Food11CategoryMapper.getSearchTerms("Seafood")
        assertTrue("fish" in terms)
        assertTrue("salmon" in terms)
        assertTrue("shrimp" in terms)
    }

    @Test
    fun `Soup maps to soup items`() {
        val terms = Food11CategoryMapper.getSearchTerms("Soup")
        assertTrue("soup" in terms)
        assertTrue("stew" in terms)
    }

    // ==================== Case insensitive and variant matching ====================

    @Test
    fun `case-insensitive category lookup`() {
        val result = Food11CategoryMapper.getSearchTerms("meat")
        assertTrue(result.isNotEmpty())
        assertTrue("beef" in result)
    }

    @Test
    fun `slash variant matches hyphen`() {
        // "Noodles/Pasta" should match "Noodles-Pasta"
        val result = Food11CategoryMapper.getSearchTerms("Noodles/Pasta")
        assertTrue(result.isNotEmpty())
        assertTrue("pasta" in result)
    }

    @Test
    fun `unknown category falls back to itself`() {
        val result = Food11CategoryMapper.getSearchTerms("UnknownCategory")
        assertEquals(1, result.size)
        assertEquals("unknowncategory", result[0])
    }

    // ==================== isKnownCategory() ====================

    @Test
    fun `all 11 categories are known`() {
        val expectedCategories = listOf(
            "Bread", "Dairy product", "Dessert", "Egg", "Fried food",
            "Meat", "Noodles-Pasta", "Rice", "Seafood", "Soup", "Vegetable-Fruit"
        )
        expectedCategories.forEach { category ->
            assertTrue("$category should be known", Food11CategoryMapper.isKnownCategory(category))
        }
    }

    @Test
    fun `getAllCategories returns 11 entries`() {
        assertEquals(11, Food11CategoryMapper.getAllCategories().size)
    }
}
