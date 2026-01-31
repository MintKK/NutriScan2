package com.nutriscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutriscan.data.local.entity.FoodItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    
    @Query("SELECT * FROM food_items ORDER BY name ASC")
    fun getAllFoods(): Flow<List<FoodItem>>
    
    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Int): FoodItem?
    
    @Query("SELECT * FROM food_items WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByExactName(name: String): FoodItem?
    
    /**
     * Fuzzy search by name or aliases.
     * Used for matching ML labels to database entries.
     */
    @Query("""
        SELECT * FROM food_items 
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' 
           OR LOWER(aliases) LIKE '%' || LOWER(:query) || '%'
        ORDER BY 
            CASE WHEN LOWER(name) = LOWER(:query) THEN 0
                 WHEN LOWER(name) LIKE LOWER(:query) || '%' THEN 1
                 ELSE 2
            END
        LIMIT 20
    """)
    fun searchFoods(query: String): Flow<List<FoodItem>>
    
    @Query("SELECT * FROM food_items WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(aliases) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findBestMatch(query: String): FoodItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItem>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodItem): Long
    
    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun getCount(): Int
}
