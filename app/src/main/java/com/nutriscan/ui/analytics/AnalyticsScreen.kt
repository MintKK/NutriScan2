package com.nutriscan.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.dao.DailyCalories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onCalorieTargetClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val last7Days by viewModel.last7DaysCalories.collectAsState()
    val weeklyAverage by viewModel.weeklyAverage.collectAsState()
    val targetCalories by viewModel.getTargetCalories.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
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
            // Weekly Summary Card
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
                                "Weekly Goal",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${targetCalories.toInt()} kcal/day",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onCalorieTargetClick
                        ) {
                            Text("Edit Goal")
                        }
//                        Icon(
//                            Icons.Default.AddLocation,
//                            contentDescription = null,
//                            modifier = Modifier.size(48.dp),
//                            tint = MaterialTheme.colorScheme.onPrimaryContainer
//                        )
                    }

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
                            Text(
                                "Weekly Average",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${weeklyAverage.toInt()} kcal/day",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }


            }
            
            // Trend Chart
            Text(
                "Last 7 Days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (last7Days.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No data yet. Start logging meals!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                CalorieTrendChart(
                    data = last7Days,
                    targetCalorie = targetCalories,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            
            // Daily Breakdown
            Text(
                "Daily Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            last7Days.reversed().forEach { day ->
                DailyCalorieRow(day)
            }
        }
    }
}

@Composable
fun CalorieTrendChart(
    data: List<DailyCalories>,
    targetCalorie: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (data.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No data")
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val maxDataValue = data.maxOfOrNull { it.totalKcal }?.toFloat() ?: 0f
                val maxValue = maxOf(maxDataValue, targetCalorie.toFloat()).coerceAtLeast(1f)
                val width = size.width
                val height = size.height
                val barWidth = width / (data.size * 2)
                
                // Draw bars
                data.forEachIndexed { index, daily ->
                    val barHeight = (daily.totalKcal / maxValue) * height * 0.8f
                    val x = index * (width / data.size) + barWidth / 2
                    val y = height - barHeight

                    if (daily.totalKcal < targetCalorie) {
                        drawRect(
                            color = Color(0xFF80C583),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    } else {
                        drawRect(
                            color = Color(0xFFB87777),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    }

                }
                
                // Draw trend line
                if (data.size > 1) {
                    val path = Path()
                    data.forEachIndexed { index, daily ->
                        val x = index * (width / (data.size - 1))
                        val y = height - (daily.totalKcal / maxValue) * height * 0.8f
                        
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    
                    drawPath(
                        path = path,
                        color = primaryColor.copy(alpha = 0.5f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Draw Goal line
                if (true){
                    val path = Path()
                    val x0 = 0f
                    val y0 = height - (targetCalorie / maxValue) * height * 0.8f

                    val x1 = width
                    val y1 = y0

                    path.moveTo(x0,y0)
                    path.lineTo(x1,y1)

                    drawPath(
                        path = path,
                        color = primaryColor.copy(alpha = 0.5f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f), // [dash length, gap length] in pixels
                                0f
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DailyCalorieRow(day: DailyCalories) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                day.day,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${day.totalKcal} kcal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
