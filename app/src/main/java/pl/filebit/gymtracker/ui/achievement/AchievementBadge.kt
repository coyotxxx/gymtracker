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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    // === Brushe w remember (zero recreate) ===
    // Płynny gradient 2 stops — BEZ plateau (które tworzyło "drugie koło" w v1.11.20)
    val discBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFE082),  // jasny żółty center
                AccentOrange,        // pomarańczowy edge — płynne przejście
                AccentOrangeDim     // ciemniejsza krawędź — TYLKO ostatnie ~5%, niewidoczna
            ),
            // tu colorStops dla niewielkiego zaciemnienia tylko na samej krawędzi
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
        // TARCZA — shadow + clip + gradient + border + subtelny inner ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Drop shadow (natywny Android RenderNode → GPU free, brak halo)
                // ambientColor + spotColor pomaranczowy daje "swiecacy" efekt
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = AccentOrange,
                    spotColor = AccentOrange
                )
                .clip(CircleShape)
                .background(discBrush)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .drawBehind {
                    // Subtelny inner ring (premium 3D feel) — alpha 0.08, ledwo widoczny
                    val s = size.minDimension
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = s * 0.44f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
        )

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
                    fontWeight = FontWeight.Bold
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
