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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim

/**
 * Premium animowany medal — v1.11.22.
 *
 * ZASADY (z 8 release'y nauki):
 * 1. graphicsLayer state.value TYLKO w lambda → zero recompose, tylko draw
 * 2. ZAKAZ: graphicsLayer scale na Column z dziećmi (offscreen halo)
 * 3. ZAKAZ: gradient z plateau (krawędź = drugie koło)
 * 4. ZAKAZ: shimmer Box z rotation (wychodzi poza clip)
 * 5. ZAKAZ: padding INSIDE size (elipsa zamiast koła)
 *
 * Co tu jest:
 * - Płynny gradient (2 stops, bez plateau) → "premium" wygląd
 * - Subtelny biały border 1dp alpha 0.2 → ledwo widoczny, brak iluzji
 * - Spring scale entry (LowBouncy + StiffnessMedium = bez przesadnego overshoot)
 * - Emoji bounce keyframes 0→1.15→1
 * - Subtelne floating infinite (translationY -2↔2px) → 1 infinite, czyte w lambda
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // === Entry: disc scale (jednorazowo) ===
    val discScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200)
        discScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // === Entry: emoji bounce (jednorazowo) ===
    val emojiScale = remember { Animatable(0f) }
    val emojiRotation = remember { Animatable(-45f) }
    LaunchedEffect(Unit) {
        delay(450)
        coroutineScope {
            launch {
                emojiScale.animateTo(1f, keyframes {
                    durationMillis = 600
                    0f at 0
                    1.15f at 300 using appleEase
                    1f at 600
                })
            }
            launch {
                emojiRotation.animateTo(0f, tween(600, easing = appleEase))
            }
        }
    }

    // === Subtelne floating (1 infinite, czyte w lambda → zero recompose) ===
    val infinite = rememberInfiniteTransition(label = "badge")
    val floatYState = infinite.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // === Pulsing highlight (subtelne pulsowanie jaśniejszego centrum) ===
    val highlightAlpha = infinite.animateFloat(
        initialValue = 0.0f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlight"
    )

    // === Outer glow alpha — subtelne "oddychanie" poswiaty wokol medalu ===
    val glowAlpha = infinite.animateFloat(
        initialValue = 0.65f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // === Brushe w remember (zero recreate) ===
    // Mocny gradient 4 stops — od jasnego żółtego centra przez pomarańczowy
    // do ciemnobrązowej krawędzi. BEZ plateau (przejścia w każdym przedziale).
    val discBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFFFF3C4),   // bardzo jasny żółty (highlight)
                0.35f to Color(0xFFFFC107),  // jasny pomarańcz
                0.75f to AccentOrange,        // pełny pomarańcz
                1.0f to Color(0xFF8B5A00)    // ciemny brąz (krawędź)
            )
        )
    }
    // Outer glow brush — pomarancowy halo wokol medalu (jak w "Legendarny")
    // Tarcza zajmuje 0-55% radius (0.55 fillMaxSize). Halo w 55%-100% (45% prostranstwa).
    val glowBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to AccentOrange.copy(alpha = 0.85f),
                0.30f to AccentOrange.copy(alpha = 0.70f),
                0.55f to AccentOrange.copy(alpha = 0.50f),  // tuz za krawedzia tarczy
                0.72f to AccentOrange.copy(alpha = 0.28f),
                0.88f to AccentOrange.copy(alpha = 0.10f),
                1.0f to Color.Transparent
            )
        )
    }
    // Highlight overlay (subtle pulsing white center) — Brush w remember
    val highlightBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = 0.6f),
                0.4f to Color.White.copy(alpha = 0.15f),
                0.7f to Color.Transparent,
                1.0f to Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                // state czytany w lambda → tylko draw, zero recompose
                scaleX = discScale.value
                scaleY = discScale.value
                translationY = floatYState.value
            },
        contentAlignment = Alignment.Center
    ) {
        // OUTER GLOW PRZENIESIONY na poziom AchievementModal (Box overlay).
        // Tam ma fillMaxSize calego ekranu — halo wystaje poza modal padding.

        // TARCZA — 75% z 320dp parent = 240dp (preferowany rozmiar)
        Box(
            modifier = Modifier
                .fillMaxSize(0.75f)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    ambientColor = AccentOrange,
                    spotColor = AccentOrange
                )
                .clip(CircleShape)
                .background(discBrush)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .drawWithCache {
                    // Brushes cached — przelicza tylko przy zmianie size, nie co frame
                    val s = size.minDimension
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val highlightOffsetCenter = Offset(size.width * 0.32f, size.height * 0.30f)
                    val highlightRadius = s * 0.40f
                    val highlightBrushOff = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = highlightOffsetCenter,
                        radius = highlightRadius
                    )
                    onDrawBehind {
                        // Off-center highlight — lustrowy odblask u góry-lewej
                        drawCircle(
                            brush = highlightBrushOff,
                            radius = highlightRadius,
                            center = highlightOffsetCenter
                        )
                        // Inner ring — jaśniejszy premium 3D feel
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = s * 0.44f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
        ) {
            // Pulsing highlight overlay — alpha animowana w lambda graphicsLayer
            // (zero recompose, tylko draw). Highlight pulsuje 0.0 ↔ 0.25 alpha.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = highlightAlpha.value }
                    .background(highlightBrush)
            )
        }

        // EMOJI — własny graphicsLayer (scale + rotation entry)
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = emojiScale.value
                scaleY = emojiScale.value
                rotationZ = emojiRotation.value
            }
        ) {
            Text(
                text = emoji,
                style = TextStyle(
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    // Subtelny cień pod emoji — premium 3D depth
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(0f, 3f),
                        blurRadius = 6f
                    )
                )
            )
        }
    }
}

/** Stub — kompatybilność. */
object BadgeDiagnostics {
    var recomposeCount: Int = 0
    fun reset() { recomposeCount = 0 }
}
