package com.nutriscan.ui.calorietarget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Man3
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

    val targetCalories by viewModel.getTargetCalories.collectAsState() // Get from repo
    val customCaloriesString by remember(weightString, heightString, ageString, isFemale) {
        derivedStateOf {
            CalculateBMICalorie(!isFemale, weightString, heightString, ageString)

            targetSaved = false
        }
    }

    var customCaloriesInput by remember { mutableStateOf("") }
    val recommendedCalories by remember(weightString, heightString, ageString, isFemale) {
        derivedStateOf {
            CalculateBMICalorie(!isFemale, weightString, heightString, ageString)
        }
    }

    LaunchedEffect(isFemaleRepo, weight, height, age) {
        isFemale = isFemaleRepo
        weightString = weight.toString()
        heightString = height.toString()
        ageString = age.toString()
    }

    // Auto calcs
//    LaunchedEffect(isFemale, weightString, heightString, ageString) {
//        customCaloriesString = CalculateBMICalorie(
//            !isFemale,
//            weightString,
//            heightString,
//            ageString
//        )
//        targetSaved = false
//    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Calorie Target") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            // Target Card
            Box() {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Gender",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Male",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                    Switch(
                                        checked = isFemale,
                                        onCheckedChange = {
                                            isFemale = it
                                        }
                                    )
                                    Text(
                                        "Female",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }

                            }
                            Icon(
                                Icons.Default.Male,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // WEIGHT
                    Card() {
                        DisplayInput(
                            Modifier,
                            weightString,
                            { weightString = it;
                                targetSaved = false },
                            "Weight (kg)",
                            Icons.Default.FitnessCenter
                        )
                    }

                    // HEIGHT
                    Card () {
                        DisplayInput(
                            Modifier,
                            heightString,
                            { heightString = it;
                                targetSaved = false },
                            "Height (cm)",
                            Icons.Default.Man
                        )
                    }

                    // AGE
                    Card() {
                        DisplayInput(
                            Modifier,
                            ageString,
                            {
                                ageString = it;
                                targetSaved = false },
                            "Age",
                            Icons.Default.CalendarMonth
                        )
                    }
                }


            }

            Text(
                "Calorie Target Recommendation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )


            OutlinedTextField(
                value = if (customCaloriesInput.isEmpty()) recommendedCalories else customCaloriesInput,
                onValueChange = { input ->
                    customCaloriesInput = input.filter { it.isDigit() }
                    targetSaved = false
                },
                label = { Text("Custom (kcal)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        UpdateDataStore(viewModel, recommendedCalories,isFemale, weightString,heightString,ageString)
                        targetSaved = true
                    },
                    enabled = !targetSaved
                ) {
                    Text("Confirm Target")
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (targetSaved) {
                    Text(
                        text = "Target Saved ✅",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green,
                    )
                } else {
                    Text("")
                }
            }

        }
    }
}

fun UpdateDataStore(viewModel: CalorieTargetViewModel, kcalString:String, isfemale: Boolean, weightString:String, heightString:String, ageString:String) {
    kcalString.toIntOrNull()?.let { cal ->
        viewModel.setCalorieTarget(cal)
    }
    viewModel.setIsFemale(isfemale)
    weightString.toIntOrNull()?.let { value ->
        viewModel.setWeight(value)
    }
    heightString.toIntOrNull()?.let { value ->
        viewModel.setHeight(value)
    }
    ageString.toIntOrNull()?.let { value ->
        viewModel.setAge(value)
    }
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

@Composable
fun DisplayInput(
    modifier: Modifier,
    displayString:String,
    onValueChange: (String) -> Unit,
    label:String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            OutlinedTextField(
                value = displayString,
                onValueChange = { input ->
                    onValueChange(input.filter { it.isDigit() })},
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}