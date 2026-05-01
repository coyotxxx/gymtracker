package pl.filebit.gymtracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Splash z gotową grafiką brand (logo G + GymTracker + hasło).
 * - 0–500ms: fade-in obrazu
 * - 500–2200ms: pasek ładowania wypełnia się
 * - 2200ms: onFinished
 * Tap → skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val imageAlpha = remember { Animatable(0f) }
    val progressAlpha = remember { Animatable(0f) }
    val progressValue = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                imageAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))
            }
            launch {
                delay(500)
                progressAlpha.animateTo(1f, tween(300))
                progressValue.animateTo(1f, tween(1500, easing = LinearEasing))
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
        Image(
            painter = painterResource(R.drawable.splash_brand),
            contentDescription = "GymTracker",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(imageAlpha.value)
        )

        // Pasek ładowania — na dole, nad bottom edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
                .alpha(progressAlpha.value),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier.width(180.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progressValue.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentOrange,
                    trackColor = DarkSurfaceVariant.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}
