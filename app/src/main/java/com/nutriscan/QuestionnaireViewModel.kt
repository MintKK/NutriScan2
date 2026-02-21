package com.nutriscan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val mealRepository: MealRepository
) : ViewModel() {
    var step by mutableIntStateOf(0)
        private set

    var selectedGoal          by mutableStateOf<Goal?>(null)
    var selectedGender        by mutableStateOf<Gender?>(null)
    var selectedActivityLevel by mutableStateOf<ActivityLevel?>(null)
    var age                   by mutableStateOf("")
    var weightKg              by mutableStateOf("")
    var heightCm              by mutableStateOf("")
    var errorMessage          by mutableStateOf<String?>(null)

    val totalSteps = 3

    fun next(): Boolean {
        errorMessage = null
        when (step) {
            0 -> if (selectedGoal == null) { errorMessage = "Please select a goal."; return false }
            1 -> {
                if (selectedGender == null) { errorMessage = "Please select a gender."; return false }
                if (age.toIntOrNull() == null || age.toInt() < 1) { errorMessage = "Please enter a valid age."; return false }
                if (weightKg.toFloatOrNull() == null || weightKg.toFloat() <= 0) { errorMessage = "Please enter a valid weight."; return false }
                if (heightCm.toFloatOrNull() == null || heightCm.toFloat() <= 0) { errorMessage = "Please enter a valid height."; return false }
            }
            2 -> if (selectedActivityLevel == null) { errorMessage = "Please select an activity level."; return false }
        }
        if (step < totalSteps - 1) { step++; return false }
        return true // last step completed
    }

    fun goBack() {
        if (step > 0) step--
    }

    fun buildProfile() = UserProfile(
        goal          = selectedGoal!!,
        gender        = selectedGender!!,
        age           = age.toInt(),
        weightKg      = weightKg.toFloat(),
        heightCm      = heightCm.toFloat(),
        activityLevel = selectedActivityLevel!!
    )

    /**
     * Save calculated targets to DataStore so the Dashboard
     * immediately reflects the new calorie and macro goals.
     */
    fun saveTargetsToDataStore(targets: NutritionTargets) {
        viewModelScope.launch {
            mealRepository.saveTargetCalories(targets.calories)
            mealRepository.saveWeight(weightKg.toFloat().toInt())
            mealRepository.saveHeight(heightCm.toFloat().toInt())
            mealRepository.saveAge(age.toInt())
            mealRepository.saveIsFemale(selectedGender == Gender.FEMALE)
        }
    }
}
