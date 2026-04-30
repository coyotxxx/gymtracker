package pl.filebit.gymtracker.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.util.formatDuration

/**
 * Mini-bar aktywnego treningu (52dp, gradient z żółtym akcentem).
 * Subtelnie świeci — przypomina że trening trwa, klik wraca do Coach mode.
 */
@Composable
fun ActiveWorkoutMiniBar(
    state: ActiveWorkoutShellState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.hasActive) return
    val title = state.planName.ifBlank { "Aktywny trening" }
    val duration = formatDuration(state.durationMillis)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                Brush.horizontalGradient(
                    0f to AccentOrange.copy(alpha = 0.18f),
                    1f to AccentOrange.copy(alpha = 0.08f)
                )
            )
            .border(width = 1.dp, color = AccentOrange.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsująca kropka — wspólny komponent z motywu
            pl.filebit.gymtracker.ui.theme.PulsingDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            // Ikona w kolorowym kwadracie
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        duration,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = AccentOrange
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "·  Wróć do treningu",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = DarkOnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
