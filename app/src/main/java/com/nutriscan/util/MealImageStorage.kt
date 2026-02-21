package com.nutriscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles saving, loading, and deleting meal photos in internal storage.
 * Uses app-private internal storage — no permissions required.
 *
 * Photos are stored as compressed JPEGs in: filesDir/meal_images/
 */
object MealImageStorage {

    private const val TAG = "MealImageStorage"
    private const val MEAL_IMAGES_DIR = "meal_images"
    private const val JPEG_QUALITY = 80
    private const val MAX_DIMENSION = 1024  // Downscale to save storage

    /**
     * Save a bitmap as a JPEG file in internal storage.
     * Returns the absolute file path, or null on failure.
     */
    suspend fun saveMealImage(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, MEAL_IMAGES_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "meal_${System.currentTimeMillis()}.jpg"
            val file = File(dir, fileName)

            // Downscale if needed to save storage
            val scaled = downscaleIfNeeded(bitmap)

            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            Log.d(TAG, "Saved meal image: ${file.absolutePath} (${file.length() / 1024} KB)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save meal image", e)
            null
        }
    }

    /**
     * Load a meal image bitmap from its stored path.
     * Returns null if the file doesn't exist or can't be decoded.
     */
    fun loadMealImage(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load meal image: $path", e)
            null
        }
    }

    /**
     * Delete a meal image file.
     */
    suspend fun deleteMealImage(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted meal image: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete meal image: $path", e)
        }
    }

    /**
     * Downscale bitmap if either dimension exceeds MAX_DIMENSION.
     * Preserves aspect ratio.
     */
    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
