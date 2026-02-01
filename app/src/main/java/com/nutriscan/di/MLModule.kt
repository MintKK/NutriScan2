package com.nutriscan.di

import com.nutriscan.ml.FoodClassificationService
import com.nutriscan.ml.TFLiteFoodClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for ML classification services.
 * 
 * ACTIVE: TFLiteFoodClassifier (food-trained model)
 * 
 * To fallback to ML Kit (generic + filter):
 * Change impl to MLKitFoodClassifier
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MLModule {
    
    /**
     * Binds the food classification service.
     * 
     * CURRENT: TFLiteFoodClassifier (food-trained, 2000+ food classes)
     */
    @Binds
    @Singleton
    abstract fun bindFoodClassificationService(
        impl: TFLiteFoodClassifier
    ): FoodClassificationService
}
