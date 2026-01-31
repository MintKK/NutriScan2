package com.nutriscan.ui.addmeal

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.repository.FoodRepository
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.domain.model.NutritionResult
import com.nutriscan.domain.model.PortionPreset
import com.nutriscan.domain.usecase.CalculateNutritionUseCase
import com.nutriscan.ml.ClassificationResult
import com.nutriscan.ml.FoodClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Add Meal screen.
 * Handles camera capture, ML classification, food selection, and logging.
 */
@HiltViewModel
class AddMealViewModel @Inject constructor(
    private val foodClassifier: FoodClassifier,
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val calculateNutrition: CalculateNutritionUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddMealUiState())
    val uiState: StateFlow<AddMealUiState> = _uiState.asStateFlow()
    
    /**
     * Process captured image through ML classification.
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClassifying = true, error = null) }
            
            try {
                val results = foodClassifier.classify(bitmap)
                val bestLabel = foodClassifier.getBestFoodLabel(results)
                
                if (bestLabel != null) {
                    // Try to find matching food in database
                    val matchedFood = foodRepository.findByMLLabel(bestLabel.label)
                    
                    _uiState.update { state ->
                        state.copy(
                            isClassifying = false,
                            classificationResults = results,
                            selectedFood = matchedFood,
                            mlLabel = bestLabel.label,
                            showConfirmation = matchedFood != null,
                            showManualSearch = matchedFood == null
                        )
                    }
                } else {
                    // No food detected, show manual search
                    _uiState.update { state ->
                        state.copy(
                            isClassifying = false,
                            showManualSearch = true,
                            error = "Could not identify food. Please search manually."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isClassifying = false,
                        error = "Classification failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * User selects a food item (from search or confirmation).
     */
    fun selectFood(food: FoodItem) {
        _uiState.update { state ->
            val nutrition = calculateNutrition(food, state.portionGrams)
            state.copy(
                selectedFood = food,
                calculatedNutrition = nutrition,
                showConfirmation = true,
                showManualSearch = false
            )
        }
    }
    
    /**
     * Select food by name (used by sample image picker).
     * Looks up the food in the database and shows confirmation.
     */
    fun selectFoodByName(foodName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClassifying = true, error = null) }
            
            try {
                val food = foodRepository.findByMLLabel(foodName)
                
                if (food != null) {
                    val nutrition = calculateNutrition(food, _uiState.value.portionGrams)
                    _uiState.update { state ->
                        state.copy(
                            isClassifying = false,
                            selectedFood = food,
                            mlLabel = foodName,
                            calculatedNutrition = nutrition,
                            showConfirmation = true,
                            showManualSearch = false
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isClassifying = false,
                            showManualSearch = true,
                            error = "Food '$foodName' not found in database. Please search manually."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isClassifying = false,
                        error = "Failed to load food: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * User changes portion size.
     */
    fun setPortionGrams(grams: Int) {
        _uiState.update { state ->
            val nutrition = state.selectedFood?.let { calculateNutrition(it, grams) }
                ?: NutritionResult.ZERO
            state.copy(
                portionGrams = grams,
                calculatedNutrition = nutrition
            )
        }
    }
    
    /**
     * User selects a portion preset.
     */
    fun setPortionPreset(preset: PortionPreset) {
        setPortionGrams(preset.grams)
    }
    
    /**
     * Confirm and log the meal.
     */
    fun confirmMeal() {
        val food = _uiState.value.selectedFood ?: return
        val grams = _uiState.value.portionGrams
        val source = if (_uiState.value.showManualSearch) "manual" else "ml"
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLogging = true) }
            
            try {
                mealRepository.logMeal(food, grams, source)
                _uiState.update { it.copy(isLogging = false, mealLogged = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLogging = false,
                    error = "Failed to log meal: ${e.message}"
                ) }
            }
        }
    }
    
    /**
     * Reset state for a new meal.
     */
    fun resetState() {
        _uiState.value = AddMealUiState()
    }
    
    /**
     * Show manual search screen.
     */
    fun showManualSearch() {
        _uiState.update { it.copy(showManualSearch = true) }
    }
    
    /**
     * Search for foods manually.
     */
    fun searchFoods(query: String) = foodRepository.searchFoods(query)
}

/**
 * UI State for Add Meal screen.
 */
data class AddMealUiState(
    val isClassifying: Boolean = false,
    val isLogging: Boolean = false,
    val classificationResults: List<ClassificationResult> = emptyList(),
    val mlLabel: String? = null,
    val selectedFood: FoodItem? = null,
    val portionGrams: Int = 250,  // Default: Bowl
    val calculatedNutrition: NutritionResult = NutritionResult.ZERO,
    val showConfirmation: Boolean = false,
    val showManualSearch: Boolean = false,
    val mealLogged: Boolean = false,
    val error: String? = null
)
