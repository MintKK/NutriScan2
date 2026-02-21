package com.nutriscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a logged meal entry.
 * Stores the computed nutrition totals based on portion size.
 */
@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(
            entity = FoodItem::class,
            parentColumns = ["id"],
            childColumns = ["food_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("food_item_id"), Index("timestamp")]
)
data class MealLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "food_item_id")
    val foodItemId: Int,
    
    @ColumnInfo(name = "food_name")
    val foodName: String,  // Denormalized for quick display
    
    val grams: Int,
    
    @ColumnInfo(name = "kcal_total")
    val kcalTotal: Int,
    
    @ColumnInfo(name = "protein_total")
    val proteinTotal: Float,
    
    @ColumnInfo(name = "carbs_total")
    val carbsTotal: Float,
    
    @ColumnInfo(name = "fat_total")
    val fatTotal: Float,
    
    val source: String = "ml",  // "ml" or "manual"
    
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null  // Local file path for captured food photo
)
