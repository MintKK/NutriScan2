package com.nutriscan.ui.caloriesburned

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaloriesBurnedScreen(
    onBack: () -> Unit,
    onActivityTrackerClick: () -> Unit,
    viewModel: CaloriesBurnedViewModel = hiltViewModel()
) {
    val liveSteps by viewModel.liveSteps.collectAsState()
    val distanceMeters by viewModel.distanceMeters.collectAsState()
    val currentActivity by viewModel.currentActivity.collectAsState()
    val activeMinutes by viewModel.activeMinutes.collectAsState()
    val isTracking by viewModel.isTrackingActive.collectAsState()
    val caloriesBurned by viewModel.caloriesBurned.collectAsState()
    val netCalories by viewModel.netCalories.collectAsState()

    val distanceKm = distanceMeters / 1000.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calories Burned") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tracking status indicator
            if (!isTracking) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Activity tracking is not active. Start it from the Physical Activity Tracker.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Activity ring + summary
            ActivitySummaryCard(
                steps = liveSteps,
                distanceKm = distanceKm,
                activeMinutes = activeMinutes,
                currentActivity = currentActivity
            )

            // Quick stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    value = String.format("%,d", liveSteps),
                    label = "Steps",
                    color = Color(0xFF4CAF50)
                )
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    value = "${activeMinutes}m",
                    label = "Active",
                    color = Color(0xFF2196F3)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = activityIcon(currentActivity),
                    value = currentActivity.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    label = "Activity",
                    color = activityColor(currentActivity)
                )
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = String.format("%.2f km", distanceKm),
                    label = "Distance",
                    color = Color(0xFFFF9800)
                )
            }

            // Person B placeholder
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Calorie Burn",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Burned calories:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "${"%.1f".format(caloriesBurned)} kcal",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Net calories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "$netCalories kcal",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Physical Activity Tracker button
            Button(
                onClick = onActivityTrackerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Physical Activity Tracker", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    steps: Int,
    distanceKm: Double,
    activeMinutes: Long,
    currentActivity: String
) {
    val stepGoal = 10000
    val progress = (steps.toFloat() / stepGoal).coerceIn(0f, 1.2f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "stepProgress"
    )

    val ringColor = when {
        steps >= stepGoal -> Color(0xFF4CAF50)
        steps >= stepGoal * 0.7 -> Color(0xFFFF9800)
        else -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Today's Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(16.dp))

            // Step progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format("%,d", steps),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "/ ${String.format("%,d", stepGoal)} steps",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun activityIcon(activity: String): ImageVector = when (activity) {
    "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "RUNNING" -> Icons.Default.DirectionsRun
    "CYCLING" -> Icons.Default.DirectionsBike
    "STILL" -> Icons.Default.SelfImprovement
    else -> Icons.Default.FitnessCenter
}

private fun activityColor(activity: String): Color = when (activity) {
    "WALKING" -> Color(0xFF4CAF50)
    "RUNNING" -> Color(0xFFF44336)
    "CYCLING" -> Color(0xFF2196F3)
    "STILL" -> Color(0xFF9E9E9E)
    else -> Color(0xFF757575)
}
