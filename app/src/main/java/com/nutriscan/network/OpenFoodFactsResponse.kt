package com.nutriscan.network

import com.google.gson.annotations.SerializedName

/**
 * Response models for the OpenFoodFacts API.
 */
data class OpenFoodFactsResponse(
    val status: Int,                   // 1 = found, 0 = not found
    val product: OpenFoodFactsProduct?
)

data class OpenFoodFactsProduct(
    @SerializedName("product_name")
    val productName: String?,

    val brands: String?,

    @SerializedName("image_url")
    val imageUrl: String?,

    val nutriments: Nutriments?
)

data class Nutriments(
    @SerializedName("energy-kcal_100g")
    val energyKcal100g: Float?,

    @SerializedName("proteins_100g")
    val proteins100g: Float?,

    @SerializedName("carbohydrates_100g")
    val carbohydrates100g: Float?,

    @SerializedName("fat_100g")
    val fat100g: Float?
)
