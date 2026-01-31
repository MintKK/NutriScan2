package com.nutriscan.data.repository

import com.nutriscan.data.local.dao.FoodItemDao
import com.nutriscan.data.local.entity.FoodItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository @Inject constructor(
    private val foodItemDao: FoodItemDao
) {
    
    fun getAllFoods(): Flow<List<FoodItem>> = foodItemDao.getAllFoods()
    
    fun searchFoods(query: String): Flow<List<FoodItem>> = foodItemDao.searchFoods(query)
    
    suspend fun getById(id: Int): FoodItem? = foodItemDao.getById(id)
    
    suspend fun getByExactName(name: String): FoodItem? = foodItemDao.getByExactName(name)
    
    /**
     * Find the best matching food item for an ML label.
     * Handles normalization of label (replacing underscores, lowercasing).
     */
    suspend fun findByMLLabel(mlLabel: String): FoodItem? {
        val normalized = mlLabel.lowercase().replace("_", " ").trim()
        
        // Try exact match first
        foodItemDao.getByExactName(normalized)?.let { return it }
        
        // Try fuzzy search
        return foodItemDao.findBestMatch(normalized)
    }
    
    suspend fun insertCustomFood(food: FoodItem): Long = foodItemDao.insert(food)
}
