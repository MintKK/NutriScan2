package com.nutriscan.ui.calorietarget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.domain.model.NutritionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalorieTargetViewModel @Inject constructor(
    private val mealRepository: MealRepository
) : ViewModel() {

    val getTargetCalories = mealRepository.getTargetCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),0)

    fun setCalorieTarget(targetCalories: Int) {
        viewModelScope.launch {
            mealRepository.saveTargetCalories(targetCalories)
        }
    }
}
