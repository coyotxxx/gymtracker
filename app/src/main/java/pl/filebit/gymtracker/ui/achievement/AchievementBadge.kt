package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
import pl.filebit.gymtracker.ui.theme.DarkBg

/**
 * Premium animowany medal — radykalnie uproszczony dla niezawodnej płynności.
 *
 * Co zostalo USUNIĘTE w v1.11.15:
 * - GLOW (Box z radialGradient wokół) — to było "drugie koło"
 * - Infinite floats: glowAlpha, floatY, emojiFloat (4 → 1 tylko shimmer)
 * - Drugi inner ring (drawBehind drawCircle × 2 → 1)
 *
 * Co zostalo:
 * - Entry animation: disc spring scale + emoji bounce in (jednorazowo)
 * - Shimmer reflex pasek (subtelna ciągła animacja, GPU translate)
 * - Statyczny medal po wjeździe — bez "lewitowania"
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // === Wjazd disc (jednorazowo) ===
    val discScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        discScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // === Wjazd emoji (jednorazowo) ===
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

    // === JEDYNA infinite animation: shimmer reflex ===
    val infinite = rememberInfiniteTransition(label = "badge")
    val shimmerProgressState = infinite.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            initialStartOffset = StartOffset(1500)
        ),
        label = "shimmer"
    )

    // === Brushe w remember ===
    val discBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFFFE082),
                0.30f to AccentOrange,
                0.80f to AccentOrange,
                1.0f to AccentOrangeDim
            )
        )
    }
    val shimmerBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.55f),
                Color.Transparent
            )
        )
    }

    var discBoxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = discScale.value
            scaleY = discScale.value
        },
        contentAlignment = Alignment.Center
    ) {
        // TARCZA (clip Circle) — jedyne koło, bez glow
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .onSizeChanged { discBoxSize = it }
                .clip(CircleShape)
        ) {
            // Disc background + jeden subtelny inner ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(discBrush)
                    .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    .drawBehind {
                        val s = size.minDimension
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f),
                            radius = s * 0.44f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            // Shimmer reflex — wąski pasek 32% szerokości, GPU translate
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.32f)
                    .graphicsLayer {
                        translationX = shimmerProgressState.value * discBoxSize.width
                        rotationZ = 18f
                    }
                    .background(shimmerBrush)
            )
        }

        // EMOJI — entry only, bez floating
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
                    shadow = Shadow(
                        color = DarkBg.copy(alpha = 0.5f),
                        offset = Offset(0f, 3f),
                        blurRadius = 8f
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
