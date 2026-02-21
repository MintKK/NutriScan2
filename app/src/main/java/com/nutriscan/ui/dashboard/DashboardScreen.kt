package com.nutriscan.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nutriscan.data.local.dao.MacroTotals
import com.nutriscan.data.local.entity.MealLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddMealClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onCaloriesBurnedClick: () -> Unit = {},
    onFeedClick: () -> Unit,
    onRetakeQuestionnaire: () -> Unit = {},
    onFoodDiaryClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
//    val todayCalories by viewModel.todayCalories.collectAsState()
    val netCalories by viewModel.netCalories.collectAsState()
    val calorieGoal by viewModel.calorieGoal.collectAsState()
    val todayMacros by viewModel.todayMacros.collectAsState()
    val todayMeals by viewModel.todayMeals.collectAsState()
    val weeklyAverage by viewModel.weeklyAverageNet.collectAsState()
    val liveSteps by viewModel.liveSteps.collectAsState()
    val isStepTrackingActive by viewModel.isStepTrackingActive.collectAsState()
    val todayWaterMl by viewModel.todayWaterMl.collectAsState()
    val waterGoalMl by viewModel.waterGoalMl.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    },
                    label = { Text("Re-take Questionnaire") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onRetakeQuestionnaire()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )}
        }
    ) {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("NutriScan") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddMealClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Meal")
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Calorie Progress Ring
                    item {
                        CalorieProgressCard(
    //                        consumed = todayCalories,
                            consumed = netCalories.toInt(),
                        goal = calorieGoal,
                            macros = todayMacros,
                            onAnalyticsClick
                        )
                    }

                    // Weekly Average Card
                    item {
                        WeeklyAverageCard(average = weeklyAverage.toInt())
                    }

                    // Calories Burned / Activity Card
                    item {
                        CaloriesBurnedQuickCard(
                            steps = liveSteps,
                            isTracking = isStepTrackingActive,
                            onClick = onCaloriesBurnedClick
                        )
                    }

                    // Water Tracker Card
                    item {
                        WaterTrackerCard(
                            currentMl = todayWaterMl,
                            goalMl = waterGoalMl,
                            onAdd250 = { viewModel.addWater(250) },
                            onAdd500 = { viewModel.addWater(500) },
                            onUndo = { viewModel.undoLastWater() }
                        )
                    }

                    // Today's Meals Header
                    item {
                        Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                "Today's Meals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onFoodDiaryClick) {
                            Text("Food Diary →")
                        }
                    }
                }

                    // Meal List
                    if (todayMeals.isEmpty()) {
                        item {
                            EmptyMealsCard(onAddMealClick = onAddMealClick)
                        }
                    } else {
                        items(todayMeals, key = { it.id }) { meal ->
                            MealLogItem(
                                meal = meal,
                                onDelete = { viewModel.deleteMeal(meal.id) }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onFeedClick,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = "Feed")
                }
            }
        }
    }
}

@Composable
fun CalorieProgressCard(
    consumed: Int,
    goal: Int,
    macros: MacroTotals,
    onAnalyticsClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onAnalyticsClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Today's Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Animated Calorie Ring
            CalorieRing(consumed = consumed, goal = goal)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Macro Breakdown
            MacroRow(macros = macros)
        }
    }
}

@Composable
fun CalorieRing(consumed: Int, goal: Int) {
    val progress = (consumed.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1.2f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )
    
    val ringColor = when {
        consumed > goal -> Color(0xFFE57373)  // Red if over
        consumed > goal * 0.8 -> Color(0xFFFFB74D)  // Orange if close
        else -> Color(0xFF81C784)  // Green otherwise
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        // Background ring
        Canvas(modifier = Modifier.size(160.dp)) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Progress ring
        Canvas(modifier = Modifier.size(160.dp)) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$consumed",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "/ $goal kcal",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MacroRow(macros: MacroTotals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MacroItem(label = "Protein", value = "${macros.protein.toInt()}g", color = Color(0xFF64B5F6))
        MacroItem(label = "Carbs", value = "${macros.carbs.toInt()}g", color = Color(0xFFFFB74D))
        MacroItem(label = "Fat", value = "${macros.fat.toInt()}g", color = Color(0xFFE57373))
    }
}

@Composable
fun MacroItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WeeklyAverageCard(average: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Weekly Average", style = MaterialTheme.typography.bodyMedium)
            Text(
                "$average kcal/day",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyMealsCard(onAddMealClick: () -> Unit) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No meals logged yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddMealClick) {
                Text("Add Your First Meal")
            }
        }
    }
}

@Composable
fun MealLogItem(
    meal: MealLog,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    // Load meal image if available
    val mealBitmap = remember(meal.imagePath) {
        meal.imagePath?.let { path ->
            try {
                android.graphics.BitmapFactory.decodeFile(path)
            } catch (e: Exception) { null }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo thumbnail or food icon placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (mealBitmap != null) {
                    Image(
                        bitmap = mealBitmap.asImageBitmap(),
                        contentDescription = meal.foodName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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
            }
            
            Text(
                text = "${meal.kcalTotal} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaloriesBurnedQuickCard(
    steps: Int,
    isTracking: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTracking) {
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "Calories Burned",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isTracking) "🚶 ${String.format("%,d", steps)} steps"
                        else "Tap to view activity tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = "Go to Calories Burned",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============ WATER TRACKER ============

private val WaterBlue = Color(0xFF2196F3)
private val WaterBlueDark = Color(0xFF1565C0)
private val WaterBlueLight = Color(0xFF64B5F6)

@Composable
fun WaterTrackerCard(
    currentMl: Int,
    goalMl: Int,
    onAdd250: () -> Unit,
    onAdd500: () -> Unit,
    onUndo: () -> Unit
) {
    val fraction = if (goalMl > 0) (currentMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val percentage = (fraction * 100).toInt()
    
    // Smooth animated fill level
    val animatedFill by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "waterFill"
    )
    
    // Continuous wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "💧 Hydration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (currentMl > 0) {
                    TextButton(onClick = onUndo) {
                        Text("Undo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Wave animation circle
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val radius = w / 2f
                    val center = Offset(radius, radius)
                    
                    // Background circle
                    drawCircle(
                        color = WaterBlueLight.copy(alpha = 0.15f),
                        radius = radius,
                        center = center
                    )
                    
                    // Clipped wave fill
                    val clipPath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
                        )
                    }
                    
                    clipPath(clipPath) {
                        // Water level (bottom to top)
                        val waterTop = h * (1f - animatedFill)
                        
                        // Wave 1
                        val wavePath1 = Path().apply {
                            moveTo(0f, waterTop)
                            for (x in 0..w.toInt()) {
                                val y = waterTop + 6f * kotlin.math.sin(
                                    (x.toFloat() / w) * 4f * Math.PI.toFloat() + wavePhase
                                )
                                lineTo(x.toFloat(), y)
                            }
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        
                        drawPath(
                            wavePath1,
                            brush = Brush.verticalGradient(
                                colors = listOf(WaterBlue, WaterBlueDark),
                                startY = waterTop,
                                endY = h
                            )
                        )
                        
                        // Wave 2 (offset, slightly transparent)
                        val wavePath2 = Path().apply {
                            moveTo(0f, waterTop)
                            for (x in 0..w.toInt()) {
                                val y = waterTop + 4f * kotlin.math.sin(
                                    (x.toFloat() / w) * 3f * Math.PI.toFloat() + wavePhase + 1.5f
                                )
                                lineTo(x.toFloat(), y)
                            }
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        
                        drawPath(
                            wavePath2,
                            color = WaterBlueLight.copy(alpha = 0.4f)
                        )
                    }
                    
                    // Border circle
                    drawCircle(
                        color = WaterBlue.copy(alpha = 0.3f),
                        radius = radius - 1f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }
                
                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$percentage%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (animatedFill > 0.5f) Color.White else WaterBlueDark
                    )
                    Text(
                        "${currentMl}ml / ${goalMl}ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (animatedFill > 0.5f) Color.White.copy(alpha = 0.9f) 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Quick-add buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = onAdd250,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = WaterBlue
                    ),
                    border = BorderStroke(1.dp, WaterBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ 250ml")
                }
                Button(
                    onClick = onAdd500,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WaterBlue
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ 500ml")
                }
            }
        }
    }
}
