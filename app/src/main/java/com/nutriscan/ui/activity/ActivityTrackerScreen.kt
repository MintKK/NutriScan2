package com.nutriscan.ui.activity

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.entity.ActivityLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTrackerScreen(
    onBack: () -> Unit,
    viewModel: ActivityTrackerViewModel = hiltViewModel()
) {
    val liveSteps by viewModel.liveSteps.collectAsState()
    val distanceMeters by viewModel.distanceMeters.collectAsState()
    val currentActivity by viewModel.currentActivity.collectAsState()
    val activeMinutes by viewModel.activeMinutes.collectAsState()
    val activeSeconds by viewModel.activeSeconds.collectAsState()
    val isTracking by viewModel.isTrackingActive.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val timeline by viewModel.activityTimeline.collectAsState()
    val context = LocalContext.current

    val distanceKm = distanceMeters / 1000.0

    // Permission launcher
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            viewModel.startTracking()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical Activity Tracker") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Start / Stop toggle
            item {
                TrackingToggleCard(
                    isTracking = isTracking,
                    onStart = {
                        val permissions = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (permissions.isEmpty()) {
                            viewModel.startTracking()
                        } else {
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                        // Prompt battery optimization after starting
                        if (viewModel.isBatteryOptimized()) {
                            viewModel.requestBatteryOptimizationExemption()
                        }
                    },
                    onStop = { viewModel.stopTracking() }
                )
            }

            // Battery optimization warning
            if (isTracking && viewModel.isBatteryOptimized()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Battery optimization is on. Tracking may stop in background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.requestBatteryOptimizationExemption() }) {
                                Text("Fix", color = Color(0xFFE65100))
                            }
                        }
                    }
                }
            }

            // IN_VEHICLE pause banner
            if (isPaused) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF795548).copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF795548),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Vehicle detected — step and distance tracking paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF795548)
                            )
                        }
                    }
                }
            }

            // Current activity card
            item {
                CurrentActivityCard(
                    activity = currentActivity,
                    isTracking = isTracking
                )
            }

            // Live stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        value = String.format("%,d", liveSteps),
                        label = "Steps",
                        color = Color(0xFF4CAF50)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Straighten,
                        value = String.format("%.2f km", distanceKm),
                        label = "Distance",
                        color = Color(0xFFFF9800)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        value = "${activeSeconds / 60}m ${activeSeconds % 60}s",
                        label = "Active Time",
                        color = Color(0xFF2196F3)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        value = if (activeMinutes > 0 && distanceKm > 0) {
                            String.format("%.1f km/h", distanceKm / (activeMinutes / 60.0))
                        } else "—",
                        label = "Avg Speed",
                        color = Color(0xFF9C27B0)
                    )
                }
            }

            // Activity timeline header
            item {
                Text(
                    "Activity Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (timeline.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No activity detected yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isTracking) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Start tracking to see your activity timeline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(timeline.reversed(), key = { it.id }) { log ->
                    TimelineItem(log = log)
                }
            }

            // Bottom spacer
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TrackingToggleCard(
    isTracking: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isTracking) {
            Color(0xFF4CAF50).copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "trackingBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isTracking) "Tracking Active" else "Tracking Stopped",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isTracking) "Sensors are running in background"
                    else "Tap Start to begin tracking",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = if (isTracking) onStop else onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracking) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF4CAF50)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isTracking) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun CurrentActivityCard(
    activity: String,
    isTracking: Boolean
) {
    // Pulse animation when tracking
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTracking) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val actColor = activityColor(activity)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = actColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Activity icon with pulse
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(if (isTracking) pulseScale else 1f)
                    .background(actColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    activityIcon(activity),
                    contentDescription = activity,
                    tint = actColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column {
                Text(
                    "Current Activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    activity.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = actColor
                )
            }

            Spacer(Modifier.weight(1f))

            // Live indicator
            AnimatedVisibility(visible = isTracking) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(pulseScale)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
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
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineItem(log: ActivityLog) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val color = activityColor(log.activityType)
    val isEnter = log.transitionType == "ENTER"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Icon(
                activityIcon(log.activityType),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    log.activityType.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isEnter) "Started" else "Stopped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                timeFormat.format(Date(log.timestamp)),
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
    "IN_VEHICLE" -> Icons.Default.DirectionsCar
    else -> Icons.Default.FitnessCenter
}

private fun activityColor(activity: String): Color = when (activity) {
    "WALKING" -> Color(0xFF4CAF50)
    "RUNNING" -> Color(0xFFF44336)
    "CYCLING" -> Color(0xFF2196F3)
    "STILL" -> Color(0xFF9E9E9E)
    "IN_VEHICLE" -> Color(0xFF795548)
    else -> Color(0xFF757575)
}
