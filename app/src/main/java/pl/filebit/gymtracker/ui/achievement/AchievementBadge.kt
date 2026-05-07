package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
 * Statyczny medal — v1.11.16. ZERO infinite animations.
 *
 * Tylko entry: fade-in + lekki scale 0.85→1 (700ms tween).
 * Po wjeździe medal jest STATYCZNY. Brak glow, brak shimmer, brak lewitowania.
 *
 * Powód: każda dodatkowa animacja powodowała lub artefakty wizualne
 * ("drugie koło", overflow z clip) lub stuttery na słabszych GPU.
 * Statyczny medal = niezawodne 60fps.
 */
@Composable
fun AchievementBadge(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val appleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        coroutineScope {
            launch { scale.animateTo(1f, tween(700, easing = appleEase)) }
            launch { alpha.animateTo(1f, tween(500, easing = appleEase)) }
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
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
        contentAlignment = Alignment.Center
    ) {
        // STATYCZNA TARCZA — jedyne koło, bez halo/shimmer/animacji
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

        // EMOJI — statyczne, bez animacji entry (fade odbywa się przez parent alpha)
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

/** Stub — kompatybilność. */
object BadgeDiagnostics {
    var recomposeCount: Int = 0
    fun reset() { recomposeCount = 0 }
}
