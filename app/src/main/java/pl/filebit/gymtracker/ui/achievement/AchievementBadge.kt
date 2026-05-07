package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
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
import pl.filebit.gymtracker.ui.theme.DarkBg

/**
 * Premium medal — v1.11.18.
 *
 * Po znalezieniu PRAWDZIWEGO problemu (modal scrim alpha 0.88 przebijał tło)
 * przywracam piękny medal: gradient disc + border + inner ring + entry bounce.
 *
 * BEZ infinite animations w środku — żadnego shimmer/glow/floating, bo
 * graphicsLayer w children wychodził poza parent clip(CircleShape).
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    val discScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(300)
        discScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val emojiScale = remember { Animatable(0f) }
    val emojiRotation = remember { Animatable(-90f) }
    LaunchedEffect(Unit) {
        delay(550)
        coroutineScope {
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

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = discScale.value
            scaleY = discScale.value
        },
        contentAlignment = Alignment.Center
    ) {
        // TARCZA — gradient + border + inner ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
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

        // EMOJI z bounce entry
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
