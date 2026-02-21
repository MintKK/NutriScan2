package com.nutriscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nutriscan.data.local.dao.ActivityLogDao
import com.nutriscan.data.local.dao.FoodItemDao
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.dao.StepLogDao
import com.nutriscan.data.local.dao.WaterLogDao
import com.nutriscan.data.local.entity.ActivityLog
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.data.local.entity.StepLog
import com.nutriscan.data.local.entity.WaterLog

@Database(
    entities = [FoodItem::class, MealLog::class, StepLog::class, ActivityLog::class, WaterLog::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun stepLogDao(): StepLogDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun waterLogDao(): WaterLogDao
}
