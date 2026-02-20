package com.nutriscan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.data.repository.StepRepository
import com.nutriscan.sensor.StepCounterService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val stepRepository: StepRepository
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
    
    /**
     * Today's step count — observed from the database via StepRepository.
     * Person B can use this to compute calories burned.
     */
    val todaySteps: StateFlow<Int> = stepRepository.getTodaySteps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    /**
     * Live step count from the running foreground service.
     * Updates more frequently than the database-backed todaySteps.
     */
    val liveSteps: StateFlow<Int> = StepCounterService.currentSteps
    
    /** Whether the step counter service is currently running. */
    val isStepTrackingActive: StateFlow<Boolean> = StepCounterService.isServiceRunning

    val userWeight: StateFlow<Int> = mealRepository.getWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70) // 70 as default weight

    /** Calories burnt from physical activity*/
    val caloriesBurned: StateFlow<Double> = combine(liveSteps, userWeight) { steps, weight ->
        val multiplier = when {
            weight >= 86 -> 0.55
            weight >= 70 -> 0.45
            else -> 0.35
        }
        steps * multiplier
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Total calories today*/
    val netCalories: StateFlow<Double> = combine(todayCalories, caloriesBurned) { food, burned ->
        food.toDouble() - burned
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    
    fun setCalorieGoal(goal: Int) {
        _calorieGoal.value = goal
    }
    
    fun deleteMeal(id: Int) {
        viewModelScope.launch {
            mealRepository.deleteLog(id)
        }
    }
}
