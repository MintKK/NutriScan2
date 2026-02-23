package com.nutriscan.ui.dashboard

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var rotation: Float,
    var rotationSpeed: Float
)

@Composable
fun FullScreenConfetti(modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )

    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    var screenWidth by remember { mutableStateOf(0f) }
    
    // Initialize particles when screen width is known
    LaunchedEffect(screenWidth) {
        if (screenWidth > 0 && particles.isEmpty()) {
            val initialParticles = List(100) {
                ConfettiParticle(
                    x = Random.nextFloat() * screenWidth,
                    y = -Random.nextFloat() * 1000f - 50f,
                    vx = Random.nextFloat() * 4f - 2f,
                    vy = Random.nextFloat() * 10f + 5f,
                    color = colors[Random.nextInt(colors.size)],
                    size = Random.nextFloat() * 20f + 10f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 10f - 5f
                )
            }
            particles = initialParticles
        }
    }

    LaunchedEffect(Unit) {
        var lastFrameTime = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTime ->
                if (lastFrameTime == 0L) lastFrameTime = frameTime
                // val deltaTime = (frameTime - lastFrameTime) / 1000f
                lastFrameTime = frameTime

                if (particles.isNotEmpty()) {
                    particles = particles.map { p ->
                        ConfettiParticle(
                            x = p.x + p.vx,
                            y = p.y + p.vy,
                            vx = p.vx,
                            vy = p.vy + 0.1f, // gravity
                            color = p.color,
                            size = p.size,
                            rotation = p.rotation + p.rotationSpeed,
                            rotationSpeed = p.rotationSpeed
                        )
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (screenWidth == 0f) {
            screenWidth = size.width
        }
        
        particles.forEach { p ->
            if (p.y < size.height + 50f) { // Only draw if on screen
                rotate(degrees = p.rotation, pivot = Offset(p.x + p.size / 2, p.y + p.size / 2)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(p.x, p.y),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
