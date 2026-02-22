package com.nutriscan.ui.social

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.repository.SocialRepository
import com.nutriscan.domain.model.NutritionResult
import com.nutriscan.domain.usecase.CalculateNutritionUseCase
import com.nutriscan.ml.ClassificationStatus
import com.nutriscan.ml.FoodClassificationService
import com.nutriscan.ml.FoodMatchResult
import com.nutriscan.ml.FoodMatchingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.graphics.Bitmap

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val foodClassifier: FoodClassificationService,
    private val foodMatchingService: FoodMatchingService,
    private val calculateNutrition: CalculateNutritionUseCase
) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                foodMatchingService.initialize()
            } catch (e: Exception) {
                // Ignore initialization failures
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPostCreated = MutableStateFlow(false)
    val isPostCreated: StateFlow<Boolean> = _isPostCreated.asStateFlow()

    private val _isClassifying = MutableStateFlow(false)
    val isClassifying: StateFlow<Boolean> = _isClassifying.asStateFlow()

    private val _matchResults = MutableStateFlow<List<FoodMatchResult>>(emptyList())
    val matchResults: StateFlow<List<FoodMatchResult>> = _matchResults.asStateFlow()

    private val _selectedFood = MutableStateFlow<FoodMatchResult?>(null)
    val selectedFood: StateFlow<FoodMatchResult?> = _selectedFood.asStateFlow()

    private val _portionGrams = MutableStateFlow(200)
    val portionGrams: StateFlow<Int> = _portionGrams.asStateFlow()

    private val _calculatedNutrition = MutableStateFlow(NutritionResult.ZERO)
    val calculatedNutrition: StateFlow<NutritionResult> = _calculatedNutrition.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    fun classifyImage(bitmap: Bitmap, imageUri: Uri? = null) {
        viewModelScope.launch {
            _isClassifying.value = true
            _error.value = null
            _capturedBitmap.value = bitmap

            try {
                val classificationResult = foodClassifier.classifyFood(bitmap)
                
                if (classificationResult.status == ClassificationStatus.NO_FOOD_DETECTED) {
                    _error.value = "Couldn't identify food. Please try a clearer photo."
                    _isClassifying.value = false
                    return@launch
                }

                if (classificationResult.status == ClassificationStatus.ERROR) {
                    _error.value = "Classification failed."
                    _isClassifying.value = false
                    return@launch
                }

                // Initialize index cleanly before matching
                try {
                    foodMatchingService.initialize()
                } catch (e: Exception) {
                }

                val candidates = foodMatchingService.getValidCandidates(
                    foodMatchingService.matchClassifications(classificationResult.results)
                )

                if (candidates.isNotEmpty()) {
                    _matchResults.value = candidates
                } else {
                    _error.value = "Food detected but could not be matched with database."
                }
            } catch (e: Exception) {
                _error.value = "Failed classifying image: ${e.message}"
            } finally {
                _isClassifying.value = false
            }
        }
    }

    fun selectFood(match: FoodMatchResult, portionGrams: Int = 200) {
        _portionGrams.value = portionGrams
        _selectedFood.value = match
        _matchResults.value = emptyList() // clear candidates to hide candidate selection sheet
        match.matchedFood?.let { food ->
            _calculatedNutrition.value = calculateNutrition(food, portionGrams)
        }
    }

    fun setPortionGrams(grams: Int) {
        if (grams in 1..2000) {
            _portionGrams.value = grams
            _selectedFood.value?.matchedFood?.let { food ->
                _calculatedNutrition.value = calculateNutrition(food, grams)
            }
        }
    }

    fun createPost(
        caption: String,
        foodName: String,
        calories: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        imageUri: Uri
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = socialRepository.createPost(
                    caption = caption,
                    foodName = foodName,
                    calories = calories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    imageUri = imageUri
                )

                result.onSuccess {
                    _isPostCreated.value = true
                }.onFailure { e ->
                    _error.value = "Failed to create post: ${e.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetState() {
        _isPostCreated.value = false
        _error.value = null
        _matchResults.value = emptyList()
        _selectedFood.value = null
        _portionGrams.value = 200
        _calculatedNutrition.value = NutritionResult.ZERO
    }
}