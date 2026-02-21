package com.nutriscan

object NutritionCalculator {
    fun calculateTargets(profile: UserProfile): NutritionTargets {
        require(profile.weightKg > 0) { "Weight must be positive" }
        require(profile.heightCm > 0) { "Height must be positive" }
        require(profile.age in 1..150) { "Age must be between 1 and 150" }

        val bmr = if (profile.gender == Gender.MALE) {
            (10 * profile.weightKg) + (6.25 * profile.heightCm) - (5 * profile.age) + 5
        } else {
            (10 * profile.weightKg) + (6.25 * profile.heightCm) - (5 * profile.age) - 161
        }

        val tdee = bmr * when (profile.activityLevel) {
            ActivityLevel.SEDENTARY -> 1.2
            ActivityLevel.LIGHTLY_ACTIVE -> 1.375
            ActivityLevel.MODERATELY_ACTIVE -> 1.55
            ActivityLevel.VERY_ACTIVE -> 1.725
        }

        val calories = when (profile.goal) {
            Goal.FAT_LOSS -> (tdee * 0.8).toInt()
            Goal.MUSCLE_GAIN -> (tdee * 1.2).toInt()
            Goal.WEIGHT_MAINTENANCE -> tdee.toInt()
        }

        val proteinGrams = when (profile.goal) {
            Goal.FAT_LOSS -> (profile.weightKg * 2.0).toInt()
            Goal.MUSCLE_GAIN -> (profile.weightKg * 2.5).toInt()
            Goal.WEIGHT_MAINTENANCE -> (profile.weightKg * 1.8).toInt()
        }

        val fatGrams = (calories * 0.25 / 9).toInt()
        val carbGrams = ((calories - (proteinGrams * 4) - (fatGrams * 9)) / 4).coerceAtLeast(0)

        return NutritionTargets(calories, proteinGrams, carbGrams, fatGrams)
    }
}