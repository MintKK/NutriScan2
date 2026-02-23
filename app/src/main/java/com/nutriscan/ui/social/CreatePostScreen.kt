package com.nutriscan.ui.social

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriscan.domain.model.PortionPreset
import com.nutriscan.ui.common.ImagePickerResult
import com.nutriscan.ui.common.GalleryImagePicker
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nutriscan.ui.addmeal.CandidateSelectionSheet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    viewModel: CreatePostViewModel = hiltViewModel()
) {
    // input fields
    var caption by remember { mutableStateOf("") }
    var foodName by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    // get current user ID from SocialViewModel
    // to tell if this user profile is their own profile
    val socialViewModel: SocialViewModel = hiltViewModel()
    val currentUserID by socialViewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var showImagePicker by remember { mutableStateOf(false) }

    val isUploading by viewModel.isLoading.collectAsState()
    val postError by viewModel.error.collectAsState()
    val isPostCreated by viewModel.isPostCreated.collectAsState()
    
    var customGrams by remember { mutableStateOf("") }
    var isCustomPortionSelected by remember { mutableStateOf(false) }

    val isClassifying by viewModel.isClassifying.collectAsState()
    val matchResults by viewModel.matchResults.collectAsState()
    val selectedFood by viewModel.selectedFood.collectAsState()
    val calculatedNutrition by viewModel.calculatedNutrition.collectAsState()
    val portionGrams by viewModel.portionGrams.collectAsState()
    
    // uri for camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.cacheDir, "reports")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    // camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            imageUri = tempCameraUri
            // load bitmap from uri
            val bitmap = try {
                if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, tempCameraUri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, tempCameraUri!!)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
            bitmap?.let { viewModel.classifyImage(it, tempCameraUri) }
        }
    }

    // image launcher (gallery) 
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            val bitmap = try {
                if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
            bitmap?.let { viewModel.classifyImage(it, uri) }
        }
    }

    LaunchedEffect(postError) {
        if (postError != null) {
            error = postError
            viewModel.resetState()
        }
    }

    LaunchedEffect(isPostCreated) {
        if (isPostCreated) {
            onBack()
        }
    }

//    LaunchedEffect(Unit) {
//        try {
//            isLoading = true
//
//            val inputStream = context.assets.open("sample_images/apple.png")
//            val file = File(context.cacheDir, "temp_apple_${System.currentTimeMillis()}.png")
//            FileOutputStream(file).use { outputStream ->
//                inputStream.copyTo(outputStream)
//            }
//
//            imageUri = Uri.fromFile(file)
//            error = null
//        } catch (e: Exception) {
//            e.printStackTrace()
//            error = "Failed to load image: ${e.message}"
//
//            // fallback
//            imageUri = Uri.parse("https://via.placeholder.com/300")
//        } finally {
//            isLoading = false
//        }
//    }

    fun handleImagePickerResult() {
        galleryLauncher.launch("image/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Post"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        padding ->

        if (imageUri == null) {
            // STEP 1: Select Image
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Identify Your Food",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Choose how to add your meal picture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val cameraPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            val file = createImageFile()
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file
                            )
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            showPermissionDeniedDialog = true
                        }
                    }

                    // Take Photo option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val file = createImageFile()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                tempCameraUri = uri

                                val permissionStatus = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                )

                                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Camera",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Upload from Gallery option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { handleImagePickerResult() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Upload from Gallery",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Gallery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // STEP 2 & 3: Image chosen, candidate selection, then form
            val isFormVisible = !isClassifying && matchResults.isEmpty()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(250.dp).clickable { handleImagePickerResult() },
                    shape = RoundedCornerShape(size = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = "Selected food",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { handleImagePickerResult() },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change image"
                            )
                        }

                        if (isUploading || isClassifying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (isClassifying) "Analyzing Image..." else "Uploading...",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (isFormVisible) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Food Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    // food name
                    OutlinedTextField(
                        value = selectedFood?.matchedFood?.name ?: foodName,
                        onValueChange = { foodName = it },
                        label = { Text("Food Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = selectedFood == null
                    )

                    if (selectedFood != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Select Portion Size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(PortionPreset.entries) { preset ->
                                FilterChip(
                                    selected = portionGrams == preset.grams && !isCustomPortionSelected,
                                    onClick = {
                                        if (portionGrams == preset.grams && !isCustomPortionSelected) {
                                            isCustomPortionSelected = true
                                        } else {
                                            isCustomPortionSelected = false
                                            viewModel.setPortionGrams(preset.grams)
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
                                        customGrams.toIntOrNull()?.let { viewModel.setPortionGrams(it) }
                                    },
                                    label = { Text("Custom") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = if (isCustomPortionSelected) customGrams else portionGrams.toString(),
                            onValueChange = { 
                                if (isCustomPortionSelected) {
                                    customGrams = it
                                    it.toIntOrNull()?.let { grams -> viewModel.setPortionGrams(grams) } 
                                }
                            },
                            label = { Text("Custom (grams)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isCustomPortionSelected
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Nutrition Info", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    // MACROS (2x2 Grid)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val calText = if (selectedFood != null) "${calculatedNutrition.kcal}" else calories
                        OutlinedTextField(
                            value = calText,
                            onValueChange = { if (it.all { char -> char.isDigit() }) calories = it },
                            label = { Text("Calories (kcal)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = selectedFood == null
                        )
                        
                        val proteinText = if (selectedFood != null) "%.1f".format(calculatedNutrition.protein) else protein
                        OutlinedTextField(
                            value = proteinText,
                            onValueChange = { if (it.isEmpty() || it.toFloatOrNull() != null) protein = it },
                            label = { Text("Protein (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = selectedFood == null
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val carbsText = if (selectedFood != null) "%.1f".format(calculatedNutrition.carbs) else carbs
                        OutlinedTextField(
                            value = carbsText,
                            onValueChange = { if (it.isEmpty() || it.toFloatOrNull() != null) carbs = it },
                            label = { Text("Carbs (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = selectedFood == null
                        )
                        
                        val fatText = if (selectedFood != null) "%.1f".format(calculatedNutrition.fat) else fat
                        OutlinedTextField(
                            value = fatText,
                            onValueChange = { if (it.isEmpty() || it.toFloatOrNull() != null) fat = it },
                            label = { Text("Fat (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = selectedFood == null
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Caption", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Tell us about your meal!") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "Unknown error occurred",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val finalFoodName = selectedFood?.matchedFood?.name ?: foodName
                            val finalCalories = if (selectedFood != null) calculatedNutrition.kcal else calories.toIntOrNull()
                            val finalProtein = if (selectedFood != null) calculatedNutrition.protein else protein.toFloatOrNull()
                            val finalCarbs = if (selectedFood != null) calculatedNutrition.carbs else (carbs.toFloatOrNull() ?: 0f)
                            val finalFat = if (selectedFood != null) calculatedNutrition.fat else (fat.toFloatOrNull() ?: 0f)

                            if (finalCalories != null && finalProtein != null && imageUri != null) {
                                viewModel.createPost(
                                    caption = caption,
                                    foodName = finalFoodName,
                                    calories = finalCalories,
                                    protein = finalProtein,
                                    carbs = finalCarbs,
                                    fat = finalFat,
                                    imageUri = imageUri!!
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (
                            (selectedFood != null || (foodName.isNotEmpty() && calories.toIntOrNull() != null && protein.toFloatOrNull() != null))
                            && caption.isNotEmpty() && imageUri != null
                        ) && !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sharing...")
                        } else {
                            Text("Post")
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text("Please grant camera permission to take photos.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (matchResults.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.resetState() }
        ) {
            CandidateSelectionSheet(
                candidates = matchResults,
                onCandidateSelected = { viewModel.selectFood(it) },
                onManualSearch = { /* To do later if needed */ },
                onCancel = { viewModel.resetState() }
            )
        }
    }
}

// returns true if valid post
fun ValidatePost(
    caption: String,
    foodName: String,
    calories: Int?,
    protein: Float?,
    imageUri: Uri?
): Boolean {
    return ((caption.isNotEmpty()) && (foodName.isNotEmpty()) && (calories != null) && (protein != null) && (imageUri != null))
}
