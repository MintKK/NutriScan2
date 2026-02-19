package com.nutriscan.data.remote.models

data class Follow(
    val followID: String = "",
    val followerID: String = "",
    val followingID: String = "",
    val createdAt: Long = System.currentTimeMillis()
)