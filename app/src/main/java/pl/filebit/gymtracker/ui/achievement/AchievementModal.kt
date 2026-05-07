package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.AnimatedVisibility
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
            onDismiss = vm::dismiss,
            onShare = vm::onShareClick
        )
        AchievementUiState.Hidden -> Unit
    }
}

@Composable
private fun AchievementModal(
    achievement: Achievement,
    queueRemaining: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Tylko JEDEN haptic na otwarcie — drugi po 2.6s usuniety (powodowal puls)
    LaunchedEffect(achievement.id) {
        delay(300)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // USUNIETO v1.11.19:
    // - cardScale + cardAlpha animation → graphicsLayer w Column tworzyl
    //   offscreen GPU layer dla calego contentu, subpixel sampling przy
    //   scale produkowal HALO/aliasing edges = "drugie kolo" wokol medalu.
    // - Drugi haptic po 2.05s (powodowal puls, kolejny stutter).
    // - Martwy LaunchedEffect z delay(6500) bez akcji.

    // Box overlay zamiast Dialog — wyzwala render w tym samym window co reszta UI
    // (zero overhead nowego DecorView/Surface, ~150ms szybsze otwarcie + lepszy fps)
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // PEŁNA czerń (alpha=1.0) — alpha 0.88 powodował że pomarańczowe ikony
            // BigAchievementRow z listy w tle przebijały się przez scrim i wyglądały
            // jak "drugie koło" wokół medalu. To był prawdziwy bug, nie medal.
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // BreathingBackground USUNIĘTY w v1.11.15 — statyczny scrim zamiast animowanego halo

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

            // Tag wersji — diagnostic, do weryfikacji że user testuje aktualny APK
            Text(
                text = "v1.11.19",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 12.dp)
            )

            // Content card — BEZ graphicsLayer scale (powodowal "drugie kolo")
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
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

// BreathingBackground USUNIĘTY w v1.11.15 — był głównym źródłem stutterów

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
