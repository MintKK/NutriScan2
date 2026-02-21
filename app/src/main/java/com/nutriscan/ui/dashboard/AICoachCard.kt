package com.nutriscan.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriscan.data.repository.CoachInsight
import com.nutriscan.data.repository.InsightType

private val CoachPurple = Color(0xFF7C4DFF)
private val CoachPurpleLight = Color(0xFFB388FF)
private val CoachGradientStart = Color(0xFFF3E5F5)

@Composable
fun AICoachCard(
    insights: List<CoachInsight>,
    onActionClick: (String, String) -> Unit = { _, _ -> }
) {
    if (insights.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // Header with gradient accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(CoachPurple, CoachPurpleLight)
                        ),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI Coach",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Insights
            if (insights.size == 1) {
                // Single insight — show inline
                InsightBubble(
                    insight = insights.first(),
                    onActionClick = onActionClick,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Multiple insights — horizontal scroll
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(insights) { insight ->
                        InsightBubble(
                            insight = insight,
                            onActionClick = onActionClick,
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightBubble(
    insight: CoachInsight,
    onActionClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val bubbleColor = when (insight.type) {
        InsightType.SUCCESS -> Color(0xFFE8F5E9)
        InsightType.WARNING -> Color(0xFFFFF3E0)
        InsightType.TIP     -> Color(0xFFE3F2FD)
        InsightType.INFO    -> Color(0xFFF3E5F5)
    }

    val accentColor = when (insight.type) {
        InsightType.SUCCESS -> Color(0xFF4CAF50)
        InsightType.WARNING -> Color(0xFFFF9800)
        InsightType.TIP     -> Color(0xFF2196F3)
        InsightType.INFO    -> Color(0xFF9C27B0)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(insight.emoji, fontSize = 18.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (insight.actionLabel != null && insight.actionData != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = { onActionClick(insight.actionLabel, insight.actionData) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            insight.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }
        }
    }
}
