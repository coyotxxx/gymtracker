package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ui.theme.AccentGlow
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
import pl.filebit.gymtracker.ui.theme.DarkBg

/**
 * Premium animowany medal — pełen 60fps na debug build.
 *
 * **Krytyczna optymalizacja Compose performance (v1.11.9):**
 * - Animacje state zwracane z `infinite.animateFloat(...)` (bez `by`) —
 *   composable NIE czyta wartości w body, więc state changes nie triggerują
 *   recomposition.
 * - Wartości czytane DOPIERO w lambda block: `graphicsLayer { ... }`,
 *   `drawWithCache { ... }`, `drawBehind { ... }`.
 * - Compose wykrywa że state.value czytany w deferred lambda → uruchamia tylko
 *   fazę DRAW, omija COMPOSE i LAYOUT phase.
 *
 * Wynik: 60fps stable, brak recomposition cycles per frame.
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // Spring entry — czytane TYLKO w lambda
    val discScale = remember { Animatable(0f) }
    val discRotation = remember { Animatable(-180f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        kotlinx.coroutines.coroutineScope {
            launch {
                discScale.animateTo(1f, spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ))
            }
            launch {
                discRotation.animateTo(0f, tween(700, easing = appleEase))
            }
        }
    }

    val emojiScale = remember { Animatable(0f) }
    val emojiRotation = remember { Animatable(-90f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(550)
        kotlinx.coroutines.coroutineScope {
            launch {
                emojiScale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 700
                        0f at 0
                        1.15f at 350 using appleEase
                        1f at 700
                    }
                )
            }
            launch {
                emojiRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 700
                        -90f at 0
                        8f at 400 using appleEase
                        0f at 700
                    }
                )
            }
        }
    }

    // Infinite transitions — BEZ `by`, czytane w lambda
    val infinite = rememberInfiniteTransition(label = "badge")
    val glowAlphaState = infinite.animateFloat(
        initialValue = 0.50f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val floatYState = infinite.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )
    val emojiFloatState = infinite.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "emojiFloat"
    )
    val emojiMicroRotState = infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "emojiRot"
    )
    val shimmerState = infinite.animateFloat(
        initialValue = -0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val rotationState = infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )

    Box(
        modifier = modifier
            // graphicsLayer lambda — czyta state, ale tylko draw phase
            .graphicsLayer {
                scaleX = discScale.value
                scaleY = discScale.value
                rotationZ = discRotation.value
                translationY = floatYState.value
            }
            // drawWithCache lambda — czyta state w onDrawBehind, BRAK recomposition
            .drawWithCache {
                val s = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f

                val discBrush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFFFE082),
                        0.30f to AccentOrange,
                        0.80f to AccentOrange,
                        1.0f to AccentOrangeDim
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.42f),
                    radius = s * 0.42f
                )

                onDrawBehind {
                    // Wszystkie state.value czytane TUTAJ — tylko draw phase
                    val glowAlpha = glowAlphaState.value
                    val shimmer = shimmerState.value
                    val rotation = rotationState.value

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

                    drawCircle(
                        brush = discBrush,
                        radius = s * 0.39f,
                        center = Offset(cx, cy)
                    )

                    rotate(rotation, Offset(cx, cy)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = s * 0.32f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    val sx = size.width * shimmer
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.32f),
                                Color.Transparent
                            ),
                            start = Offset(sx - size.width * 0.2f, 0f),
                            end = Offset(sx + size.width * 0.2f, size.height)
                        ),
                        radius = s * 0.39f,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.50f),
                        radius = s * 0.39f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.20f),
                        radius = s * 0.355f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = emojiScale.value
                scaleY = emojiScale.value
                rotationZ = emojiRotation.value + emojiMicroRotState.value
                translationY = emojiFloatState.value
            }
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
}
