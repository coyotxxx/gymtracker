package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.Achievement
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
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
            onDismiss = vm::dismiss,
            onShare = vm::onShareClick
        )
        AchievementUiState.Hidden -> Unit
    }
}

private val AppleEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun AchievementModal(
    achievement: Achievement,
    queueRemaining: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Karta wjazd — scale 0.85→1 z Apple bezier (0.2s start)
    val cardScale = remember { androidx.compose.animation.core.Animatable(0.85f) }
    val cardAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(achievement.id) {
        delay(200)
        coroutineScope {
            launch { cardScale.animateTo(1f, tween(700, easing = AppleEase)) }
            launch { cardAlpha.animateTo(1f, tween(500, easing = AppleEase)) }
        }
    }

    // Haptic — synchroniczny z animacją medalu i progresu
    LaunchedEffect(achievement.id) {
        delay(550)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(2050)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Auto-dismiss po 5s (dodatkowo do timeoutu w VM — defensywny)
    LaunchedEffect(achievement.id) {
        delay(6500)
        // VM również wyzwala dismiss — to backup
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            BreathingBackground()

            // Close button (prawy górny)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Zamknij",
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

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

            // Content card (centered) — z wjazdem scale 0.85→1
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        alpha = cardAlpha.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // zatrzymuje dismiss przy kliknięciu w content
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLabel(modifier = Modifier.padding(bottom = 28.dp))

                AchievementBadge(
                    emoji = achievement.emoji,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(bottom = 40.dp)
                )

                AnimatedTitle(text = achievement.title)
                Spacer(Modifier.height(12.dp))
                AnimatedDescription(text = achievement.description)
                Spacer(Modifier.height(36.dp))

                AchievementProgress(
                    current = achievement.currentValue,
                    target = achievement.targetValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 280.dp)
                        .padding(bottom = 24.dp)
                )

                AnimatedActions(
                    onConfirm = onDismiss,
                    onShare = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 280.dp)
                )
            }
        }
    }
}

@Composable
private fun BreathingBackground() {
    val infinite = rememberInfiniteTransition(label = "bg")
    val intensity by infinite.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgIntensity"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentOrange.copy(alpha = intensity),
                        AccentOrangeDim.copy(alpha = intensity * 0.45f),
                        Color.Transparent
                    ),
                    radius = 900f
                )
            )
    )
}

@Composable
private fun AnimatedLabel(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 14 }
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .height(1.dp)
                    .padding(horizontal = 0.dp)
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
}

@Composable
private fun AnimatedTitle(text: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(700); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 14 }
    ) {
        Text(
            text = text,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            color = DarkOnSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnimatedDescription(text: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(850); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 14 }
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
private fun AnimatedActions(
    onConfirm: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1150); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 14 }
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = DarkBg
                )
            ) {
                Text(
                    "Świetnie!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Udostępnij",
                    tint = DarkOnSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
