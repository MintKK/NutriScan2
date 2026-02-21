package com.nutriscan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class QuestionnaireViewModel : ViewModel() {
    var step by mutableIntStateOf(0)
        private set

    var selectedGoal          by mutableStateOf<Goal?>(null)
    var selectedGender        by mutableStateOf<Gender?>(null)
    var selectedActivityLevel by mutableStateOf<ActivityLevel?>(null)
    var age                   by mutableStateOf("")
    var weightKg              by mutableStateOf("")
    var heightCm              by mutableStateOf("")
    var errorMessage          by mutableStateOf<String?>(null)

    val totalSteps = 6

    fun next(): Boolean {
        errorMessage = null
        when (step) {
            0 -> if (selectedGoal == null) { errorMessage = "Please select a goal."; return false }
            1 -> if (selectedGender == null) { errorMessage = "Please select a gender."; return false }
            2 -> if (selectedActivityLevel == null) { errorMessage = "Please select an activity level."; return false }
            3 -> if (age.toIntOrNull() == null) { errorMessage = "Please enter a valid age."; return false }
            4 -> if (weightKg.toFloatOrNull() == null) { errorMessage = "Please enter a valid weight."; return false }
            5 -> if (heightCm.toFloatOrNull() == null) { errorMessage = "Please enter a valid height."; return false }
        }
        if (step < totalSteps - 1) step++ else return true
        return step == totalSteps
    }

    fun buildProfile() = UserProfile(
        goal          = selectedGoal!!,
        gender        = selectedGender!!,
        age           = age.toInt(),
        weightKg      = weightKg.toFloat(),
        heightCm      = heightCm.toFloat(),
        activityLevel = selectedActivityLevel!!
    )
}
