package com.nutriscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.ranges.step
import kotlin.text.toFloat

@Composable
fun QuestionnaireScreen(
    viewModel: QuestionnaireViewModel = viewModel(),
    onFinished: (NutritionTargets) -> Unit
) {
    val progress = (viewModel.step + 1).toFloat() / viewModel.totalSteps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Step ${viewModel.step + 1} of ${viewModel.totalSteps}",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (viewModel.step) {
            0 -> OptionStep(
                title = "What is your goal?",
                options = listOf("Lose Weight", "Maintain Weight", "Gain Muscle"),
                selected = when (viewModel.selectedGoal) {
                    Goal.FAT_LOSS -> "Lose Weight"
                    Goal.WEIGHT_MAINTENANCE -> "Maintain Weight"
                    Goal.MUSCLE_GAIN -> "Gain Muscle"
                    null -> null
                },
                onSelect = {
                    viewModel.selectedGoal = when (it) {
                        "Lose Weight"     -> Goal.FAT_LOSS
                        "Maintain Weight" -> Goal.WEIGHT_MAINTENANCE
                        else              -> Goal.MUSCLE_GAIN
                    }
                }
            )
            1 -> OptionStep(
                title = "What is your gender?",
                options = listOf("Male", "Female"),
                selected = viewModel.selectedGender?.name,
                onSelect = {
                    viewModel.selectedGender = if (it == "Male") Gender.MALE else Gender.FEMALE
                }
            )
            2 -> OptionStep(
                title = "What is your activity level?",
                options = listOf(
                    "Sedentary (little or no exercise)",
                    "Lightly Active (1-3 days/week)",
                    "Moderately Active (3-5 days/week)",
                    "Very Active (6-7 days/week)"
                ),
                selected = viewModel.selectedActivityLevel?.name,
                onSelect = {
                    viewModel.selectedActivityLevel = when (it) {
                        "Sedentary (little or no exercise)"  -> ActivityLevel.SEDENTARY
                        "Lightly Active (1-3 days/week)"     -> ActivityLevel.LIGHTLY_ACTIVE
                        "Moderately Active (3-5 days/week)"  -> ActivityLevel.MODERATELY_ACTIVE
                        else                                 -> ActivityLevel.VERY_ACTIVE
                    }
                }
            )
            3 -> InputStep(
                title = "What is your age?",
                value = viewModel.age,
                hint = "Age (years)",
                keyboardType = KeyboardType.Number,
                onValueChange = { viewModel.age = it }
            )
            4 -> InputStep(
                title = "What is your weight?",
                value = viewModel.weightKg,
                hint = "Weight (kg)",
                keyboardType = KeyboardType.Decimal,
                onValueChange = { viewModel.weightKg = it }
            )
            5 -> InputStep(
                title = "What is your height?",
                value = viewModel.heightCm,
                hint = "Height (cm)",
                keyboardType = KeyboardType.Decimal,
                onValueChange = { viewModel.heightCm = it }
            )
        }

        viewModel.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val done = viewModel.next()
                if (done) {
                    val targets = NutritionCalculator.calculateTargets(viewModel.buildProfile())
                    onFinished(targets)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.step == viewModel.totalSteps - 1) "Finish" else "Next")
        }
    }
}

@Composable
private fun OptionStep(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    options.forEach { option ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected != null && option.startsWith(selected.replace("_", " "), ignoreCase = true),
                    onClick = { onSelect(option) }
                )
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selected != null && option.startsWith(selected.replace("_", " "), ignoreCase = true),
                onClick = { onSelect(option) }
            )
            Text(text = option, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun InputStep(
    title: String,
    value: String,
    hint: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
