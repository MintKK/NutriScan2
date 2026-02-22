package com.nutriscan.ui.social

import android.R
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // get current user ID from SocialViewModel
    // to tell if this user profile is their own profile
    val socialViewModel: SocialViewModel = hiltViewModel()
    val currentUserID by socialViewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var showImagePicker by remember { mutableStateOf(false) }

    val isUploading by viewModel.isLoading.collectAsState()
    val postError by viewModel.error.collectAsState()
    val isPostCreated by viewModel.isPostCreated.collectAsState()

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

    LaunchedEffect(Unit) {
        try {
            isLoading = true

            val inputStream = context.assets.open("sample_images/apple.png")
            val file = File(context.cacheDir, "temp_apple_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            imageUri = Uri.fromFile(file)
            error = null
        } catch (e: Exception) {
            e.printStackTrace()
            error = "Failed to load image: ${e.message}"

            // fallback
            imageUri = Uri.parse("https://via.placeholder.com/300")
        } finally {
            isLoading = false
        }
    }

    fun handleImagePickerResult(result: ImagePickerResult) {
        showImagePicker = false
        when (result) {
            is ImagePickerResult.Success -> {
                imageUri = result.uri
                // Optionally show a preview
            }
            is ImagePickerResult.Error -> {
                error = result.message
            }
            is ImagePickerResult.Cancelled -> {
                // User cancelled, do nothing
            }
        }
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

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Right now all the field is from input but maybe want use the scan to pre-fill
            // some of the text fields?

            when {
                isLoading -> Text("Loading image...")
                error != null -> Text(
                    text = "⚠️ $error",
                    color = MaterialTheme.colorScheme.error
                )
                imageUri != null -> Text(
                    text = "✅ Image ready",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { showImagePicker = true },
                shape = RoundedCornerShape(size = 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        // Show image preview
                        Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = "Selected food",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Overlay change button
                        IconButton(
                            onClick = { showImagePicker = true },
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
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap to select an image",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // food name
            OutlinedTextField(
                value = foodName,
                onValueChange = { foodName = it },
                label = { Text("Food Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // caption
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // calories
            OutlinedTextField(
                value = calories,
                onValueChange = {
                    newValue ->
                    if (newValue.all { it.isDigit() }) {
                        calories = newValue
                    }
                },
                label = { Text("Calories (kcal)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // protein
            OutlinedTextField(
                value = protein,
                onValueChange = {
                    newValue ->
                    if (newValue.isEmpty() || newValue.toFloatOrNull() != null) {
                        protein = newValue
                    }
                },
                label = { Text("Protein (g)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // image uri field here? //

            val caloriesValue = calories.toIntOrNull()
            val proteinValue = protein.toFloatOrNull()

            Button(onClick = {
                viewModel.viewModelScope.launch {
                    try {
                        viewModel.createPost(
                            caption = caption,
                            foodName = foodName,
                            calories = caloriesValue!!,
                            protein = proteinValue!!,
                            imageUri = imageUri!!
                        )
                    } catch (e: Exception) {
                        error = e.message
                    } finally {

                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ValidatePost(
                caption = caption,
                foodName = foodName,
                calories = caloriesValue,
                protein = proteinValue,
                imageUri = imageUri
            )  && !isUploading) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading...")
                } else {
                    Text("Post")
                }
            }
        }
    }

    if (showImagePicker) {
        GalleryImagePicker {
            result ->
            handleImagePickerResult(result)
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
