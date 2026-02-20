package com.nutriscan.data.remote.models

data class Comment(
    val commentID: String = "",
    val postID: String = "",
    val userID: String = "",
    val username: String = "",
    val userProfileImageUrl: String = "",
    val content: String = "",
    val numlikes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)