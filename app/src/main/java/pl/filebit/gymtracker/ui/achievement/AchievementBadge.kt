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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.rotate
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
 * Premium animowany medal — pełen 60fps zgodnie z 5 zasadami performance:
 *
 * 1. graphicsLayer {} z LAMBDA (defer state read do GPU draw, skip recomposition)
 * 2. Wszystkie Brushe w remember {} (no re-create co frame)
 * 3. Shimmer przez Box.graphicsLayer { translationX } (GPU translate, nie Canvas)
 * 4. Spring twardszy: LowBouncy + StiffnessMedium (krótszy czas → mniej kosztu)
 * 5. StartOffset zamiast LaunchedEffect+delay dla pętli
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // === Wjazdy (jednorazowe — mogą zostać LaunchedEffect+delay) ===
    val discScale = remember { Animatable(0f) }
    val discRotation = remember { Animatable(-180f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        kotlinx.coroutines.coroutineScope {
            launch {
                // Spring twardszy — krótszy, mniej kosztu (Zasada B)
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

    // === Infinite transitions z StartOffset (Zasada A) ===
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
    val emojiMicroRotState = infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(1500)
        ),
        label = "emojiRot"
    )
    val shimmerProgressState = infinite.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            initialStartOffset = StartOffset(1200)
        ),
        label = "shimmerProgress"
    )
    val rotationState = infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    // === Brushe w remember (Zasada 2) ===
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
    val shimmerBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.40f),
                Color.Transparent
            )
        )
    }

    // Box size — dla shimmer translationX
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .graphicsLayer {
                scaleX = discScale.value
                scaleY = discScale.value
                rotationZ = discRotation.value
                translationY = floatYState.value
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. GLOW — animowane alpha przez graphicsLayer (BEZ recompose)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlphaState.value }
                .background(glowBrush)
        )

        // 2. TARCZA + shimmer overlay + ring rotation
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .clip(CircleShape)
        ) {
            // Tarcza — natywny background (Compose optimized)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(discBrush)
                    .border(1.5.dp, Color.White.copy(alpha = 0.50f), CircleShape)
                    .drawBehind {
                        val s = size.minDimension
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        // Inner ring grawerunku (statyczny — premium 3D feel)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.20f),
                            radius = s * 0.46f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Inner ring rotacyjny — state.value w lambda
                        rotate(rotationState.value, Offset(cx, cy)) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.18f),
                                radius = s * 0.42f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
            )

            // 3. SHIMMER overlay — translationX (Zasada 3, GPU translate)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = shimmerProgressState.value * boxSize.width
                    }
                    .background(shimmerBrush)
            )
        }

        // 4. EMOJI — własna graphicsLayer
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
