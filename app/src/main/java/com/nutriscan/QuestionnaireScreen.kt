package com.nutriscan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Star

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    viewModel: QuestionnaireViewModel = hiltViewModel(),
    onFinished: (NutritionTargets) -> Unit
) {
    val progress = (viewModel.step + 1).toFloat() / viewModel.totalSteps
    
    val stepTitles = listOf("Your Goal", "About You", "Activity Level")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Up Your Profile") },
                navigationIcon = {
                    if (viewModel.step > 0) {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "Step ${viewModel.step + 1} of ${viewModel.totalSteps}: ${stepTitles[viewModel.step]}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Animated step content
            AnimatedContent(
                targetState = viewModel.step,
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                label = "stepTransition",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentStep) {
                        0 -> GoalSelectionStep(
                            selected = viewModel.selectedGoal,
                            onSelect = { viewModel.selectedGoal = it }
                        )
                        1 -> PersonalInfoStep(
                            selectedGender = viewModel.selectedGender,
                            onGenderSelect = { viewModel.selectedGender = it },
                            age = viewModel.age,
                            onAgeChange = { viewModel.age = it },
                            weight = viewModel.weightKg,
                            onWeightChange = { viewModel.weightKg = it },
                            height = viewModel.heightCm,
                            onHeightChange = { viewModel.heightCm = it }
                        )
                        2 -> ActivityLevelStep(
                            selected = viewModel.selectedActivityLevel,
                            onSelect = { viewModel.selectedActivityLevel = it }
                        )
                    }
                }
            }

            // Error message
            viewModel.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (viewModel.step > 0) {
                    OutlinedButton(
                        onClick = { viewModel.goBack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = {
                        val done = viewModel.next()
                        if (done) {
                            val profile = viewModel.buildProfile()
                            val targets = NutritionCalculator.calculateTargets(profile)
                            viewModel.saveTargetsToDataStore(targets)
                            onFinished(targets)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (viewModel.step == viewModel.totalSteps - 1) "Get My Plan ✨" else "Next →")
                }
            }
        }
    }
}

// ============ STEP 0: GOAL SELECTION ============

@Composable
private fun GoalSelectionStep(selected: Goal?, onSelect: (Goal) -> Unit) {
    Text(
        "What's your fitness goal?",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        "We'll customize your nutrition targets based on your goal.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    SelectionCard(
        icon = Icons.AutoMirrored.Filled.TrendingDown,
        title = "Lose Weight",
        subtitle = "Calorie deficit for fat loss",
        isSelected = selected == Goal.FAT_LOSS,
        onClick = { onSelect(Goal.FAT_LOSS) },
        tintColor = Color(0xFFE57373)
    )
    SelectionCard(
        icon = Icons.Default.Star,
        title = "Maintain Weight",
        subtitle = "Stay at your current weight",
        isSelected = selected == Goal.WEIGHT_MAINTENANCE,
        onClick = { onSelect(Goal.WEIGHT_MAINTENANCE) },
        tintColor = Color(0xFF81C784)
    )
    SelectionCard(
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        title = "Gain Muscle",
        subtitle = "Calorie surplus for muscle growth",
        isSelected = selected == Goal.MUSCLE_GAIN,
        onClick = { onSelect(Goal.MUSCLE_GAIN) },
        tintColor = Color(0xFF64B5F6)
    )
}

// ============ STEP 1: PERSONAL INFO (CONSOLIDATED) ============

@Composable
private fun PersonalInfoStep(
    selectedGender: Gender?,
    onGenderSelect: (Gender) -> Unit,
    age: String,
    onAgeChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    height: String,
    onHeightChange: (String) -> Unit
) {
    Text(
        "Tell us about yourself",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        "We use this to calculate your daily calorie needs.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))

    // Gender row
    Text("Gender", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SelectionCard(
            icon = Icons.Default.Person,
            title = "Male",
            subtitle = null,
            isSelected = selectedGender == Gender.MALE,
            onClick = { onGenderSelect(Gender.MALE) },
            tintColor = Color(0xFF64B5F6),
            modifier = Modifier.weight(1f)
        )
        SelectionCard(
            icon = Icons.Default.Person,
            title = "Female",
            subtitle = null,
            isSelected = selectedGender == Gender.FEMALE,
            onClick = { onGenderSelect(Gender.FEMALE) },
            tintColor = Color(0xFFF48FB1),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(4.dp))

    // Age
    IconTextField(
        icon = Icons.Default.Cake,
        value = age,
        label = "Age (years)",
        keyboardType = KeyboardType.Number,
        onValueChange = onAgeChange
    )

    // Weight
    IconTextField(
        icon = Icons.Default.MonitorWeight,
        value = weight,
        label = "Weight (kg)",
        keyboardType = KeyboardType.Decimal,
        onValueChange = onWeightChange
    )

    // Height
    IconTextField(
        icon = Icons.Default.Height,
        value = height,
        label = "Height (cm)",
        keyboardType = KeyboardType.Decimal,
        onValueChange = onHeightChange
    )
}

// ============ STEP 2: ACTIVITY LEVEL ============

@Composable
private fun ActivityLevelStep(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    Text(
        "How active are you?",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        "This helps us adjust your daily calorie expenditure.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    SelectionCard(
        icon = Icons.Default.SelfImprovement,
        title = "Sedentary",
        subtitle = "Little or no exercise",
        isSelected = selected == ActivityLevel.SEDENTARY,
        onClick = { onSelect(ActivityLevel.SEDENTARY) },
        tintColor = Color(0xFF90A4AE)
    )
    SelectionCard(
        icon = Icons.Default.Person,
        title = "Lightly Active",
        subtitle = "Exercise 1–3 days/week",
        isSelected = selected == ActivityLevel.LIGHTLY_ACTIVE,
        onClick = { onSelect(ActivityLevel.LIGHTLY_ACTIVE) },
        tintColor = Color(0xFF81C784)
    )
    SelectionCard(
        icon = Icons.Default.FitnessCenter,
        title = "Moderately Active",
        subtitle = "Exercise 3–5 days/week",
        isSelected = selected == ActivityLevel.MODERATELY_ACTIVE,
        onClick = { onSelect(ActivityLevel.MODERATELY_ACTIVE) },
        tintColor = Color(0xFFFFB74D)
    )
    SelectionCard(
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        title = "Very Active",
        subtitle = "Exercise 6–7 days/week",
        isSelected = selected == ActivityLevel.VERY_ACTIVE,
        onClick = { onSelect(ActivityLevel.VERY_ACTIVE) },
        tintColor = Color(0xFFE57373)
    )
}

// ============ REUSABLE COMPONENTS ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) tintColor else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) tintColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) tintColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) tintColor else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTextField(
    icon: ImageVector,
    value: String,
    label: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
