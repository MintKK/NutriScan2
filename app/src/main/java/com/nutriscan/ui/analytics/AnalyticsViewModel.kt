package com.nutriscan.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.data.local.dao.DailyMacros
import com.nutriscan.data.local.dao.DailyNetCalories
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.data.local.entity.StepLog
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.data.repository.StepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    stepRepository: StepRepository
) : ViewModel() {

    val last7DaysCalories: StateFlow<List<DailyCalories>> = mealRepository.getLast7DaysCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val weeklyAverage: StateFlow<Float> = mealRepository.getWeeklyAverageCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val getTargetCalories = mealRepository.getTargetCalories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),0)

    val last7DaysSteps: StateFlow<List<StepLog>> = stepRepository.getStepsForWeek()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userWeight = mealRepository.getWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),50)

    /**Takes into account burned calories**/
    val last7DaysNet: StateFlow<List<DailyNetCalories>> =
        combine(last7DaysSteps, userWeight, last7DaysCalories) { stepLogs, weight, calorieLogs ->

            // Map steps by date for fast lookup
            val stepsByDate = stepLogs.associateBy { it.date }

            calorieLogs.map { dailyCalories ->
                val stepsForDay = stepsByDate[dailyCalories.day]?.steps ?: 0
                val burned = when {
                    weight >= 86 -> stepsForDay * 0.55
                    weight >= 70 -> stepsForDay * 0.45
                    else -> stepsForDay * 0.35
                }

                DailyNetCalories(
                    day = dailyCalories.day,
                    eatenKcal = dailyCalories.totalKcal,
                    burnedKcal = burned.toInt(),
                    netKcal = dailyCalories.totalKcal - burned.toInt()
                )
            }
        }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    /**Takes into account burned calories**/
    val weeklyAverageNet: StateFlow<Float> =
        last7DaysNet
            .map { days ->
                val daysWithMeals = days.filter { it.eatenKcal > 0 } // only count days with meals
                if (daysWithMeals.isEmpty()) 0f
                else daysWithMeals.sumOf { it.netKcal }.toFloat() / daysWithMeals.size
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    
    // ============ MACRO TRENDS ============
    
    val last7DaysMacros: StateFlow<List<DailyMacros>> = mealRepository.getLast7DaysMacros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /** Weekly average macros per day (only counting days with data). */
    val weeklyAverageMacros: StateFlow<MacroTotals> = last7DaysMacros
        .map { days ->
            val daysWithData = days.filter { it.protein > 0 || it.carbs > 0 || it.fat > 0 }
            if (daysWithData.isEmpty()) MacroTotals(0f, 0f, 0f)
            else MacroTotals(
                protein = daysWithData.sumOf { it.protein.toDouble() }.toFloat() / daysWithData.size,
                carbs = daysWithData.sumOf { it.carbs.toDouble() }.toFloat() / daysWithData.size,
                fat = daysWithData.sumOf { it.fat.toDouble() }.toFloat() / daysWithData.size
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroTotals(0f, 0f, 0f))
    
    // ============ DRILL-DOWN (Day detail bottom sheet) ============
    
    private val _selectedDateMeals = MutableStateFlow<List<MealLog>>(emptyList())
    val selectedDateMeals: StateFlow<List<MealLog>> = _selectedDateMeals
    
    private val _selectedDateLabel = MutableStateFlow<String?>(null)
    val selectedDateLabel: StateFlow<String?> = _selectedDateLabel
    
    fun selectDate(dateKey: String) {
        val label = try {
            val parsed = java.time.LocalDate.parse(dateKey)
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
        } catch (e: Exception) { dateKey }
        
        _selectedDateLabel.value = label
        viewModelScope.launch {
            _selectedDateMeals.value = mealRepository.getLogsForDate(dateKey)
        }
    }
    
    fun clearSelectedDate() {
        _selectedDateLabel.value = null
        _selectedDateMeals.value = emptyList()
    }
}
