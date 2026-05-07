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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.layout
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
 * Pasek progresu — animacja 0 → pct w 1.4s, potem STATYCZNY.
 * v1.11.18: usunięty infinite barShimmer (drawWithCache + state.value
 * = redraw co frame, jedyna pozostała ciągła animacja w modal).
 */
@Composable
fun AchievementProgress(
    current: Long,
    target: Long,
    modifier: Modifier = Modifier
) {
    val pct = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 1f
    // Plynna animacja paska 0 → pct po wjezdzie medalu (delay 1300ms)
    val animPct = remember { Animatable(0f) }
    LaunchedEffect(current, target) {
        kotlinx.coroutines.delay(1300)
        animPct.animateTo(pct, animationSpec = tween(1200))
    }

    Column(modifier = modifier) {
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
        // Track + fill (statyczny po wjeździe)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            // Custom layout — wczytujemy animPct.value w lambda → zero recompose,
            // tylko remeasure width tego konkretnego Box.
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * animPct.value).toInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width)
                        )
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentOrangeDim, AccentOrange, AccentOrangeDim)
                        )
                    )
            )
        }
    }
}
