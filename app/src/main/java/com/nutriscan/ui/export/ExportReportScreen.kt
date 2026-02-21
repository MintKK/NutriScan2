package com.nutriscan.ui.export

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs

// Colour palette
private val GreenAccent = Color(0xFF4CAF50)
private val OrangeAccent = Color(0xFFFF9800)
private val RedAccent = Color(0xFFF44336)
private val BlueAccent = Color(0xFF2196F3)
private val NetBlue = Color(0xFF1E88E5)
private val BurnedOrange = Color(0xFFFF7043)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportScreen(
    onBack: () -> Unit,
    viewModel: ExportReportViewModel = hiltViewModel()
) {
    val reportData by viewModel.reportData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val context = LocalContext.current

    // Launch share intent when pdf is ready
    LaunchedEffect(pdfFile) {
        pdfFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NutriScan Weekly Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
            viewModel.clearPdfFile()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (reportData != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.generatePdf() },
                    icon = {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Default.Share, "Export")
                        }
                    },
                    text = { Text(if (isGenerating) "Generating…" else "Export PDF") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Loading weekly data…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (reportData == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Unable to load report data.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val data = reportData!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ---- Header ----
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            "NutriScan Weekly Report",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${data.startDate}  →  ${data.endDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        val gender = if (data.isFemale) "Female" else "Male"
                        Text(
                            "${data.userWeightKg} kg  •  ${data.userHeightCm} cm  •  Age ${data.userAge}  •  $gender",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }

                // ---- Net Calorie Results ----
                SectionHeader("📊  Net Calorie Results")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Avg Net",
                        value = "${data.avgNetCalories} kcal",
                        color = NetBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Target",
                        value = "${data.targetCalories} kcal",
                        color = GreenAccent,
                        modifier = Modifier.weight(1f)
                    )
                    val devText = if (data.calorieDeviationPct >= 0) "+${"%.1f".format(data.calorieDeviationPct)}%"
                        else "${"%.1f".format(data.calorieDeviationPct)}%"
                    val devColor = if (abs(data.calorieDeviationPct) <= 10) GreenAccent else BurnedOrange
                    StatCard(
                        label = "Deviation",
                        value = devText,
                        color = devColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Per-day net breakdown
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        data.dailyNet.forEach { day ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(day.day.takeLast(5), style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "Eaten ${day.eatenKcal}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenAccent
                                    )
                                    Text(
                                        "Burned ${day.burnedKcal}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BurnedOrange
                                    )
                                    Text(
                                        "Net ${day.netKcal}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.netKcal <= data.targetCalories) GreenAccent else RedAccent
                                    )
                                }
                            }
                            if (day != data.dailyNet.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // ---- Macros ----
                SectionHeader("🥩  Macro Breakdown (Daily Avg)")

                val m = data.avgMacros
                val total = m.protein + m.carbs + m.fat
                if (total > 0) {
                    // Stacked bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        val pFrac = m.protein / total
                        val cFrac = m.carbs / total
                        val fFrac = m.fat / total
                        Box(Modifier.weight(pFrac).fillMaxHeight().background(GreenAccent))
                        Box(Modifier.weight(cFrac).fillMaxHeight().background(OrangeAccent))
                        Box(Modifier.weight(fFrac).fillMaxHeight().background(RedAccent))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroLabel("Protein", m.protein, total, GreenAccent)
                        MacroLabel("Carbs", m.carbs, total, OrangeAccent)
                        MacroLabel("Fat", m.fat, total, RedAccent)
                    }
                } else {
                    Text(
                        "No macro data this week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ---- Activity ----
                SectionHeader("🚶  Activity Summary")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Total Steps",
                        value = String.format("%,d", data.totalSteps),
                        color = GreenAccent,
                        icon = Icons.Default.DirectionsWalk,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Distance",
                        value = "${"%.2f".format(data.totalDistanceKm)} km",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Daily Avg",
                        value = String.format("%,d", data.avgDailySteps),
                        color = NetBlue,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ---- Water ----
                SectionHeader("💧  Water Intake")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Avg / Day",
                        value = "${data.avgWaterMl} ml",
                        color = BlueAccent,
                        icon = Icons.Default.WaterDrop,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Goal",
                        value = "${data.waterGoalMl} ml",
                        color = BlueAccent,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ---- Achievements ----
                SectionHeader("🏆  Achievements")

                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        data.streaks.forEach { streak ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(streak.emoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        streak.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${streak.currentStreak}-day streak (best: ${streak.bestStreak})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (data.earnedBadges.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "Earned Badges",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            data.earnedBadges.forEach { badge ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(badge.emoji, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(badge.title, fontWeight = FontWeight.Medium)
                                        Text(
                                            badge.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacer for FAB
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ============ HELPER COMPOSABLES ============

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MacroLabel(label: String, value: Float, total: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(10.dp).background(color, CircleShape)
        )
        Text(
            "${"%.0f".format(value)}g (${"%.0f".format(value / total * 100)}%)",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
