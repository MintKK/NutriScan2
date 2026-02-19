package com.nutriscan.data.remote.models

data class Like(
    val likeID: String = "",
    val postID: String = "",
    val userID: String = "",
    val createdAt: Long = System.currentTimeMillis()
)