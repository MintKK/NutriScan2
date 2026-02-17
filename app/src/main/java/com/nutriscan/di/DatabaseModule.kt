package com.nutriscan.di

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutriscan.data.local.AppDatabase
import com.nutriscan.data.local.dao.FoodItemDao
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.entity.FoodItem
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.InputStreamReader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val TAG = "DatabaseModule"
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "nutriscan_database"
        )
            .fallbackToDestructiveMigration()
            .build()
        
        // Seed database if empty (runs after build, so instance is available)
        runBlocking {
            val dao = db.foodItemDao()
            val existingCount = dao.getCount()
            Log.d(TAG, "DB food count: $existingCount")
            
            if (existingCount == 0) {
                Log.d(TAG, "Seeding food database from JSON...")
                try {
                    val inputStream = context.assets.open("food_items.json")
                    val reader = InputStreamReader(inputStream)
                    val type = object : TypeToken<List<FoodItem>>() {}.type
                    val foods: List<FoodItem> = Gson().fromJson(reader, type)
                    dao.insertAll(foods)
                    reader.close()
                    Log.d(TAG, "Seeded ${foods.size} food items")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to seed database", e)
                }
            }
        }
        
        return db
    }
    
    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao {
        return database.foodItemDao()
    }
    
    @Provides
    fun provideMealLogDao(database: AppDatabase): MealLogDao {
        return database.mealLogDao()
    }
}
