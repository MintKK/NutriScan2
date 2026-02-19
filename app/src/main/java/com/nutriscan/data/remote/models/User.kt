package com.nutriscan.data.remote.models

data class User(
    val uid: String = "",
    val username: String = "",
    val displayname: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val numFollowers: Int = 0,
    val numFollowing: Int = 0,
    val numPosts: Int = 0,
    val created: Long = System.currentTimeMillis(),
    val email: String = ""
)