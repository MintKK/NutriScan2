// File: data/repository/CloudinaryRepository.kt
package com.nutriscan.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.nutriscan.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cloudinary: Cloudinary by lazy {
        android.util.Log.d("Cloudinary", "Cloud name: ${BuildConfig.CLOUDINARY_CLOUD_NAME}")
        android.util.Log.d("Cloudinary", "API Key length: ${BuildConfig.CLOUDINARY_API_KEY.length}")
        android.util.Log.d("Cloudinary", "API Secret length: ${BuildConfig.CLOUDINARY_API_SECRET.length}")

        Cloudinary(
            mapOf(
                "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                "api_key" to BuildConfig.CLOUDINARY_API_KEY,
                "api_secret" to BuildConfig.CLOUDINARY_API_SECRET
            )
        )
    }

    init {
        android.util.Log.d("Cloudinary", "Cloudinary initialized with cloud name: ${cloudinary.config.cloudName}")
    }

    suspend fun uploadImage(
        imageUri: Uri,
        folder: String = "nutriscan"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Cloudinary", "Starting upload to folder: $folder")

            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Could not open image"))

            val file = File.createTempFile("upload_", ".jpg", context.cacheDir)
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            android.util.Log.d("Cloudinary", "Temp file created: ${file.absolutePath}")

            // FIX: Use HashMap instead of ObjectUtils.asMap
            val uploadOptions = HashMap<String, Any>().apply {
                put("folder", folder)
                put("overwrite", true)
                put("resource_type", "image")
            }

            android.util.Log.d("Cloudinary", "Upload options: $uploadOptions")

            val uploadResult = cloudinary.uploader().upload(file, uploadOptions)

            android.util.Log.d("Cloudinary", "Upload result: $uploadResult")

            val imageUrl = uploadResult["secure_url"] as String
            android.util.Log.d("Cloudinary", "Upload successful: $imageUrl")

            file.delete()
            Result.success(imageUrl)
        } catch (e: Exception) {
            android.util.Log.e("Cloudinary", "Upload failed", e)
            android.util.Log.e("Cloudinary", "Error message: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun uploadImage(
        bitmap: Bitmap,
        folder: String = "nutriscan"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File.createTempFile("upload_", ".jpg", context.cacheDir)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            // FIX: Use HashMap instead of ObjectUtils.asMap
            val uploadOptions = HashMap<String, Any>().apply {
                put("folder", folder)
                put("overwrite", true)
                put("resource_type", "image")
            }

            val uploadResult = cloudinary.uploader().upload(file, uploadOptions)
            val imageUrl = uploadResult["secure_url"] as String

            file.delete()
            Result.success(imageUrl)
        } catch (e: Exception) {
            android.util.Log.e("Cloudinary", "Bitmap upload failed", e)
            Result.failure(e)
        }
    }
}