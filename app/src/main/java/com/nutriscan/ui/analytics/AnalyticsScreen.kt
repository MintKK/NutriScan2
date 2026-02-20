package com.nutriscan.ui.analytics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
            // Weekly Summary Card
            Box() {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Card() {
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
                        }
                    }

                    Card() {
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
                            Box(
                                modifier = Modifier.size(48.dp)
                                    .background(
                                        color = if (weeklyAverage > targetCalories) Color(0x80B77676)
                                        else Color(0x807FC382),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    if (weeklyAverage > targetCalories) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                        }
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
                DailyCalorieRow(day, targetCalories)
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

    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val animationProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1600,
            easing = FastOutSlowInEasing
        ),
        label = "chart_animation"
    )
    
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
            val textMeasurer = rememberTextMeasurer()
            val graphTextColor = MaterialTheme.colorScheme.onSurfaceVariant

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val maxDataValue = data.maxOfOrNull { it.totalKcal }?.toFloat() ?: 0f
                val maxValue = maxOf(maxDataValue, targetCalorie.toFloat()).coerceAtLeast(1f)
                val width = size.width
                val height = size.height
                //  chart padding

                val topPadding = 32.dp.toPx()
                val labelPadding  = 32.dp.toPx()
                val chartBottom = height - labelPadding  // bottom of the drawable chart area
                val chartHeight = chartBottom - topPadding

                val spacing = width / data.size
                val barWidth = spacing * 0.6f

                // Grids
                val numberOfGridLines = 4
                val gridColor = Color.Gray.copy(alpha = 0.35f)
                for (i in 0..numberOfGridLines) {
                    val y = chartBottom - i * (chartHeight / numberOfGridLines)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                // Draw bars
                data.forEachIndexed { index, daily ->
                    val fullBarHeight = (daily.totalKcal / maxValue) * chartHeight
                    val barHeight = fullBarHeight * animationProgress
                    val centerX = index * spacing + spacing / 2f
                    val x = centerX - barWidth / 2f
                    val y = chartBottom - barHeight

                    drawRoundRect(
                        color = if (daily.totalKcal < targetCalorie)
                            Color(0xFF80C583)
                        else
                            Color(0xFFB87777),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Draw text below bar
                    val dateLayoutResult = textMeasurer.measure(
                        text = daily.day.takeLast(5), // e.g. "02-02"
                    )

                    // date
                    val textY = height - labelPadding / 2f - dateLayoutResult.size.height / 2f
                    drawText(
                        dateLayoutResult,
                        topLeft = Offset(
                            x + (barWidth - dateLayoutResult.size.width) / 2f,
                            textY // small spacing below bar
                        ),
                        color = graphTextColor
                    )
                    val deviationPercent = targetCalorie.takeIf { it != 0 }
                        ?.let { ((daily.totalKcal - it).toFloat() / it) * 100f }
                        ?: 0f

                    val formattedDeviation =
                        if (deviationPercent <= -100f) {
                            ""   // hide -100%
                        } else {
                            String.format("%+.0f%%", deviationPercent)
                        }

                    val deviationLayoutResult = textMeasurer.measure(
                        text = formattedDeviation,
                        style = TextStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // deviation
                    drawText(
                        deviationLayoutResult,
                        topLeft = Offset(
                            x + (barWidth - deviationLayoutResult.size.width) / 2f, // center text
                            y - deviationLayoutResult.size.height - 10f // move above bar with spacing
                        )
                    )

                }


                // Draw trend line
                if (data.size > 1) {
                    val path = Path()
                    val points = data.mapIndexed { index, daily ->
                        val centerX = index * spacing + spacing / 2f
                        val y = chartBottom - (daily.totalKcal / maxValue) * chartHeight
                        Offset(centerX, y)
                    }


                    path.moveTo(points.first().x, points.first().y)
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = Offset(points.first().x, points.first().y)
                    )

                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val current = points[i]

                        val controlX = (prev.x + current.x) / 2f

                        path.cubicTo(
                            controlX, prev.y,   // control point 1
                            controlX, current.y,// control point 2
                            current.x, current.y
                        )

                        drawCircle(
                            color = primaryColor,
                            radius = 5.dp.toPx(),
                            center = Offset(current.x, current.y)
                        )
                    }
                    val pathLength = 1200f
                    val pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(pathLength * animationProgress, pathLength),
                        0f
                    )

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = pathEffect
                        )
                    )


                }

                // Draw Goal line
                if (true){
                    val path = Path()
                    val x0 = 0f
                    val goalY = chartBottom - (targetCalorie / maxValue) * chartHeight

                    val x1 = width * animationProgress

                    path.moveTo(x0,goalY)
                    path.lineTo(x1,goalY)

                    drawPath(
                        path = path,
                        color = primaryColor.copy(alpha = 0.5f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(18f, 12f), // [dash length, gap length] in pixels
                                0f
                            )
                        )
                    )

                    val goalLabel = "Goal"
                    val goalTextLayout = textMeasurer.measure(goalLabel)

                    // Calculate centered position
                    val textX = (width - goalTextLayout.size.width) / 2f
                    val textY = goalY - goalTextLayout.size.height - 8f // 8px above line

                    // Draw pill background
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.1f),
                        topLeft = Offset(textX - 8f, textY - 4f), // add padding around text
                        size = Size(
                            goalTextLayout.size.width + 16f,
                            goalTextLayout.size.height + 8f
                        ),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Draw text on top
                    drawText(
                        goalTextLayout,
                        topLeft = Offset(textX, textY),
                        color = primaryColor
                    )
                }


            }
        }
    }
}

@Composable
fun DailyCalorieRow(day: DailyCalories, targetCalories: Int) {
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.wrapContentWidth()
                    .background(
                        color = if (day.totalKcal > targetCalories) Color(0x80B77676)
                        else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ).padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${day.totalKcal} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

        }
    }
}
