package com.nutriscan.ui.addmeal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.entity.FoodItem
import com.nutriscan.data.repository.CoachInsight
import com.nutriscan.data.repository.InsightType
import com.nutriscan.domain.model.PortionPreset
import com.nutriscan.ml.FoodMatchResult
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    initialSearchQuery: String? = null,
    onMealLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddMealViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle initial search query if provided (e.g. from AI Coach swap suggestion)
    LaunchedEffect(initialSearchQuery) {
        if (!initialSearchQuery.isNullOrBlank()) {
            viewModel.showManualSearch(initialSearchQuery)
        }
    }
    
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
                        coachSuggestion = uiState.coachSuggestion,
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
                uiState.showCamera -> {
                    CameraCapture(
                        isClassifying = uiState.isClassifying,
                        error = uiState.error,
                        onImageCaptured = { bitmap -> viewModel.classifyImage(bitmap) },
                        onManualSearch = { viewModel.showManualSearch() }
                    )
                }
                uiState.showGallery -> {
                    GalleryPicker(
                        onImagePicked = { bitmap -> viewModel.classifyImage(bitmap) },
                        onDismiss = { viewModel.resetState() }
                    )
                }
                uiState.showBarcode -> {
                    BarcodeScannerLauncher(
                        isLoading = uiState.isScanningBarcode,
                        error = uiState.error,
                        onBarcodeScanned = { barcode -> viewModel.handleBarcodeResult(barcode) },
                        onCancel = { viewModel.resetState() }
                    )
                }
                uiState.showOCRScanner -> {
                    OCRLabelScanner(
                        isProcessing = uiState.isProcessingOCR,
                        error = uiState.error,
                        onImageCaptured = { bitmap -> viewModel.processOCRLabel(bitmap) },
                        onCancel = { viewModel.resetState() }
                    )
                }
                else -> {
                    // Landing screen: choose input method
                    UnifiedFoodCapture(
                        isClassifying = uiState.isClassifying,
                        error = uiState.error,
                        onImageCaptured = { bitmap -> viewModel.classifyImage(bitmap) },
                        onManualSearch = { viewModel.showManualSearch() },
                        onShowCamera = { viewModel.showCamera() },
                        onShowGallery = { viewModel.showGallery() },
                        onShowBarcode = { viewModel.showBarcodeScanner() },
                        onShowOCR = { viewModel.showOCRScanner() }
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryPicker(
    onImagePicked: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // launcher for photo picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // convert URI to bitmap
            val bitmap = try {
                if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        // optional part: scale down if image is massive to prevent out-of-memory
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }

            bitmap?.let { onImagePicked(it) }
        } else {
            // user cancelled
            onDismiss()
        }
    }

    // launch immediately when this composable enters composition
    LaunchedEffect(Unit) {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // show a placeholder or loading screen while the system picker is open
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "Opening Gallery...",
            modifier = Modifier.padding(top = 64.dp)
        )
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

    // state to track if permission is granted to use the camera
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // launcher to request permission
    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { hasCameraPermission = it }
    )

    // launch permission request when this screen is shown
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
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
    coachSuggestion: CoachInsight? = null,
    onPortionChange: (Int) -> Unit,
    onPresetSelect: (PortionPreset) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onNotCorrect: () -> Unit = {}  // For "Not this food?" option
) {
    var customGrams by remember { mutableStateOf(if (PortionPreset.entries.any { it.grams == portionGrams }) "" else portionGrams.toString()) }
    var isCustomPortionSelected by remember { mutableStateOf(PortionPreset.entries.none { it.grams == portionGrams }) }
    
    // Validation: either a preset is selected (via portionGrams matching a preset) 
    // or custom is selected and customGrams is a valid positive number.
    val isPortionValid = if (!isCustomPortionSelected) {
        PortionPreset.entries.any { it.grams == portionGrams }
    } else {
        customGrams.toIntOrNull()?.let { it > 0 } ?: false
    }
    
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
                    selected = portionGrams == preset.grams && !isCustomPortionSelected,
                    onClick = { 
                        if (portionGrams == preset.grams && !isCustomPortionSelected) {
                            isCustomPortionSelected = true
                        } else {
                            isCustomPortionSelected = false
                            onPresetSelect(preset) 
                        }
                    },
                    label = { Text(preset.displayName) }
                )
            }
            
            item {
                FilterChip(
                    selected = isCustomPortionSelected,
                    onClick = {
                        isCustomPortionSelected = true
                        customGrams.toIntOrNull()?.let { onPortionChange(it) }
                    },
                    label = { Text("Custom") }
                )
            }
        }
        
        // Custom grams input
        OutlinedTextField(
            value = if (isCustomPortionSelected) customGrams else portionGrams.toString(),
            onValueChange = { 
                if (isCustomPortionSelected) {
                    // Force numbers only
                    val filtered = it.filter { char -> char.isDigit() }
                    customGrams = filtered
                    filtered.toIntOrNull()?.let { grams -> onPortionChange(grams) }
                }
            },
            label = { Text("Custom (grams)") },
            isError = isCustomPortionSelected && !isPortionValid,
            supportingText = if (isCustomPortionSelected && !isPortionValid) {
                { Text("Enter a valid weight in grams") }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = isCustomPortionSelected
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
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Smart Switch Suggestion
        if (coachSuggestion != null) {
            val bubbleColor = when (coachSuggestion.type) {
                InsightType.TIP -> Color(0xFFE3F2FD)
                InsightType.WARNING -> Color(0xFFFFF3E0)
                InsightType.SUCCESS -> Color(0xFFE8F5E9)
                InsightType.INFO -> Color(0xFFF3E5F5)
            }
            val accentColor = when (coachSuggestion.type) {
                InsightType.TIP -> Color(0xFF2196F3)
                InsightType.WARNING -> Color(0xFFFF9800)
                InsightType.SUCCESS -> Color(0xFF4CAF50)
                InsightType.INFO -> Color(0xFF9C27B0)
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(coachSuggestion.emoji, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Coach's Tip",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            coachSuggestion.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                enabled = !isLogging && isPortionValid
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
    onManualSearch: () -> Unit,
    onShowCamera: () -> Unit,
    onShowGallery: () -> Unit,
    onShowBarcode: () -> Unit,
    onShowOCR: () -> Unit
) {
    val context = LocalContext.current
    
    // Sample images - labels are for display only, NOT for matching
    val sampleImages = remember {
        listOf(
            SampleFoodImage("sample_images/Carbonara.png", "Sample 1"),
            SampleFoodImage("sample_images/fried_rice.png", "Sample 2"),
            SampleFoodImage("sample_images/curry_chicken.png", "Sample 3"),
            SampleFoodImage("sample_images/cheesecake.png", "Sample 4")
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
                    "Choose how to add your meal",
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
        
        // === INPUT METHOD OPTIONS ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Take Photo option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isClassifying) { onShowCamera() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Camera",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Upload from Gallery option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isClassifying) { onShowGallery() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Upload from Gallery",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Search Manually option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isClassifying) { onManualSearch() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search Manually",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Search",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Scan Barcode option
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isClassifying) { onShowBarcode() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Barcode",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "Barcode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // OCR Label Scanner Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isClassifying) { onShowOCR() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = "Scan Label",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Scan Nutrition Label",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Auto-fill calories & macros from a photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // === SAMPLE IMAGES SECTION ===
        Text(
            "Or try a sample image",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Sample images grid - tapping sends to ML
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
    onCancel: () -> Unit,
    showSearchButton: Boolean = true
) {
    val bestConfidence = candidates.firstOrNull()?.confidence ?: 0f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            
            if (showSearchButton) {
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
}

/**
 * Launches the Google Code Scanner and handles the result.
 * Also supports scanning barcodes from gallery images via ML Kit.
 */
@Composable
fun BarcodeScannerLauncher(
    isLoading: Boolean,
    error: String?,
    onBarcodeScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var scannerLaunched by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(true) } // Start with options
    var galleryError by remember { mutableStateOf<String?>(null) }
    var isDecodingImage by remember { mutableStateOf(false) }

    // Gallery picker for barcode images
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isDecodingImage = true
            galleryError = null
            try {
                val inputImage = com.google.mlkit.vision.common.InputImage
                    .fromFilePath(context, uri)
                val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        isDecodingImage = false
                        val barcode = barcodes.firstOrNull()?.rawValue
                        if (barcode != null) {
                            onBarcodeScanned(barcode)
                        } else {
                            galleryError = "No barcode found in this image. Try a clearer photo."
                        }
                    }
                    .addOnFailureListener { e ->
                        isDecodingImage = false
                        galleryError = "Failed to scan image: ${e.localizedMessage}"
                    }
            } catch (e: Exception) {
                isDecodingImage = false
                galleryError = "Could not load image: ${e.localizedMessage}"
            }
        }
    }

    // Live scanner auto-launch removed - user must choose Camera or Gallery first

    // UI: Loading / Error / Options
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Looking up product...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Searching OpenFoodFacts database",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                isDecodingImage -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Scanning image for barcode...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                error != null -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = onCancel) {
                        Text("Go Back")
                    }
                }
                showOptions -> {
                    // User cancelled live scanner or it failed — show retry options
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )

                    Text(
                        "Scan a Barcode",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // OpenFoodFacts Disclaimer
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Only products found in the OpenFoodFacts database will return results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (galleryError != null) {
                        Text(
                            galleryError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }


                    // Live scanner button
                    Button(
                        onClick = {
                            val scanner = GmsBarcodeScanning.getClient(context)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let { onBarcodeScanned(it) }
                                }
                                .addOnCanceledListener { /* stay on options */ }
                                .addOnFailureListener { /* stay on options */ }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan with Camera")
                    }

                    // Gallery scanner button
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan from Photo")
                    }

                    // Cancel button
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                else -> {
                    // Initial state while live scanner is launching
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Opening barcode scanner...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * OCR Label Scanner: captures or picks an image of a nutrition label,
 * then sends it to the ViewModel for OCR processing.
 */
@Composable
fun OCRLabelScanner(
    isProcessing: Boolean,
    error: String?,
    onImageCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var galleryError by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        CameraCapture(
            isClassifying = isProcessing,
            error = error,
            onImageCaptured = onImageCaptured,
            onManualSearch = { showCamera = false } // Use search button as back/cancel in this context
        )
        return
    }

    // Gallery picker for label images
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, uri)
                    ) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                onImageCaptured(bitmap)
            } catch (e: Exception) {
                galleryError = "Could not load image: ${e.localizedMessage}"
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Reading nutrition label...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Extracting calories, protein, carbs & fat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error != null -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { galleryLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Try Again")
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                galleryError != null -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        galleryError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            galleryError = null
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Text("Try Again")
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                else -> {
                    // Main options screen
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Scan Nutrition Label",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Take a photo or pick an image of a nutrition facts label to auto-fill calorie and macro data.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scan with Camera
                    Button(
                        onClick = { showCamera = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan with Camera")
                    }

                    // Pick from gallery
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }

                    // Cancel
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
