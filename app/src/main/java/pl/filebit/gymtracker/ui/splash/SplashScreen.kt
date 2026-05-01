package pl.filebit.gymtracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Compose splash. ~3 sekundy:
 * - 0–700ms: logo G fade-in + scale 0.6→1.0 + halo żółte
 * - 700–1300ms: tytuł GymTracker
 * - 1300–1800ms: subtitle "Twój trening. Twój progres."
 * - 1800–3000ms: pasek ładowania + "Gotowy?"
 * Tap → skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val progressAlpha = remember { Animatable(0f) }
    val progressValue = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(700, easing = EaseOutCubic)) }
            launch { logoScale.animateTo(1f, tween(700, easing = EaseOutCubic)) }
            launch { particleProgress.animateTo(1f, tween(900, easing = EaseOutCubic)) }
            launch {
                delay(700)
                titleAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))
            }
            launch {
                delay(1300)
                subtitleAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))
            }
            launch {
                delay(1800)
                progressAlpha.animateTo(1f, tween(300))
                progressValue.animateTo(1f, tween(1100, easing = LinearEasing))
            }
        }
        delay(200)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .clickable { finishOnce() },
        contentAlignment = Alignment.Center
    ) {
        // Halo żółte za logo (radial gradient)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val haloR = size.minDimension * 0.40f * (0.7f + 0.3f * logoAlpha.value)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentOrange.copy(alpha = 0.28f * logoAlpha.value),
                        AccentOrange.copy(alpha = 0.10f * logoAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloR
                ),
                radius = haloR,
                center = center
            )
        }

        // Cząsteczki — 12 punktów wokół logo, rozsuwają się radialnie
        Canvas(modifier = Modifier.size(320.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.32f
            for (i in 0 until 12) {
                val seed = abs(sin((i * 127.1f + 311.7f).toDouble())).toFloat()
                val angle = (i / 12f) * 2f * PI.toFloat()
                val radius = baseR * (0.7f + seed * 0.5f) *
                    (0.4f + 0.6f * particleProgress.value)
                val x = center.x + cos(angle) * radius
                val y = center.y + sin(angle) * radius
                val alpha = (1f - particleProgress.value * 0.5f) * logoAlpha.value
                drawCircle(
                    color = AccentOrange.copy(alpha = alpha),
                    radius = 3f + seed * 4f,
                    center = Offset(x, y)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo "G" w żółtym kwadracie
            Box(
                modifier = Modifier
                    .size((96 * logoScale.value).dp.coerceAtLeast(0.dp))
                    .background(
                        AccentOrange.copy(alpha = logoAlpha.value),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "G",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 64.sp,
                        letterSpacing = (-2).sp
                    ),
                    color = Color.Black.copy(alpha = logoAlpha.value)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "GymTracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = DarkOnSurface.copy(alpha = titleAlpha.value)
            )

            Spacer(Modifier.height(8.dp))

            // Subtitle: część "progres." w AccentOrange dla akcentu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Twój trening. Twój ",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = DarkOnSurfaceVariant.copy(alpha = subtitleAlpha.value)
                )
                Text(
                    "progres.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = AccentOrange.copy(alpha = subtitleAlpha.value)
                )
            }

            Spacer(Modifier.height(36.dp))

            Box(modifier = Modifier.width(160.dp)) {
                LinearProgressIndicator(
                    progress = { progressValue.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentOrange.copy(alpha = progressAlpha.value),
                    trackColor = DarkSurfaceVariant.copy(alpha = progressAlpha.value),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                if (progressValue.value < 1f) "Gotowy?" else "Gotowe!",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant.copy(alpha = progressAlpha.value)
            )
        }
    }
}
