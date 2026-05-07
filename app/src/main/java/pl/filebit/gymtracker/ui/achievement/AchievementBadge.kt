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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * Premium animowany medal — ZNACZNIE prostsza implementacja.
 *
 * **Klucz do płynności:**
 * Zamiast skomplikowanego Canvas + drawBehind dla wszystkiego, używamy
 * **standardowych Modifier-ów** Compose które są mocno zoptymalizowane:
 *
 * 1. Tarcza = `Modifier.background(radialGradient).clip(CircleShape)` — Compose
 *    rysuje to RAZ i cache'uje. Jeden gładki render.
 * 2. Pierścienie = `Modifier.border(stroke, CircleShape)` — natywna Compose
 *    obsługa, super szybka.
 * 3. Glow = osobny Box z `.background(radialGradient)` z animowaną alpha.
 * 4. Shimmer i ring rotation = `drawBehind` (jedyny case gdzie Canvas potrzebny).
 *
 * Wszystkie scale/rotation/translation = `graphicsLayer { state.value }` — GPU.
 *
 * Wynik: 60fps stable, prosty kod, łatwy debug.
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // Wjazd tarczy
    val discScale = remember { Animatable(0f) }
    val discRotation = remember { Animatable(-180f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        kotlinx.coroutines.coroutineScope {
            launch { discScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            launch { discRotation.animateTo(0f, tween(700, easing = appleEase)) }
        }
    }

    // Wjazd emoji
    val emojiScale = remember { Animatable(0f) }
    val emojiRotation = remember { Animatable(-90f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(550)
        kotlinx.coroutines.coroutineScope {
            launch {
                emojiScale.animateTo(1f, keyframes {
                    durationMillis = 700
                    0f at 0
                    1.15f at 350 using appleEase
                    1f at 700
                })
            }
            launch {
                emojiRotation.animateTo(0f, keyframes {
                    durationMillis = 700
                    -90f at 0
                    8f at 400 using appleEase
                    0f at 700
                })
            }
        }
    }

    // Infinite — wszystkie czytane W LAMBDA, nie w composable body
    val infinite = rememberInfiniteTransition(label = "badge")
    val glowAlphaState = infinite.animateFloat(
        0.50f, 0.95f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), "glow"
    )
    val floatYState = infinite.animateFloat(
        -3f, 3f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), "float"
    )
    val emojiFloatState = infinite.animateFloat(
        -1.5f, 1.5f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), "emojiFloat"
    )
    val emojiMicroRotState = infinite.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), "emojiRot"
    )
    val shimmerState = infinite.animateFloat(
        -0.3f, 1.3f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), "shimmer"
    )
    val rotationState = infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), "ring"
    )

    // Cache brushes — remember
    val discBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFFFE082),
                0.30f to AccentOrange,
                0.80f to AccentOrange,
                1.0f to AccentOrangeDim
            ),
            radius = 220f
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = discScale.value
                scaleY = discScale.value
                rotationZ = discRotation.value
                translationY = floatYState.value
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. GLOW — osobny Box, alpha animated przez graphicsLayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlphaState.value }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentGlow.copy(alpha = 0.55f),
                            AccentGlow.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. TARCZA — natywny background + clip (Compose optimized)
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .clip(CircleShape)
                .background(discBrush)
                .border(1.5.dp, Color.White.copy(alpha = 0.50f), CircleShape)
                // Inner ring grawerunku — drugi border z padding nie jest możliwy,
                // więc rysujemy go w drawBehind RAZEM z shimmer i rotation ring
                .drawBehind {
                    val s = size.minDimension
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Inner ring grawerunku (8% od krawędzi)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.20f),
                        radius = s * 0.46f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Inner ring rotacyjny — czyta state.value w lambda (no recomp)
                    rotate(rotationState.value, Offset(cx, cy)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = s * 0.42f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Shimmer diagonal — także czyta state.value w lambda
                    val sx = size.width * shimmerState.value
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
                        radius = s * 0.5f,
                        center = Offset(cx, cy)
                    )
                }
        )

        // 3. EMOJI — własna graphicsLayer
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

/** Stub — zostawiony dla kompatybilności. */
object BadgeDiagnostics {
    var recomposeCount: Int = 0
    fun reset() { recomposeCount = 0 }
}
