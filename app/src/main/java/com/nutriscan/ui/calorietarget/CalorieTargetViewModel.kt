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

    val getTargetProtein = mealRepository.getTargetProtein()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val getTargetCarbs = mealRepository.getTargetCarbs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val getTargetFat = mealRepository.getTargetFat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun setCalorieTarget(targetCalories: Int) {
        viewModelScope.launch {
            mealRepository.saveTargetCalories(targetCalories)
        }
    }

    fun setTargetMacros(protein: Int, carbs: Int, fat: Int) {
        viewModelScope.launch {
            mealRepository.saveTargetMacros(protein, carbs, fat)
        }
    }

    //------------------ GENDER(is female)
    val getIsFemale = mealRepository.getIsFemale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),false)

    fun setIsFemale(value: Boolean) {
        viewModelScope.launch {
            mealRepository.saveIsFemale(value)
        }
    }

    //------------------ WEIGHT
    val getWeight = mealRepository.getWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),50)

    fun setWeight(value: Int) {
        viewModelScope.launch {
            mealRepository.saveWeight(value)
        }
    }

    //------------------ HEIGHT
    val getHeight = mealRepository.getHeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),160)

    fun setHeight(value: Int) {
        viewModelScope.launch {
            mealRepository.saveHeight(value)
        }
    }

    //------------------ AGE
    val getAge = mealRepository.getAge()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),21)

    fun setAge(value: Int) {
        viewModelScope.launch {
            mealRepository.saveAge(value)
        }
    }
}
