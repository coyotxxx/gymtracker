package pl.filebit.gymtracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

/**
 * Splash sekwencyjny:
 * - 0–600ms: logo G fade-in + scale 0.7→1.0
 * - 600–1200ms: napis "GymTracker + hasło" fade-in pod logo
 * - 1200–2700ms: pasek ładowania wypełnia się
 * - 2700ms+: "Gotowe!" 200ms → onFinished → AppNavigation
 *
 * Tap → skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val textAlpha = remember { Animatable(0f) }
    val progressAlpha = remember { Animatable(0f) }
    val progressValue = remember { Animatable(0f) }
    var doneLabel by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(600, easing = EaseOutCubic)) }
            launch { logoScale.animateTo(1f, tween(700, easing = EaseOutCubic)) }
            launch {
                delay(600)
                textAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))
            }
            launch {
                delay(1200)
                progressAlpha.animateTo(1f, tween(300))
                progressValue.animateTo(1f, tween(1500, easing = LinearEasing))
                doneLabel = true
            }
        }
        delay(300)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .clickable { finishOnce() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Logo G — wypełnia szerokość ekranu (z padding kolumny)
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = "GymTracker logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(800f / 538f)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )

            // Cały blok napisów przesunięty w górę żeby zniwelować pusty obszar pyłu wokół G
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = (-48).dp)
            ) {
                Text(
                    text = "GymTracker",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = textAlpha.value),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = DarkOnSurfaceVariant.copy(alpha = textAlpha.value))) {
                            append("Twój trening. Twój ")
                        }
                        withStyle(SpanStyle(color = AccentOrange.copy(alpha = textAlpha.value))) {
                            append("progres.")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // Pasek ładowania
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .alpha(progressAlpha.value)
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

            Spacer(Modifier.height(12.dp))

            Text(
                if (doneLabel) "Gotowe!" else "Ładowanie",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = if (doneLabel) AccentOrange.copy(alpha = progressAlpha.value)
                else DarkOnSurfaceVariant.copy(alpha = progressAlpha.value)
            )
        }
    }
}
