package com.nutriscan

data class UserProfile(
    val goal: Goal,
    val gender: Gender,
    val age: Int,
    val weightKg: Float,
    val heightCm: Float,
    val activityLevel: ActivityLevel
)

enum class Goal { FAT_LOSS, MUSCLE_GAIN, WEIGHT_MAINTENANCE }
enum class Gender { MALE, FEMALE }
enum class ActivityLevel { SEDENTARY, LIGHTLY_ACTIVE, MODERATELY_ACTIVE, VERY_ACTIVE }
