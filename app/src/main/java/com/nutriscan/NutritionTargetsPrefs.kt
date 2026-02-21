package com.nutriscan

import android.content.SharedPreferences

object NutritionTargetsPrefs {

    fun save(prefs: SharedPreferences, targets: NutritionTargets) {
        prefs.edit()
            .putBoolean("questionnaire_done", true)
            .putInt("calories", targets.calories)
            .putInt("protein", targets.proteinGrams)
            .putInt("carbs", targets.carbGrams)
            .putInt("fat", targets.fatGrams)
            .apply()
    }

    fun load(prefs: SharedPreferences): NutritionTargets? {
        if (!prefs.getBoolean("questionnaire_done", false)) return null
        return NutritionTargets(
            calories     = prefs.getInt("calories", 0),
            proteinGrams = prefs.getInt("protein", 0),
            carbGrams    = prefs.getInt("carbs", 0),
            fatGrams     = prefs.getInt("fat", 0)
        )
    }
}
