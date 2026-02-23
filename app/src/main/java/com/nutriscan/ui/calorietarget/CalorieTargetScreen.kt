package com.nutriscan.ui.calorietarget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Man
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.ui.analytics.AnalyticsViewModel
import kotlin.math.roundToInt

enum class Gender { Male, Female }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieTargetScreen(
    onBack: () -> Unit,
    viewModel: CalorieTargetViewModel = hiltViewModel()
) {


    var targetSaved by remember { mutableStateOf(false) }

    val isFemaleRepo by viewModel.getIsFemale.collectAsState()
    val weight by viewModel.getWeight.collectAsState()
    val height by viewModel.getHeight.collectAsState()
    val age by viewModel.getAge.collectAsState()

    var isFemale by remember { mutableStateOf(false) }
    var weightString by remember { mutableStateOf("") }
    var heightString by remember { mutableStateOf("") }
    var ageString by remember { mutableStateOf("") }

    val targetCaloriesRepo by viewModel.getTargetCalories.collectAsState()
    val targetProteinRepo by viewModel.getTargetProtein.collectAsState()
    val targetCarbsRepo by viewModel.getTargetCarbs.collectAsState()
    val targetFatRepo by viewModel.getTargetFat.collectAsState()

    var customCaloriesInput by remember { mutableStateOf("") }
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }

    val recommendedCalories by remember(weightString, heightString, ageString, isFemale) {
        derivedStateOf {
            CalculateBMICalorie(!isFemale, weightString, heightString, ageString)
        }
    }

    // Initialize inputs from repository when they are loaded (and only once or when saved)
    LaunchedEffect(targetCaloriesRepo, targetProteinRepo, targetCarbsRepo, targetFatRepo) {
        if (customCaloriesInput.isEmpty() && targetCaloriesRepo > 0) {
            customCaloriesInput = targetCaloriesRepo.toString()
        }
        if (proteinInput.isEmpty() && targetProteinRepo > 0) {
            proteinInput = targetProteinRepo.roundToInt().toString()
        }
        if (carbsInput.isEmpty() && targetCarbsRepo > 0) {
            carbsInput = targetCarbsRepo.roundToInt().toString()
        }
        if (fatInput.isEmpty() && targetFatRepo > 0) {
            fatInput = targetFatRepo.roundToInt().toString()
        }
    }

    // When parameters change, we can update the "recommended" UI but maybe don't overwrite custom immediately
    // unless the user wants to "Reset to Recommended"
    
    LaunchedEffect(isFemaleRepo, weight, height, age) {
        isFemale = isFemaleRepo
        weightString = weight.toString()
        heightString = height.toString()
        ageString = age.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Calorie Target") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Info Section
            Text(
                "Personal Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ){
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isFemale) Icons.Default.Female else Icons.Default.Male,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Gender", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = isFemale,
                                    onCheckedChange = { isFemale = it; targetSaved = false },
                                    scale = 0.8f
                                )
                            }
                            Text(if (isFemale) "Female" else "Male", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    DisplayInputSmall("Weight (kg)", weightString, { weightString = it; targetSaved = false }, Icons.Default.FitnessCenter)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    DisplayInputSmall("Height (cm)", heightString, { heightString = it; targetSaved = false }, Icons.Default.Man)
                }

                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    DisplayInputSmall("Age", ageString, { ageString = it; targetSaved = false }, Icons.Default.CalendarMonth)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Calorie Target Section
            Text(
                "Daily Calorie Target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customCaloriesInput,
                        onValueChange = { input ->
                            customCaloriesInput = input.filter { it.isDigit() }
                            targetSaved = false
                        },
                        label = { Text("Daily Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Macro Targets Section
            Text(
                "Macronutrient Targets (grams)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroInputField(
                    modifier = Modifier.weight(1f),
                    label = "Protein",
                    value = proteinInput,
                    onValueChange = { proteinInput = it; targetSaved = false },
                    color = Color(0xFFE3F2FD) // Light Blue
                )
                MacroInputField(
                    modifier = Modifier.weight(1f),
                    label = "Carbs",
                    value = carbsInput,
                    onValueChange = { carbsInput = it; targetSaved = false },
                    color = Color(0xFFFFF3E0) // Light Orange
                )
                MacroInputField(
                    modifier = Modifier.weight(1f),
                    label = "Fat",
                    value = fatInput,
                    onValueChange = { fatInput = it; targetSaved = false },
                    color = Color(0xFFFFEBEE) // Light Red
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val cal = customCaloriesInput.toIntOrNull() ?: recommendedCalories.toInt()
                    val prot = proteinInput.toIntOrNull() ?: 0
                    val carb = carbsInput.toIntOrNull() ?: 0
                    val fat = fatInput.toIntOrNull() ?: 0
                    
                    viewModel.setCalorieTarget(cal)
                    viewModel.setTargetMacros(prot, carb, fat)
                    viewModel.setIsFemale(isFemale)
                    weightString.toIntOrNull()?.let { viewModel.setWeight(it) }
                    heightString.toIntOrNull()?.let { viewModel.setHeight(it) }
                    ageString.toIntOrNull()?.let { viewModel.setAge(it) }
                    
                    targetSaved = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !targetSaved
            ) {
                Text(if (targetSaved) "Target Saved ✅" else "Save All Changes", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DisplayInputSmall(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() }) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun MacroInputField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { it.isDigit() }) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// Utility extension for Switch scale
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    enabled: Boolean = true
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(scale),
        enabled = enabled
    )
}


fun CalculateBMICalorie(isMale:Boolean, weightStr:String, heightStr:String, ageStr:String) : String {

    val weight: Int = weightStr.toIntOrNull() ?: 0
    val height: Int = heightStr.toIntOrNull() ?: 0
    val age: Int = ageStr.toIntOrNull() ?: 0

    // Harris-Benedict
    val out: Double
    if (isMale)
        out = 66.47 + (13.75 * weight) + (5.003 * height) - (6.775 * age)
    else
        out = 655.1 + (9.563 * weight) + (1.850 * height) - (4.676 * age)

    return out.roundToInt().toString()
}