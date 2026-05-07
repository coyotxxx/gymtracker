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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
 * Animowany medal odznaki — w pełni GPU-accelerated (graphicsLayer + drawBehind).
 *
 * **Performance:**
 * - graphicsLayer dla scale/translation — pomija recomposition + measure
 *   (operuje wyłącznie na compose layer)
 * - drawWithCache + drawBehind dla wszystkich kolorów/gradientów —
 *   cachowane brush'e, tylko faza DRAW na każdej klatce, BEZ recomposition
 * - 1 Box zamiast 3 (outer Canvas + inner Box + inner Canvas)
 * - Brushy gradient cached przez drawWithCache (nie rebuilded co frame)
 *
 * Animacje (wszystkie odczytywane wewnątrz drawBehind, nie wymagają recomp):
 *   1. Wjazd: spring bouncy scale 0 → 1.08 → 1
 *   2. Glow pulse: 1.8s reverse loop (alpha 0.55 ↔ 0.95)
 *   3. Float bobbing: ±3px, 2.4s reverse loop
 *   4. Shimmer diagonal: -0.3 → 1.3 (linear), 3.5s loop
 *   5. Inner ring rotation: 360°, 40s linear loop
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    // === Spring entry (1× przy starcie) ===
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

    // === Infinite animations — wszystko z jednej InfiniteTransition (jeden ticker) ===
    val infinite = rememberInfiniteTransition(label = "badge")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val floatY by infinite.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )

    Box(
        modifier = modifier
            // Wszystkie transformacje GPU-accelerated:
            .graphicsLayer {
                scaleX = entryScale.value
                scaleY = entryScale.value
                translationY = floatY
            }
            // Cache brushes — przebudowane TYLKO przy zmianie size, nie animacji
            .drawWithCache {
                val s = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Tarcza — soft highlight in center fading to deeper gold (jak referencja)
                val discBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFFFE082),         // jasny żółty highlight w środku
                        0.25f to AccentOrange,             // pełen gold/amber
                        0.75f to AccentOrange,
                        1.0f to AccentOrangeDim            // głęboki amber na krawędzi
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.42f),
                    radius = s * 0.42f
                )

                onDrawBehind {
                    // 1. Outer glow (poza dyskiem) — alpha modulated by glowAlpha
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentGlow.copy(alpha = glowAlpha * 0.55f),
                                AccentGlow.copy(alpha = glowAlpha * 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = s * 0.55f
                        ),
                        radius = s * 0.55f,
                        center = Offset(cx, cy)
                    )

                    // 2. Tarcza medalu (cached brush)
                    drawCircle(
                        brush = discBrush,
                        radius = s * 0.39f,
                        center = Offset(cx, cy)
                    )

                    // 3. Inner ring rotacyjny
                    rotate(rotation, Offset(cx, cy)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = s * 0.32f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // 4. Shimmer — diagonal light wewnątrz dysku
                    val sx = size.width * shimmer
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.30f),
                                Color.Transparent
                            ),
                            start = Offset(sx - size.width * 0.2f, 0f),
                            end = Offset(sx + size.width * 0.2f, size.height)
                        ),
                        radius = s * 0.39f,
                        center = Offset(cx, cy)
                    )

                    // 5. Highlight ring — cienki, jasny
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = s * 0.39f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = TextStyle(
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = DarkBg.copy(alpha = 0.6f),
                    offset = Offset(0f, 4f),
                    blurRadius = 12f
                )
            )
        )
    }
}
