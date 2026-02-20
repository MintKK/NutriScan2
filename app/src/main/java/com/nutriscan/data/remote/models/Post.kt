package com.nutriscan.data.remote.models

data class Post(
    val postID: String = "",
    val userID: String = "",
    val username: String = "",
    val userProfileImageUrl: String = "",
    val caption: String = "",
    val foodImageUrl: String = "",
    val foodName: String = "",
    val numCalories: Int = 0,
    val numProtein: Float = 0f,
    val numLikes: Int = 0,
    val numComments: Int = 0,
    val comments: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    var trendingScore: Double = 0.0
)