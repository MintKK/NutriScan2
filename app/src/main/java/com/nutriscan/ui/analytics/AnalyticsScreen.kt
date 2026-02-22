package com.nutriscan.ui.analytics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.dao.DailyCalories
import com.nutriscan.data.local.dao.DailyMacros
import com.nutriscan.data.local.dao.DailyNetCalories
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.entity.MealLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onCalorieTargetClick: () -> Unit,
    onBack: () -> Unit,
    onExportClick: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val last7Days by viewModel.last7DaysNet.collectAsState()
    val weeklyAverage by viewModel.weeklyAverageNet.collectAsState()
    val targetCalories by viewModel.getTargetCalories.collectAsState()
    val last7DaysMacros by viewModel.last7DaysMacros.collectAsState()
    val weeklyAverageMacros by viewModel.weeklyAverageMacros.collectAsState()
    
    // Drill-down state
    val selectedDateLabel by viewModel.selectedDateLabel.collectAsState()
    val selectedDateMeals by viewModel.selectedDateMeals.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Default.AddLocation,
                            contentDescription = "Export Report",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                "Last 7 Days kcal/day",
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
                DailyCalorieRow(
                    day = day,
                    targetCalories = targetCalories,
                    onClick = { viewModel.selectDate(day.day) }
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // ============ MACRO TRENDS ============
            Text(
                "Macro Trends (7 Days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (last7DaysMacros.any { it.protein > 0 || it.carbs > 0 || it.fat > 0 }) {
                MacroTrendChart(
                    data = last7DaysMacros,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                
                WeeklyMacroInsightsCard(weeklyAverageMacros)
                
                Text(
                    "Daily Macro Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                last7DaysMacros.reversed().forEach { day ->
                    DailyMacroRow(
                        day = day,
                        onClick = { viewModel.selectDate(day.day) }
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No macro data yet. Start logging meals!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // ============ DRILL-DOWN BOTTOM SHEET ============
    if (selectedDateLabel != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedDate() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = selectedDateLabel ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "${selectedDateMeals.size} meal${if (selectedDateMeals.size != 1) "s" else ""} logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (selectedDateMeals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No meals logged this day",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    selectedDateMeals.forEach { meal ->
                        BottomSheetMealItem(meal)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CalorieTrendChart(
    data: List<DailyNetCalories>,
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
                val maxDataValue = data.maxOfOrNull { it.netKcal }?.toFloat() ?: 0f
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
                    val fullBarHeight = (daily.netKcal / maxValue) * chartHeight
                    val barHeight = fullBarHeight * animationProgress
                    val centerX = index * spacing + spacing / 2f
                    val x = centerX - barWidth / 2f
                    val y = chartBottom - barHeight

                    drawRoundRect(
                        color = if (daily.netKcal < targetCalorie)
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
                        ?.let { ((daily.netKcal - it).toFloat() / it) * 100f }
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
                        val y = chartBottom - (daily.netKcal / maxValue) * chartHeight
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

                    val goalLabel = "Goal: ${String.format("%,d", targetCalorie)} kcal"
                    val goalTextLayout = textMeasurer.measure(goalLabel, style = TextStyle(fontSize = 10.sp))

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
fun DailyCalorieRow(day: DailyNetCalories, targetCalories: Int, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
                        color = if (day.netKcal > targetCalories) Color(0x80B77676)
                        else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ).padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${day.netKcal} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

        }
    }
}

// ============ MACRO TREND COMPOSABLES ============

// Color constants for macros
private val ProteinColor = Color(0xFF4CAF50)  // Green
private val CarbsColor = Color(0xFFFF9800)    // Orange  
private val FatColor = Color(0xFFF44336)      // Red

/**
 * 7-day stacked bar chart showing Protein, Carbs, Fat per day.
 * Uses Compose Canvas for custom drawing.
 */
@Composable
fun MacroTrendChart(
    data: List<DailyMacros>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Legend row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MacroLegendItem("Protein", ProteinColor)
                MacroLegendItem("Carbs", CarbsColor)
                MacroLegendItem("Fat", FatColor)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Canvas(modifier = modifier) {
                val barCount = data.size
                if (barCount == 0) return@Canvas
                
                val yAxisPadding = 100f
                val rightPadding = 40f
                val bottomPadding = 40f
                val topPadding = 20f
                
                val chartWidth = size.width - yAxisPadding - rightPadding
                val chartHeight = size.height - topPadding - bottomPadding
                val barWidth = chartWidth / barCount * 0.6f
                val barSpacing = chartWidth / barCount
                
                // Convert macros to calories for scaling: P:4, C:4, F:9
                val maxTotalKcal = data.maxOf { it.protein * 4f + it.carbs * 4f + it.fat * 9f }.coerceAtLeast(10f)
                
                // Draw Y-axis labels and grid lines
                val ySteps = 4
                for (i in 0..ySteps) {
                    val yLabelValue = (maxTotalKcal * i / ySteps).toInt()
                    val yPos = size.height - bottomPadding - (chartHeight * i / ySteps)
                    
                    // Grid line
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(yAxisPadding, yPos),
                        end = Offset(size.width - rightPadding, yPos),
                        strokeWidth = 2f
                    )
                    
                    // Label
                    val labelResult = textMeasurer.measure(
                        "$yLabelValue kcal",
                        style = TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.Gray)
                    )
                    drawText(
                        labelResult,
                        topLeft = Offset(yAxisPadding - labelResult.size.width - 16f, yPos - labelResult.size.height / 2f)
                    )
                }
                
                data.forEachIndexed { index, day ->
                    val x = yAxisPadding + index * barSpacing + (barSpacing - barWidth) / 2
                    
                    val proteinKcal = day.protein * 4f
                    val carbsKcal = day.carbs * 4f
                    val fatKcal = day.fat * 9f
                    
                    val proteinH = (proteinKcal / maxTotalKcal) * chartHeight
                    val carbsH = (carbsKcal / maxTotalKcal) * chartHeight
                    val fatH = (fatKcal / maxTotalKcal) * chartHeight
                    
                    var yOffset = size.height - bottomPadding
                    
                    // Protein (bottom)
                    if (proteinH > 0f) {
                        drawRoundRect(
                            color = ProteinColor,
                            topLeft = Offset(x, yOffset - proteinH),
                            size = Size(barWidth, proteinH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        yOffset -= proteinH
                    }
                    
                    // Carbs (middle)
                    if (carbsH > 0f) {
                        drawRoundRect(
                            color = CarbsColor,
                            topLeft = Offset(x, yOffset - carbsH),
                            size = Size(barWidth, carbsH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        yOffset -= carbsH
                    }
                    
                    // Fat (top)
                    if (fatH > 0f) {
                        drawRoundRect(
                            color = FatColor,
                            topLeft = Offset(x, yOffset - fatH),
                            size = Size(barWidth, fatH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    
                    // Day label
                    val dayLabel = day.day.takeLast(5) // "MM-DD"
                    val textResult = textMeasurer.measure(
                        dayLabel,
                        style = TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
                    )
                    drawText(
                        textResult,
                        topLeft = Offset(
                            x + barWidth / 2 - textResult.size.width / 2,
                            size.height - bottomPadding + 8f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroLegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Card showing weekly average P/C/F with progress bars.
 */
@Composable
fun WeeklyMacroInsightsCard(macros: MacroTotals) {
    val total = macros.protein + macros.carbs + macros.fat
    
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Weekly Avg. Macros / Day",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            MacroProgressRow(
                label = "Protein",
                value = macros.protein,
                total = total,
                color = ProteinColor
            )
            MacroProgressRow(
                label = "Carbs",
                value = macros.carbs,
                total = total,
                color = CarbsColor
            )
            MacroProgressRow(
                label = "Fat",
                value = macros.fat,
                total = total,
                color = FatColor
            )
        }
    }
}

@Composable
private fun MacroProgressRow(
    label: String,
    value: Float,
    total: Float,
    color: Color
) {
    val fraction = if (total > 0) value / total else 0f
    val percentage = (fraction * 100).toInt()
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${"%.0f".format(value)}g ($percentage%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
    }
}

/**
 * Per-day macro breakdown row with inline colored bars.
 */
@Composable
fun DailyMacroRow(day: DailyMacros, onClick: () -> Unit = {}) {
    val dayLabel = try {
        val parsed = java.time.LocalDate.parse(day.day)
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
    } catch (e: Exception) {
        day.day
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${day.kcal} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Stacked horizontal bar
            val total = day.protein + day.carbs + day.fat
            if (total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    Box(
                        Modifier
                            .weight(day.protein.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(ProteinColor)
                    )
                    Box(
                        Modifier
                            .weight(day.carbs.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(CarbsColor)
                    )
                    Box(
                        Modifier
                            .weight(day.fat.coerceAtLeast(0.1f))
                            .fillMaxHeight()
                            .background(FatColor)
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("P: ${"%.0f".format(day.protein)}g", style = MaterialTheme.typography.labelSmall, color = ProteinColor)
                Text("C: ${"%.0f".format(day.carbs)}g", style = MaterialTheme.typography.labelSmall, color = CarbsColor)
                Text("F: ${"%.0f".format(day.fat)}g", style = MaterialTheme.typography.labelSmall, color = FatColor)
            }
        }
    }
}

/**
 * Meal item shown inside the drill-down bottom sheet.
 * Shows photo thumbnail (or icon), food name, time, grams, kcal, and macro badges.
 */
@Composable
private fun BottomSheetMealItem(meal: MealLog) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    val mealBitmap = remember(meal.imagePath) {
        meal.imagePath?.let { path ->
            try { android.graphics.BitmapFactory.decodeFile(path) }
            catch (e: Exception) { null }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo or icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (mealBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = mealBitmap.asImageBitmap(),
                        contentDescription = meal.foodName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.foodName.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${meal.grams}g • ${timeFormat.format(Date(meal.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("P:${"%.0f".format(meal.proteinTotal)}g", style = MaterialTheme.typography.labelSmall, color = ProteinColor)
                    Text("C:${"%.0f".format(meal.carbsTotal)}g", style = MaterialTheme.typography.labelSmall, color = CarbsColor)
                    Text("F:${"%.0f".format(meal.fatTotal)}g", style = MaterialTheme.typography.labelSmall, color = FatColor)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${meal.kcalTotal}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

