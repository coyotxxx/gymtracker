package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.entity.HydrationLog
import pl.filebit.gymtracker.data.entity.HydrationSource
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista wpisów wody z dzisiejszego dnia + możliwość usunięcia.
 */
@Composable
fun HydrationLogDialog(
    logs: List<HydrationLog>,
    consumedToday: Int,
    goal: Int,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ScrollableDialogShell(
        title = "💧 Woda dzisiaj",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        bodyArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    "$consumedToday / $goal ml",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    ),
                    color = AccentOrange
                )
                val pct = if (goal > 0) (consumedToday * 100 / goal) else 0
                Text(
                    "$pct% celu dziennego",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }

        if (logs.isEmpty()) {
            Text(
                "Brak wpisów dzisiaj. Użyj +250 / +500 / +750 ml żeby dodać.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        } else {
            Text(
                "Wpisy:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            logs.forEach { log ->
                HydrationRow(log = log, onDelete = { onDelete(log.id) })
            }
        }
    }
}

@Composable
private fun HydrationRow(
    log: HydrationLog,
    onDelete: () -> Unit
) {
    val time = remember(log.createdAt) {
        SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date(log.createdAt))
    }
    val sourceLabel = when (log.source) {
        HydrationSource.WATER -> "💧 woda"
        HydrationSource.TEA -> "🍵 herbata"
        HydrationSource.COFFEE -> "☕ kawa"
        HydrationSource.JUICE -> "🍊 sok"
        HydrationSource.OTHER -> "🥛 inne"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${log.ml} ml",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    ),
                    color = DarkOnSurface
                )
                Text(
                    "$time · $sourceLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Usuń",
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

