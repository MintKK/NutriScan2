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
    val targetCalories by viewModel.getTargetCalories.collectAsState()
    var customCaloriesString by remember(targetCalories) { mutableStateOf(targetCalories.toString()) }

    var targetSaved by remember { mutableStateOf(false) }
    var isFemale by remember { mutableStateOf(false) }

    var weightString by remember { mutableStateOf("50") }
    var heightString by remember { mutableStateOf("165") }
    var ageString by remember { mutableStateOf("21") }

    customCaloriesString = CalculateBMICalorie(!isFemale, weightString,heightString,ageString)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Calorie Target") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {

                Column() {
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
                                        targetSaved = false
                                        customCaloriesString = CalculateBMICalorie(!isFemale, weightString,heightString,ageString)
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
//---------------------------------------------------------------------------WEIGHT
                    HorizontalDivider(
                            thickness = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            OutlinedTextField(
                                value = weightString,
                                onValueChange = { input ->
                                    weightString = input.filter { it.isDigit() }
                                    targetSaved = false
                                    customCaloriesString = CalculateBMICalorie(!isFemale, weightString,heightString,ageString)
                                },
                                label = { Text("Weight (kg)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
//---------------------------------------------------------------------------HEIGHT
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            OutlinedTextField(
                                value = heightString,
                                onValueChange = { input ->
                                    heightString = input.filter { it.isDigit() }
                                    targetSaved = false
                                    customCaloriesString = CalculateBMICalorie(!isFemale, weightString,heightString,ageString)
                                },
                                label = { Text("Height (cm)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                        Icon(
                            Icons.Default.Man,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
//---------------------------------------------------------------------------AGE
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            OutlinedTextField(
                                value = ageString,
                                onValueChange = { input ->
                                    ageString = input.filter { it.isDigit() }
                                    targetSaved = false
                                    customCaloriesString = CalculateBMICalorie(!isFemale, weightString,heightString,ageString)
                                },
                                label = { Text("Age") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                }


            }

            Text(
                "Calorie Target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )


            OutlinedTextField(
                value = customCaloriesString,
                onValueChange = { input ->
                    customCaloriesString = input.filter { it.isDigit() }
                    targetSaved = false
                },
                label = { Text("Custom (calories)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        customCaloriesString.toIntOrNull()?.let { cal ->
                            viewModel.setCalorieTarget(cal)
                        }
                        targetSaved = true
                    },
                    enabled = !targetSaved
                ) {
                    Text("Confirm Target")
                }


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