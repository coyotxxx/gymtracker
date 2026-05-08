package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant

/**
 * Globalny host modala odznak. Wstaw raz w MainActivity nad NavHost.
 * VM trzyma kolejkę — gdy user zdobywa wiele odznak naraz, pokazujemy je sekwencyjnie.
 */
@Composable
fun AchievementModalHost(
    vm: AchievementViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    when (val s = state) {
        is AchievementUiState.Showing -> AchievementModal(
            achievement = s.achievement,
            queueRemaining = s.queueRemaining,
            onDismiss = vm::dismiss
        )
        AchievementUiState.Hidden -> Unit
    }
}

@Composable
private fun AchievementModal(
    achievement: Achievement,
    queueRemaining: Int,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Haptic na otwarcie modal
    LaunchedEffect(achievement.id) {
        delay(300)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // === Fade-in scrim 0→1 ===
    val scrimAlpha = remember { Animatable(0f) }
    LaunchedEffect(achievement.id) {
        scrimAlpha.animateTo(1f, tween(250))
    }

    // === Card slide-in: translationY 30dp → 0 (czytany w lambda → zero recompose) ===
    val cardTranslateY = remember { Animatable(30f) }
    LaunchedEffect(achievement.id) {
        delay(150)
        cardTranslateY.animateTo(0f, tween(700, easing = AppleEase))
    }

    // Halo stops — fade do 0 na 75% radius (jak CSS "ellipse 90% 70%")
    // Bardziej miękki niż wcześniej, intensywniejsze centrum, dłuższy fade.
    val haloStops = remember {
        arrayOf(
            0.0f to AccentOrange.copy(alpha = 0.50f),
            0.15f to AccentOrange.copy(alpha = 0.35f),
            0.30f to AccentOrange.copy(alpha = 0.22f),
            0.50f to AccentOrange.copy(alpha = 0.10f),
            0.75f to Color.Transparent,
            1.0f to Color.Transparent
        )
    }

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrimAlpha.value }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // AMBIENT HALO — kołowy radial gradient z DUŻYM radius (sięga rogów ekranu)
        // BEZ scale (scale w drawScope obcinało rect do 58% szerokości)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // Radius = przekątna połówki = sięga rogów ekranu
                    val maxR = kotlin.math.sqrt(
                        (size.width * size.width + size.height * size.height).toDouble()
                    ).toFloat() / 2f
                    val haloBrush = Brush.radialGradient(
                        colorStops = haloStops,
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = maxR
                    )
                    onDrawBehind {
                        drawRect(haloBrush)
                    }
                }
        )

            // (X close button USUNIETY w v1.11.32 — tap-to-dismiss na calym ekranie)

            // Queue indicator (jeśli więcej w kolejce)
            if (queueRemaining > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 56.dp, start = 24.dp)
                        .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ $queueRemaining w kolejce",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AccentOrange
                    )
                }
            }

            // Tag wersji — diagnostic, do weryfikacji że user testuje aktualny APK
            Text(
                text = "v1.11.40",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 12.dp)
            )

            // Content card — translationY przez graphicsLayer
            // BEZ clickable na Column (tap-to-dismiss na calym ekranie z parent Box)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .graphicsLayer { translationY = cardTranslateY.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLabel(
                    delayMs = 100,
                    modifier = Modifier.padding(bottom = 28.dp)
                )

                AchievementBadge(
                    emoji = achievement.emoji,
                    modifier = Modifier.size(240.dp)  // halo przeniesiony na poziom modal (cały ekran)
                )
                Spacer(Modifier.height(36.dp))

                AnimatedTitle(text = achievement.title, delayMs = 700)
                Spacer(Modifier.height(12.dp))
                AnimatedDescription(text = achievement.description, delayMs = 850)
                Spacer(Modifier.height(28.dp))
                AnimatedCongrats(delayMs = 1050)
            }
    }
}

// === Plynne entry przez graphicsLayer.alpha (state.value w lambda → zero recompose) ===
private val AppleEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun rememberFadeInAlpha(delayMs: Int): Animatable<Float, *> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        alpha.animateTo(1f, tween(500, easing = AppleEase))
    }
    return alpha
}

@Composable
private fun AnimatedLabel(delayMs: Int, modifier: Modifier = Modifier) {
    val alphaAnim = rememberFadeInAlpha(delayMs)
    Row(
        modifier = modifier.graphicsLayer { alpha = alphaAnim.value },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, AccentOrange))
                )
                .size(width = 24.dp, height = 1.dp)
        )
        Text(
            text = "ODZNAKA ODBLOKOWANA",
            color = AccentOrange,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.4.sp
        )
        Box(
            Modifier
                .size(width = 24.dp, height = 1.dp)
                .background(
                    Brush.horizontalGradient(listOf(AccentOrange, Color.Transparent))
                )
        )
    }
}

@Composable
private fun AnimatedTitle(text: String, delayMs: Int) {
    val alphaAnim = rememberFadeInAlpha(delayMs)
    Text(
        text = text,
        fontSize = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
        color = DarkOnSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.graphicsLayer { alpha = alphaAnim.value }
    )
}

@Composable
private fun AnimatedDescription(text: String, delayMs: Int) {
    val alphaAnim = rememberFadeInAlpha(delayMs)
    Text(
        text = text,
        fontSize = 14.sp,
        color = DarkOnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .graphicsLayer { alpha = alphaAnim.value }
    )
}

@Composable
private fun AnimatedCongrats(delayMs: Int) {
    val alphaAnim = rememberFadeInAlpha(delayMs)
    Text(
        text = "Świetna robota! 💪",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = AccentOrange,
        textAlign = TextAlign.Center,
        modifier = Modifier.graphicsLayer { alpha = alphaAnim.value }
    )
}
