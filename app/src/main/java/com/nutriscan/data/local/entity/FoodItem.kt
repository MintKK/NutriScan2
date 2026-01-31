package com.nutriscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a food item with nutritional information per 100g serving.
 * This table is pre-populated from food_items.json on first launch.
 */
@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String,
    
    @ColumnInfo(name = "kcal_per_100g")
    val kcalPer100g: Int,
    
    @ColumnInfo(name = "protein_per_100g")
    val proteinPer100g: Float,
    
    @ColumnInfo(name = "carbs_per_100g")
    val carbsPer100g: Float,
    
    @ColumnInfo(name = "fat_per_100g")
    val fatPer100g: Float,
    
    val tags: String? = null,      // e.g., "breakfast,asian,quick"
    val aliases: String? = null    // e.g., "fried rice,nasi goreng,炒饭"
)
