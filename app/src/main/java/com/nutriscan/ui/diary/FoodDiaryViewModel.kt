package com.nutriscan.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.entity.MealLog
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.util.MealImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Groups meal logs by date for the Food Diary timeline view.
 */
data class DayGroup(
    val label: String,      // "Today", "Yesterday", "Feb 19, 2026"
    val dateKey: String,     // "2026-02-21" (for sorting)
    val meals: List<MealLog>
)

@HiltViewModel
class FoodDiaryViewModel @Inject constructor(
    private val mealRepository: MealRepository
) : ViewModel() {

    val diaryGroups: StateFlow<List<DayGroup>> = mealRepository.getRecentLogs(200)
        .map { meals -> groupByDate(meals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMeal(id: Int) {
        viewModelScope.launch {
            mealRepository.deleteLogWithImage(id)
        }
    }

    private fun groupByDate(meals: List<MealLog>): List<DayGroup> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
        )

        return meals
            .groupBy { dateFormat.format(Date(it.timestamp)) }
            .map { (dateKey, mealsForDay) ->
                val label = when (dateKey) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> displayFormat.format(dateFormat.parse(dateKey)!!)
                }
                DayGroup(label = label, dateKey = dateKey, meals = mealsForDay)
            }
            .sortedByDescending { it.dateKey }
    }
}
