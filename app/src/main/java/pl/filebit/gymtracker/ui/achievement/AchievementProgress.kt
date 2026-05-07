package pl.filebit.gymtracker.ui.achievement

import androidx.compose.animation.core.Animatable
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
        kotlinx.coroutines.delay(600)
        animPct.animateTo(pct, animationSpec = tween(1500))
    }

    Column(modifier = modifier) {
        // Tekst current / target
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                color = AccentOrange
            )
            Text(
                text = "${(animPct.value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = DarkOnSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        // Track + fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animPct.value)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentOrangeDim, AccentOrange, AccentOrangeDim)
                        )
                    )
            )
        }
    }
}
