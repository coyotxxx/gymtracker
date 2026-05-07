package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.AccentOrangeDim
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface

/**
 * Animowany pasek progresu — od 0 do (current/target × 100)% w 1.5s,
 * z opóźnieniem 600ms (po wjeździe medalu).
 */
@Composable
fun AchievementProgress(
    current: Long,
    target: Long,
    modifier: Modifier = Modifier
) {
    val pct = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 1f
    val animPct = remember { Animatable(0f) }

    LaunchedEffect(current, target) {
        kotlinx.coroutines.delay(1200)
        animPct.animateTo(pct, animationSpec = tween(1400))
    }

    // Shimmer wewnątrz paska — premium gloss effect
    val infinite = rememberInfiniteTransition(label = "barShimmer")
    val barShimmer by infinite.animateFloat(
        initialValue = -0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "barSh"
    )

    Column(modifier = modifier) {
        // Label "TWÓJ POSTĘP" + duża wartość "X / Y" (jak w referencji)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TWÓJ POSTĘP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp
                ),
                color = AccentOrange
            )
        }
        Spacer(Modifier.height(10.dp))
        // Track + fill + shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            // Fill (gradient gold)
            Box(
                modifier = Modifier
                    .fillMaxWidth(animPct.value)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentOrangeDim, AccentOrange, AccentOrangeDim)
                        )
                    )
                    .drawWithCache {
                        // Shimmer wewnątrz wypełnionego obszaru
                        onDrawWithContent {
                            drawContent()
                            val sx = size.width * barShimmer
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.45f),
                                        Color.Transparent
                                    ),
                                    startX = sx - size.width * 0.15f,
                                    endX = sx + size.width * 0.15f
                                )
                            )
                        }
                    }
            )
        }
    }
}
