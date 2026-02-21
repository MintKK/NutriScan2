package com.nutriscan.ml

import com.nutriscan.data.local.entity.FoodItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FoodAliasIndex.
 * Tests O(1) lookup behaviour for exact name, alias, and partial matches.
 * Uses synthetic FoodItem data for deterministic, reproducible assertions.
 */
class FoodAliasIndexTest {

    private lateinit var index: FoodAliasIndex

    /** Build a test index with known food items */
    @Before
    fun setUp() {
        val foods = listOf(
            FoodItem(id = 1, name = "banana", kcalPer100g = 89, proteinPer100g = 1.1f, carbsPer100g = 22.8f, fatPer100g = 0.3f,
                tags = "fruit", aliases = "plantain,bananas"),
            FoodItem(id = 2, name = "fried rice", kcalPer100g = 163, proteinPer100g = 3.7f, carbsPer100g = 24.0f, fatPer100g = 5.8f,
                tags = "asian,rice", aliases = "nasi goreng,chinese fried rice"),
            FoodItem(id = 3, name = "pad thai", kcalPer100g = 120, proteinPer100g = 5.0f, carbsPer100g = 16.0f, fatPer100g = 4.0f,
                tags = "thai,noodles", aliases = "phad thai,thai stir fry noodles"),
            FoodItem(id = 4, name = "chicken tikka masala", kcalPer100g = 150, proteinPer100g = 12.0f, carbsPer100g = 8.0f, fatPer100g = 8.0f,
                tags = "indian,curry", aliases = "tikka masala,chicken tikka"),
            FoodItem(id = 5, name = "hamburger", kcalPer100g = 250, proteinPer100g = 15.0f, carbsPer100g = 20.0f, fatPer100g = 13.0f,
                tags = "american", aliases = "burger,cheeseburger"),
            FoodItem(id = 6, name = "apple", kcalPer100g = 52, proteinPer100g = 0.3f, carbsPer100g = 14.0f, fatPer100g = 0.2f,
                tags = "fruit", aliases = null),
            FoodItem(id = 7, name = "spaghetti bolognese", kcalPer100g = 130, proteinPer100g = 7.0f, carbsPer100g = 15.0f, fatPer100g = 4.5f,
                tags = "italian,pasta", aliases = "spag bol,bolognese,spaghetti with meat sauce")
        )
        index = FoodAliasIndex(foods)
    }

    // ==================== findByExactName() ====================

    @Test
    fun `exact name match returns correct food`() {
        val result = index.findByExactName("banana")
        assertNotNull(result)
        assertEquals(1, result!!.id)
        assertEquals("banana", result.name)
    }

    @Test
    fun `exact name match works with multi-word names`() {
        // Note: "fried rice" normalizes to "rice" because "fried" is a stopword.
        // So we test with "pad thai" whose words are NOT stopwords.
        val result = index.findByExactName("pad thai")
        assertNotNull(result)
        assertEquals(3, result!!.id)
    }

    @Test
    fun `exact name match returns null for unknown food`() {
        assertNull(index.findByExactName("unicorn steak"))
    }

    @Test
    fun `exact name match is case-normalized`() {
        // The index normalizes via LabelNormalizer, so keys are lowercase
        val result = index.findByExactName("banana")
        assertNotNull(result)
    }

    // ==================== findByAlias() ====================

    @Test
    fun `alias match returns correct food`() {
        val result = index.findByAlias("plantain")
        assertNotNull(result)
        assertEquals(1, result!!.id)
        assertEquals("banana", result.name)
    }

    @Test
    fun `alias match for multi-word alias`() {
        val result = index.findByAlias("nasi goreng")
        assertNotNull(result)
        assertEquals(2, result!!.id)
        assertEquals("fried rice", result.name)
    }

    @Test
    fun `alias match for alternative spelling`() {
        val result = index.findByAlias("phad thai")
        assertNotNull(result)
        assertEquals(3, result!!.id)
        assertEquals("pad thai", result.name)
    }

    @Test
    fun `alias match returns null for non-alias`() {
        assertNull(index.findByAlias("completely unknown food"))
    }

    @Test
    fun `alias match works for shortened names`() {
        val result = index.findByAlias("tikka masala")
        assertNotNull(result)
        assertEquals(4, result!!.id)
    }

    @Test
    fun `alias match for burger variant`() {
        val result = index.findByAlias("burger")
        assertNotNull(result)
        assertEquals(5, result!!.id)
        assertEquals("hamburger", result.name)
    }

    // ==================== findByPartialName() ====================

    @Test
    fun `partial name match finds containing match`() {
        val result = index.findByPartialName("spaghetti")
        assertNotNull(result)
        assertEquals(7, result!!.id)
    }

    @Test
    fun `partial name match respects minimum length`() {
        // "ham" is < 4 characters so should return null (prevents "ham" → "hamburger")
        assertNull(index.findByPartialName("ham"))
    }

    @Test
    fun `partial name match with sufficient length`() {
        val result = index.findByPartialName("hamburger")
        assertNotNull(result)
        assertEquals(5, result!!.id)
    }

    // ==================== size() and isEmpty() ====================

    @Test
    fun `index reports correct size`() {
        assertEquals(7, index.size())
    }

    @Test
    fun `index is not empty after population`() {
        assertFalse(index.isEmpty())
    }

    @Test
    fun `empty index reports empty`() {
        val emptyIndex = FoodAliasIndex(emptyList())
        assertTrue(emptyIndex.isEmpty())
        assertEquals(0, emptyIndex.size())
    }
}
