package com.nutriscan.ui.addmeal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.domain.model.PortionPreset
import com.nutriscan.ml.FoodMatchResult
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    onMealLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddMealViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle meal logged
    LaunchedEffect(uiState.mealLogged) {
        if (uiState.mealLogged) {
            onMealLogged()
            viewModel.resetState()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Meal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.showConfirmation && uiState.selectedFood != null -> {
                    ConfirmationSheet(
                        food = uiState.selectedFood!!,
                        mlLabel = uiState.mlLabel,
                        mlConfidence = uiState.classificationResults.firstOrNull()?.confidence,
                        portionGrams = uiState.portionGrams,
                        nutrition = uiState.calculatedNutrition,
                        isLogging = uiState.isLogging,
                        onPortionChange = { viewModel.setPortionGrams(it) },
                        onPresetSelect = { viewModel.setPortionPreset(it) },
                        onConfirm = { viewModel.confirmMeal() },
                        onCancel = { viewModel.resetState() },
                        onNotCorrect = { viewModel.showCandidatesFromConfirmation() }
                    )
                }
                uiState.showCandidateSelection -> {
                    CandidateSelectionSheet(
                        candidates = uiState.matchResults,
                        onCandidateSelected = { viewModel.selectFromCandidates(it) },
                        onManualSearch = { viewModel.showManualSearch() },
                        onCancel = { viewModel.resetState() }
                    )
                }
                uiState.showManualSearch -> {
                    ManualSearchScreen(
                        viewModel = viewModel,
                        searchHint = uiState.searchHint,
                        onFoodSelected = { viewModel.selectFood(it) },
                        onCancel = { viewModel.resetState() }
                    )
                }
                else -> {
                    // Unified capture: passes bitmap to ML, not hardcoded names
                    UnifiedFoodCapture(
                        isClassifying = uiState.isClassifying,
                        error = uiState.error,
                        onImageCaptured = { bitmap -> viewModel.classifyImage(bitmap) },
                        onManualSearch = { viewModel.showManualSearch() }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraCapture(
    isClassifying: Boolean,
    error: String?,
    onImageCaptured: (Bitmap) -> Unit,
    onManualSearch: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    // Setup camera
    LaunchedEffect(previewView) {
        previewView?.let { preview ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("Camera", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlay UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    "Point camera at your food",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White
                )
            }
            
            // Bottom section - capture button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                error?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Manual search button
                    OutlinedButton(onClick = onManualSearch) {
                        Icon(Icons.Default.Search, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search")
                    }
                    
                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            previewView?.bitmap?.let { bitmap ->
                                onImageCaptured(bitmap)
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isClassifying) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.width(100.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    food: FoodItem,
    mlLabel: String?,
    mlConfidence: Float? = null,
    portionGrams: Int,
    nutrition: com.nutriscan.domain.model.NutritionResult,
    isLogging: Boolean,
    onPortionChange: (Int) -> Unit,
    onPresetSelect: (PortionPreset) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNotCorrect: () -> Unit = {}  // For "Not this food?" option
) {
    var customGrams by remember { mutableStateOf(portionGrams.toString()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (mlLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Detected: $mlLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        if (mlConfidence != null) {
                            val confidencePercent = (mlConfidence * 100).toInt()
                            val confidenceColor = when {
                                confidencePercent >= 70 -> Color(0xFF4CAF50)
                                confidencePercent >= 40 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = confidenceColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "$confidencePercent%",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = confidenceColor
                                )
                            }
                        }
                    }
                }
                Text(
                    food.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Low confidence warning
        if (mlConfidence != null && mlConfidence < 0.6f) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Low confidence — please verify this is the correct food",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
        
        // Portion Presets
        Text("Select Portion Size", fontWeight = FontWeight.Medium)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PortionPreset.entries) { preset ->
                FilterChip(
                    selected = portionGrams == preset.grams,
                    onClick = { onPresetSelect(preset) },
                    label = { Text(preset.displayName) }
                )
            }
        }
        
        // Custom grams input
        OutlinedTextField(
            value = customGrams,
            onValueChange = { 
                customGrams = it
                it.toIntOrNull()?.let { grams -> onPortionChange(grams) }
            },
            label = { Text("Custom (grams)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        // Nutrition Summary
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Nutrition for ${portionGrams}g",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NutrientColumn("Calories", "${nutrition.kcal}", "kcal")
                    NutrientColumn("Protein", "%.1f".format(nutrition.protein), "g")
                    NutrientColumn("Carbs", "%.1f".format(nutrition.carbs), "g")
                    NutrientColumn("Fat", "%.1f".format(nutrition.fat), "g")
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = !isLogging
            ) {
                if (isLogging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Meal")
                }
            }
        }
        
        // "Not correct?" link for when ML picked wrong food
        if (mlLabel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onNotCorrect,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Not this food? See other suggestions")
            }
        }
    }
}

@Composable
fun NutrientColumn(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$label ($unit)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchScreen(
    viewModel: AddMealViewModel,
    searchHint: String = "",
    onFoodSelected: (FoodItem) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(searchHint) }
    val searchResults by viewModel.searchFoods(searchQuery).collectAsState(initial = emptyList())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search foods...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Results
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { food ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFoodSelected(food) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                food.name.replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${food.kcalPer100g} kcal/100g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Data class for sample food images.
 * Note: displayName is for UI only - NO hardcoded food matching.
 */
data class SampleFoodImage(
    val assetPath: String,
    val displayName: String  // For display only, not for food matching
)

/**
 * Unified food capture: sample images + camera (future).
 * ALL images go through ML classification - no hardcoded food names.
 */
@Composable
fun UnifiedFoodCapture(
    isClassifying: Boolean,
    error: String?,
    onImageCaptured: (Bitmap) -> Unit,
    onManualSearch: () -> Unit
) {
    val context = LocalContext.current
    
    // Sample images - labels are for display only, NOT for matching
    val sampleImages = remember {
        listOf(
            SampleFoodImage("sample_images/banana.png", "Sample 1"),
            SampleFoodImage("sample_images/fried_rice.png", "Sample 2"),
            SampleFoodImage("sample_images/pizza.png", "Sample 3"),
            SampleFoodImage("sample_images/hamburger.png", "Sample 4")
        )
    }
    
    // Load bitmaps from assets
    val bitmaps = remember(sampleImages) {
        sampleImages.associate { sample ->
            sample.assetPath to try {
                context.assets.open(sample.assetPath).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e("UnifiedFoodCapture", "Failed to load ${sample.assetPath}", e)
                null
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Identify Your Food",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap an image to analyze with AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message if any
        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Loading indicator
        if (isClassifying) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing with AI...")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Sample images grid - tapping sends to ML, not direct lookup
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sampleImages) { sample ->
                val bitmap = bitmaps[sample.assetPath]
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(enabled = !isClassifying && bitmap != null) {
                            // Pass bitmap to ML classification - no hardcoded names!
                            bitmap?.let { onImageCaptured(it) }
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = sample.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            // Tap indicator overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "Tap to analyze",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text("Failed to load")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Manual search button
        OutlinedButton(
            onClick = onManualSearch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Search, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search Manually Instead")
        }
    }
}

/**
 * Candidate selection sheet for when ML returns multiple possible matches.
 * Shows ranked list of food candidates with confidence % for user to choose from.
 */
@Composable
fun CandidateSelectionSheet(
    candidates: List<FoodMatchResult>,
    onCandidateSelected: (FoodMatchResult) -> Unit,
    onManualSearch: () -> Unit,
    onCancel: () -> Unit
) {
    val bestConfidence = candidates.firstOrNull()?.confidence ?: 0f
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Which food is this?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select the best match from our suggestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Low confidence warning banner
        if (bestConfidence < 0.6f) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Low confidence — the AI isn't sure. Please verify or search manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Candidates list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(candidates) { candidate ->
                val food = candidate.matchedFood ?: return@items
                val confidencePercent = (candidate.confidence * 100).toInt()
                val confidenceColor = when {
                    confidencePercent >= 70 -> Color(0xFF4CAF50)  // Green
                    confidencePercent >= 40 -> Color(0xFFFF9800)  // Amber
                    else -> Color(0xFFF44336)                     // Red
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCandidateSelected(candidate) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    food.name.replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                // Confidence badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = confidenceColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "$confidencePercent%",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = confidenceColor
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${food.kcalPer100g} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "P: ${food.proteinPer100g}g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "C: ${food.carbsPer100g}g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Select",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            OutlinedButton(
                onClick = onManualSearch,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search")
            }
        }
    }
}

