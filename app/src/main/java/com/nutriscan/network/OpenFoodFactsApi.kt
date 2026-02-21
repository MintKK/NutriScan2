package com.nutriscan.network

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for OpenFoodFacts product lookup.
 * Docs: https://wiki.openfoodfacts.org/API
 */
interface OpenFoodFactsApi {

    @GET("api/v0/product/{barcode}.json?fields=product_name,nutriments,brands,image_url")
    suspend fun getProduct(@Path("barcode") barcode: String): OpenFoodFactsResponse
}
