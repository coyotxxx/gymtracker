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
import pl.filebit.gymtracker.ui.theme.AccentGlow
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
import pl.filebit.gymtracker.ui.theme.DarkBg

/**
 * Premium animowany medal — 5 zasad performance + naprawa "drugiego koła":
 *
 * BUG NAPRAWIONY: shimmer Box był fillMaxSize z brush 0%-100% → biały gradient
 * widoczny ZAWSZE jako "drugie koło" w środku. Fix: pasek 35% szerokości,
 * brush transparent → biały → transparent (jak prawdziwy reflex).
 *
 * 1. graphicsLayer {} z LAMBDA (zero recompose, tylko draw)
 * 2. Brushe w remember {} (no re-create per frame)
 * 3. Shimmer pasek przez translationX (GPU translate)
 * 4. Spring LowBouncy + StiffnessMedium
 * 5. StartOffset zamiast LaunchedEffect+delay
 *
 * Optymalizacja v1.11.13: usunięto rotującą obręcz + emojiMicroRot
 * (4 infinite floats zamiast 6 → 33% mniej tickerów co frame).
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // === Wjazdy (jednorazowe) ===
    val discScale = remember { Animatable(0f) }
    val discRotation = remember { Animatable(-180f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        kotlinx.coroutines.coroutineScope {
            launch {
                discScale.animateTo(1f, spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                ))
            }
            launch { discRotation.animateTo(0f, tween(700, easing = appleEase)) }
        }
    }

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

    // === 4 infinite floats (zredukowane z 6) ===
    val infinite = rememberInfiniteTransition(label = "badge")

    val glowAlphaState = infinite.animateFloat(
        initialValue = 0.50f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val floatYState = infinite.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(1300)
        ),
        label = "float"
    )
    val emojiFloatState = infinite.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(1500)
        ),
        label = "emojiFloat"
    )
    val shimmerProgressState = infinite.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            initialStartOffset = StartOffset(1200)
        ),
        label = "shimmerProgress"
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
    val glowBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                AccentGlow.copy(alpha = 0.55f),
                AccentGlow.copy(alpha = 0.18f),
                Color.Transparent
            )
        )
    }
    // Wąski reflex — transparent → biały → transparent
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
        modifier = modifier
            .graphicsLayer {
                scaleX = discScale.value
                scaleY = discScale.value
                rotationZ = discRotation.value
                translationY = floatYState.value
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. GLOW — graphicsLayer alpha (BEZ recompose)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlphaState.value }
                .background(glowBrush)
        )

        // 2. TARCZA — clip Circle, zawiera disc + shimmer reflex
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .onSizeChanged { discBoxSize = it }
                .clip(CircleShape)
        ) {
            // Tarcza
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(discBrush)
                    .border(1.5.dp, Color.White.copy(alpha = 0.50f), CircleShape)
                    .drawBehind {
                        // Statyczny inner ring (premium 3D — bez animacji = bez drogi)
                        val s = size.minDimension
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.20f),
                            radius = s * 0.46f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.10f),
                            radius = s * 0.42f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            // 3. SHIMMER REFLEX — wąski pasek 35% szerokości, ślizga się przez tarczę
            // Pozycja translationX: -1×width (poza ekranem lewo) → 2×width (poza prawo)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .graphicsLayer {
                        translationX = shimmerProgressState.value * discBoxSize.width
                        rotationZ = 18f // lekkie pochylenie — bardziej naturalny reflex
                    }
                    .background(shimmerBrush)
            )
        }

        // 4. EMOJI
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = emojiScale.value
                scaleY = emojiScale.value
                rotationZ = emojiRotation.value
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
