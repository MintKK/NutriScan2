package com.nutriscan.barcode

import android.util.Log
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.repository.FoodRepository
import com.nutriscan.network.OpenFoodFactsApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a barcode lookup.
 */
sealed class BarcodeResult {
    data class Found(val food: FoodItem) : BarcodeResult()
    data class NotFound(val barcode: String) : BarcodeResult()
    data class Error(val message: String) : BarcodeResult()
}

/**
 * Orchestrates barcode scanning → OpenFoodFacts API → FoodItem.
 * Also caches scanned products into the local Room database.
 */
@Singleton
class BarcodeService @Inject constructor(
    private val api: OpenFoodFactsApi,
    private val foodRepository: FoodRepository
) {
    companion object {
        private const val TAG = "BarcodeService"
    }

    /**
     * Look up a barcode via OpenFoodFacts and return a FoodItem.
     * If the product was previously scanned and saved locally, returns
     * the cached version. Otherwise queries the API, saves to Room,
     * and returns the result.
     */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        // 1. Check local DB first (barcode stored in tags field)
        val cached = foodRepository.getByExactName("barcode:$barcode")
        if (cached != null) {
            Log.d(TAG, "Cache hit for barcode $barcode: ${cached.name}")
            return BarcodeResult.Found(cached)
        }

        // 2. Call OpenFoodFacts API
        return try {
            val response = api.getProduct(barcode)

            if (response.status != 1 || response.product == null) {
                Log.w(TAG, "Product not found for barcode: $barcode")
                return BarcodeResult.NotFound(barcode)
            }

            val product = response.product
            val name = product.productName?.takeIf { it.isNotBlank() }
                ?: return BarcodeResult.NotFound(barcode)
            val nutriments = product.nutriments

            // Build FoodItem from API data
            val displayName = buildString {
                append(name)
                if (!product.brands.isNullOrBlank()) {
                    append(" (${product.brands})")
                }
            }

            val foodItem = FoodItem(
                name = displayName,
                kcalPer100g = nutriments?.energyKcal100g?.toInt() ?: 0,
                proteinPer100g = nutriments?.proteins100g ?: 0f,
                carbsPer100g = nutriments?.carbohydrates100g ?: 0f,
                fatPer100g = nutriments?.fat100g ?: 0f,
                tags = "barcode:$barcode,scanned",
                aliases = name.lowercase()
            )

            // 3. Save to Room for future offline access
            val id = foodRepository.insertCustomFood(foodItem)
            val savedFood = foodItem.copy(id = id.toInt())

            Log.d(TAG, "Saved barcode product: $displayName (id=$id)")
            BarcodeResult.Found(savedFood)

        } catch (e: Exception) {
            Log.e(TAG, "Barcode lookup failed", e)
            BarcodeResult.Error("Could not look up barcode: ${e.localizedMessage}")
        }
    }
}
