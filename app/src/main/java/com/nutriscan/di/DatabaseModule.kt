package com.nutriscan.di

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutriscan.data.local.AppDatabase
import com.nutriscan.data.local.dao.FoodItemDao
import com.nutriscan.data.local.dao.MealLogDao
import com.nutriscan.data.local.dao.StepLogDao
import com.nutriscan.data.local.dao.ActivityLogDao
import com.nutriscan.data.local.entity.FoodItem
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val TAG = "DatabaseModule"
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        foodItemDaoProvider: Provider<FoodItemDao> // Use provider to avoid circular dependency
    ): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "nutriscan_database"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed database on first creation
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    scope.launch {
                        seedDatabase(context, foodItemDaoProvider.get())
                    }
                }
                
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    // Re-seed after destructive migration (DB version bump)
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    scope.launch {
                        seedDatabase(context, foodItemDaoProvider.get())
                    }
                }
            })
            .fallbackToDestructiveMigration()
            .build()
    }

    private suspend fun seedDatabase(context: Context, dao: FoodItemDao) {
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
    
    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao {
        return database.foodItemDao()
    }
    
    @Provides
    fun provideMealLogDao(database: AppDatabase): MealLogDao {
        return database.mealLogDao()
    }
    
    @Provides
    fun provideStepLogDao(database: AppDatabase): StepLogDao {
        return database.stepLogDao()
    }
    
    @Provides
    fun provideActivityLogDao(database: AppDatabase): ActivityLogDao {
        return database.activityLogDao()
    }
}
