package com.nutriscan.ui.addmeal

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.barcode.BarcodeResult
import com.nutriscan.barcode.BarcodeService
import com.nutriscan.util.MealImageStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nutriscan.data.repository.AICoachRepository
import com.nutriscan.data.repository.CoachInsight
import com.nutriscan.data.repository.FoodRepository
import com.nutriscan.data.repository.MealRepository
import com.nutriscan.domain.model.NutritionResult
import com.nutriscan.domain.model.PortionPreset
import com.nutriscan.domain.usecase.CalculateNutritionUseCase
import com.nutriscan.ml.ClassificationResult
import com.nutriscan.ml.ClassificationStatus
import com.nutriscan.ml.FoodClassificationResult
import com.nutriscan.ml.FoodClassificationService
import com.nutriscan.ml.FoodMatchResult
import com.nutriscan.ml.FoodMatchingService
import com.nutriscan.ml.OCRScannerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Add Meal screen.
 * 
 * Pipeline: Image → Food Classification (interface) → Database Matching → UX
 * 
 * The classifier is injected via interface so it can be swapped:
 * - Current: MLKitFoodClassifier (generic + filter)
 * - Future: TFLiteFoodClassifier (food-trained model)
 */
@HiltViewModel
class AddMealViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val foodClassifier: FoodClassificationService,  // Interface, not concrete
    private val foodMatchingService: FoodMatchingService,
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val calculateNutrition: CalculateNutritionUseCase,
    private val barcodeService: BarcodeService,
    private val aiCoachRepository: AICoachRepository,
    private val ocrScannerService: OCRScannerService
) : ViewModel() {
    
    companion object {
        private const val TAG = "AddMealViewModel"
    }
    
    private val _uiState = MutableStateFlow(AddMealUiState())
    val uiState: StateFlow<AddMealUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            try {
                foodMatchingService.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize food index", e)
            }
        }
    }
    
    /**
     * UNIFIED entry point for all image classification.
     * Uses food-filtered classifier - non-food labels are rejected upstream.
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClassifying = true, error = null, capturedBitmap = bitmap) }
            
            try {
                // Step 1: Run FOOD-SPECIFIC classification (filters non-food labels)
                val classificationResult = foodClassifier.classifyFood(bitmap)
                
                Log.d(TAG, "Classification status: ${classificationResult.status}")
                Log.d(TAG, "Raw labels (debug): ${classificationResult.rawLabels}")
                
                // Step 2: Handle based on classification status
                when (classificationResult.status) {
                    ClassificationStatus.NO_FOOD_DETECTED -> {
                        handleNoFoodDetected(classificationResult.rawLabels)
                    }
                    ClassificationStatus.ERROR -> {
                        showError("Classification failed. Please try again.")
                    }
                    else -> {
                        // Food was detected - proceed to database matching
                        handleFoodDetected(classificationResult)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Classification error", e)
                _uiState.update { state ->
                    state.copy(
                        isClassifying = false,
                        error = "Classification failed. Try again or search manually."
                    )
                }
            }
        }
    }
    
    /**
     * Handle when ML classifier found NO food-related labels.
     * This is a critical case - don't guess, ask user to search manually.
     */
    private fun handleNoFoodDetected(rawLabels: List<String>) {
        Log.w(TAG, "No food detected. Raw labels were: $rawLabels")
        
        val message = if (rawLabels.isNotEmpty()) {
            "Couldn't identify food (detected: ${rawLabels.take(2).joinToString()}). Please search manually."
        } else {
            "Couldn't identify food in image. Please search manually or try a clearer photo."
        }
        
        _uiState.update { state ->
            state.copy(
                isClassifying = false,
                showManualSearch = true,
                showConfirmation = false,
                showCandidateSelection = false,
                error = message
            )
        }
    }
    
    /**
     * Handle when food WAS detected by the classifier.
     * Always show candidates — never auto-select, because the model
     * may misidentify foods. The user should always confirm.
     */
    private suspend fun handleFoodDetected(classification: FoodClassificationResult) {
        val mlResults = classification.results
        
        // Re-initialize index to pick up any DB changes (handles seeding race condition)
        try {
            foodMatchingService.initialize()
            // If index is empty, DB might still be seeding — wait and retry once
            if (!foodMatchingService.isReady()) {
                Log.w(TAG, "Food index empty — DB may still be seeding. Retrying in 1s...")
                kotlinx.coroutines.delay(1000)
                foodMatchingService.initialize()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize food index", e)
        }
        
        // Match ML results against food database
        val matchResults = foodMatchingService.matchClassifications(mlResults)
        val candidates = foodMatchingService.getValidCandidates(matchResults)
        
        Log.d(TAG, "Match results: ${matchResults.map { "${it.mlLabel} → ${it.matchedFood?.name ?: "NO MATCH"} (${it.matchType})" }}")
        Log.d(TAG, "Valid candidates: ${candidates.size}")
        
        when {
            // Has candidates → always show selection list (user picks)
            candidates.isNotEmpty() -> {
                Log.d(TAG, "Showing ${candidates.size} candidates for user selection")
                showCandidateSelection(candidates, mlResults)
            }
            
            // No database matches → manual search with top ML predictions as context
            else -> {
                val topPredictions = mlResults.take(3).joinToString(", ") {
                    "${it.label} (${it.confidencePercent}%)"
                }
                val hint = mlResults.firstOrNull()?.label ?: ""
                val message = if (topPredictions.isNotBlank()) {
                    "AI predictions: $topPredictions — but no match in database. Please search manually."
                } else {
                    "Couldn't match to database. Please search manually."
                }
                showManualSearchWithHint(hint, message)
            }
        }
    }
    
    /**
     * Auto-select a food when confidence is high and match is safe.
     */
    private fun autoSelectFood(match: FoodMatchResult) {
        val food = match.matchedFood!!
        val nutrition = calculateNutrition(food, _uiState.value.portionGrams)
        
        _uiState.update { state ->
            state.copy(
                isClassifying = false,
                selectedFood = food,
                mlLabel = match.mlLabel,
                calculatedNutrition = nutrition,
                showConfirmation = true,
                showCandidateSelection = false,
                showManualSearch = false,
                matchResults = listOf(match)
            )
        }
    }
    
    /**
     * Show candidate selection for multiple matches or medium confidence.
     */
    private fun showCandidateSelection(
        candidates: List<FoodMatchResult>,
        mlResults: List<ClassificationResult>
    ) {
        _uiState.update { state ->
            state.copy(
                isClassifying = false,
                matchResults = candidates,
                classificationResults = mlResults,
                showCandidateSelection = true,
                showConfirmation = false,
                showManualSearch = false,
                showCamera = false,
                showGallery = false,
                error = null
            )
        }
    }
    
    /**
     * Show manual search with hint from ML.
     */
    private fun showManualSearchWithHint(hint: String, message: String? = null) {
        _uiState.update { state ->
            state.copy(
                isClassifying = false,
                showManualSearch = true,
                showCandidateSelection = false,
                showConfirmation = false,
                searchHint = hint,
                error = message
            )
        }
    }
    
    /**
     * Show error state.
     */
    private fun showError(message: String) {
        _uiState.update { state ->
            state.copy(
                isClassifying = false,
                error = message
            )
        }
    }
    
    /**
     * User selects a food from candidate list.
     */
    fun selectFromCandidates(match: FoodMatchResult) {
        match.matchedFood?.let { food ->
            val nutrition = calculateNutrition(food, _uiState.value.portionGrams)
            val suggestion = aiCoachRepository.getSuggestionForFood(food)
            _uiState.update { state ->
                state.copy(
                    selectedFood = food,
                    mlLabel = match.mlLabel,
                    calculatedNutrition = nutrition,
                    coachSuggestion = suggestion,
                    showConfirmation = true,
                    showCandidateSelection = false
                )
            }
        }
    }
    
    /**
     * User selects a food item (from search).
     */
    fun selectFood(food: FoodItem) {
        _uiState.update { state ->
            val nutrition = calculateNutrition(food, state.portionGrams)
            val suggestion = aiCoachRepository.getSuggestionForFood(food)
            state.copy(
                selectedFood = food,
                calculatedNutrition = nutrition,
                coachSuggestion = suggestion,
                showConfirmation = true,
                showCandidateSelection = false,
                showManualSearch = false
            )
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
     * If a photo was captured, saves it to internal storage first.
     */
    fun confirmMeal() {
        val food = _uiState.value.selectedFood ?: return
        val grams = _uiState.value.portionGrams
        val source = when {
            _uiState.value.matchResults.isNotEmpty() -> "ml"
            else -> "manual"
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLogging = true) }
            
            try {
                // Save captured photo to internal storage (if available)
                val imagePath = _uiState.value.capturedBitmap?.let { bitmap ->
                    MealImageStorage.saveMealImage(appContext, bitmap)
                }
                
                mealRepository.logMeal(food, grams, source, imagePath)
                _uiState.update { it.copy(isLogging = false, mealLogged = true, capturedBitmap = null) }
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
    fun showManualSearch(query: String? = null) {
        _uiState.update { it.copy(
            showManualSearch = true,
            showCandidateSelection = false,
            showConfirmation = false,
            showCamera = false,
            showGallery = false,
            showBarcode = false,
            showOCRScanner = false,
            searchHint = query ?: it.searchHint
        ) }
    }
    
    /**
     * Show camera capture screen.
     */
    fun showCamera() {
        _uiState.update { it.copy(
            showCamera = true,
            showGallery = false,
            showManualSearch = false,
            showCandidateSelection = false,
            showConfirmation = false,
            showBarcode = false,
            showOCRScanner = false
        ) }
    }
    
    /**
     * Show gallery picker.
     */
    fun showGallery() {
        _uiState.update { it.copy(
            showGallery = true,
            showCamera = false,
            showManualSearch = false,
            showCandidateSelection = false,
            showConfirmation = false,
            showBarcode = false,
            showOCRScanner = false
        ) }
    }
    
    /**
     * "Not this food?" — re-show candidates if available, else manual search.
     */
    fun showCandidatesFromConfirmation() {
        val currentState = _uiState.value
        if (currentState.matchResults.size > 1) {
            // Show remaining candidates (excluding the one already selected)
            _uiState.update { it.copy(
                showCandidateSelection = true,
                showConfirmation = false,
                showManualSearch = false,
                showCamera = false,
                showGallery = false,
                selectedFood = null
            ) }
        } else {
            // No other candidates — go to manual search
            showManualSearch()
        }
    }
    
    /**
     * Search for foods manually.
     */
    fun searchFoods(query: String) = foodRepository.searchFoods(query)
    
    // ============ BARCODE SCANNING ============
    
    /**
     * Show barcode scanner.
     */
    fun showBarcodeScanner() {
        _uiState.update { it.copy(
            showBarcode = true,
            showCamera = false,
            showGallery = false,
            showManualSearch = false,
            showCandidateSelection = false,
            showConfirmation = false,
            showOCRScanner = false
        ) }
    }
    
    /**
     * Handle a scanned barcode — look up via OpenFoodFacts.
     */
    fun handleBarcodeResult(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningBarcode = true, error = null) }
            
            when (val result = barcodeService.lookupBarcode(barcode)) {
                is BarcodeResult.Found -> {
                    val food = result.food
                    val nutrition = calculateNutrition(food, _uiState.value.portionGrams)
                    val suggestion = aiCoachRepository.getSuggestionForFood(food)
                    _uiState.update { it.copy(
                        selectedFood = food,
                        calculatedNutrition = nutrition,
                        coachSuggestion = suggestion,
                        mlLabel = "\uD83D\uDCE6 Barcode: $barcode",
                        showConfirmation = true,
                        showBarcode = false,
                        isScanningBarcode = false
                    ) }
                }
                is BarcodeResult.NotFound -> {
                    // Product not in OpenFoodFacts — let user search by name
                    _uiState.update { it.copy(
                        showManualSearch = true,
                        showBarcode = false,
                        isScanningBarcode = false,
                        searchHint = "",
                        error = "Barcode $barcode not found in OpenFoodFacts. Search by product name instead."
                    ) }
                }
                is BarcodeResult.Error -> {
                    // Network/API error — stay on barcode screen so user sees the error
                    _uiState.update { it.copy(
                        isScanningBarcode = false,
                        error = result.message
                    ) }
                }
            }
        }
    }
    
    // ============ OCR LABEL SCANNING ============
    
    /**
     * Show OCR scanner (re-uses camera or gallery picker).
     */
    fun showOCRScanner() {
        _uiState.update { it.copy(
            showOCRScanner = true,
            showCamera = false,
            showGallery = false,
            showManualSearch = false,
            showCandidateSelection = false,
            showConfirmation = false,
            showBarcode = false
        ) }
    }
    
    /**
     * Process an image of a nutrition label via OCR.
     * Extracts calories, protein, carbs, fat from the label text.
     */
    fun processOCRLabel(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOCR = true, error = null, capturedBitmap = bitmap) }
            
            try {
                val result = ocrScannerService.extractNutrition(bitmap)
                
                if (!result.hasAnyData) {
                    _uiState.update { it.copy(
                        isProcessingOCR = false,
                        error = "Could not find nutrition info in this image. Try a clearer photo of the label."
                    ) }
                    return@launch
                }
                
                // Create a FoodItem from OCR data
                val food = com.nutriscan.data.local.entity.FoodItem(
                    name = "Scanned Label",
                    kcalPer100g = result.calories ?: 0,
                    proteinPer100g = result.protein ?: 0f,
                    carbsPer100g = result.carbs ?: 0f,
                    fatPer100g = result.fat ?: 0f,
                    tags = "ocr,scanned"
                )
                
                val nutrition = calculateNutrition(food, _uiState.value.portionGrams)
                val suggestion = aiCoachRepository.getSuggestionForFood(food)
                
                _uiState.update { it.copy(
                    isProcessingOCR = false,
                    selectedFood = food,
                    calculatedNutrition = nutrition,
                    coachSuggestion = suggestion,
                    mlLabel = "📋 Scanned Label" + (result.servingSize?.let { " ($it)" } ?: ""),
                    showConfirmation = true,
                    showOCRScanner = false
                ) }
                
            } catch (e: Exception) {
                Log.e(TAG, "OCR processing failed", e)
                _uiState.update { it.copy(
                    isProcessingOCR = false,
                    error = "Label scan failed: ${e.localizedMessage}"
                ) }
            }
        }
    }
}

/**
 * UI State for Add Meal screen.
 */
data class AddMealUiState(
    // Loading states
    val isClassifying: Boolean = false,
    val isLogging: Boolean = false,
    
    // ML results
    val classificationResults: List<ClassificationResult> = emptyList(),
    val matchResults: List<FoodMatchResult> = emptyList(),
    val mlLabel: String? = null,
    
    // Selected food and nutrition
    val selectedFood: FoodItem? = null,
    val portionGrams: Int = 250,
    val calculatedNutrition: NutritionResult = NutritionResult.ZERO,
    
    // Captured photo for food diary
    val capturedBitmap: Bitmap? = null,
    
    // Screen states (mutually exclusive)
    val showConfirmation: Boolean = false,
    val showCandidateSelection: Boolean = false,
    val showManualSearch: Boolean = false,
    val showCamera: Boolean = false,
    val showGallery: Boolean = false,
    val showBarcode: Boolean = false,
    val isScanningBarcode: Boolean = false,
    val showOCRScanner: Boolean = false,
    val isProcessingOCR: Boolean = false,
    
    // Smart Switch Suggestion
    val coachSuggestion: CoachInsight? = null,
    
    // Other
    val searchHint: String = "",
    val mealLogged: Boolean = false,
    val error: String? = null
)
