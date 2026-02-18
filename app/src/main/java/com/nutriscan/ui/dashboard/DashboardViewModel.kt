package com.nutriscan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.data.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mealRepository: MealRepository
) : ViewModel() {
    
    // User's calorie goal (can be made configurable via DataStore)
    private val _calorieGoal = MutableStateFlow(2000)
    val calorieGoal: StateFlow<Int> = mealRepository.getTargetCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),0)
    
    val todayCalories: StateFlow<Int> = mealRepository.getTodayTotalCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val todayMacros: StateFlow<MacroTotals> = mealRepository.getTodayMacros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroTotals(0f, 0f, 0f))
    
    val todayMeals: StateFlow<List<MealLog>> = mealRepository.getTodayLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val weeklyAverage: StateFlow<Float> = mealRepository.getWeeklyAverageCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    
    fun setCalorieGoal(goal: Int) {
        _calorieGoal.value = goal
    }
    
    fun deleteMeal(id: Int) {
        viewModelScope.launch {
            mealRepository.deleteLog(id)
        }
    }
}
