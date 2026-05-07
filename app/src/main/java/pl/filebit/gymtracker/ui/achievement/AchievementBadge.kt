package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentGlow
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkSurface3

/**
 * Animowany medal odznaki — radial gradient w stylu app (AccentOrange).
 *
 * Animacje:
 * 1. Scale 0 → 1.08 → 1 (spring bouncy) z opóźnieniem dla bouncyness
 * 2. Glow pulse w pętli (0.6 → 1.0 alpha)
 * 3. Float ±4dp (subtle bobbing)
 * 4. Shimmer — diagonalny pasek światła co 3s
 * 5. Inner ring rotation — wolna rotacja (40s/360°)
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    // === Wjazd: scale spring ===
    val entryScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // === Pulse glow ===
    val infinite = rememberInfiniteTransition(label = "badge")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // === Subtle float ===
    val floatY by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // === Shimmer position 0..1 ===
    val shimmer by infinite.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // === Inner ring rotation ===
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = entryScale.value
            scaleY = entryScale.value
            // Float bobbing — GPU translation, bez recomposition
            translationY = floatY
        },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow (większy obszar, niżej w stosie)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentGlow.copy(alpha = glowAlpha * 0.55f),
                        AccentGlow.copy(alpha = glowAlpha * 0.18f),
                        Color.Transparent
                    ),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f
            )
        }

        // Tarcza medalu — radial gradient
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .clip(CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to AccentOrange,
                            0.45f to AccentOrange,
                            0.85f to AccentOrangeDim,
                            1.0f to DarkSurface3
                        ),
                        center = Offset(size.width * 0.30f, size.height * 0.28f),
                        radius = size.minDimension * 0.85f
                    )
                )
                // Inner ring (rotujący)
                rotate(rotation) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = size.minDimension * 0.42f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // Shimmer — diagonal light
                val sx = size.width * shimmer
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        start = Offset(sx - size.width * 0.2f, 0f),
                        end = Offset(sx + size.width * 0.2f, size.height)
                    )
                )
                // Highlight ring (cienki, bardzo jasny)
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.49f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Emoji w środku
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emoji,
                    style = TextStyle(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = DarkBg.copy(alpha = 0.6f),
                            offset = Offset(0f, 4f),
                            blurRadius = 8f
                        )
                    )
                )
            }
        }
    }
}

