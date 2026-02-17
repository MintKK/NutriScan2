package com.nutriscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nutriscan.data.local.dao.FoodItemDao
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.local.entity.MealLog

@Database(
    entities = [FoodItem::class, MealLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealLogDao(): MealLogDao
}
