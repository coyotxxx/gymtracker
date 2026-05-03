package pl.filebit.gymtracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

val DAY_FULL_LABELS = listOf(
    "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"
)
val DAY_SHORT_LABELS = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")

private val DangerRed = Color(0xFFFF6B6B)

/**
 * Wiersz akcji w menu kontekstowym (ModalBottomSheet). Ikona + label + opcjonalny subtitle.
 * Zaznaczenie `danger=true` przebarwia tekst i ikonę na czerwono.
 */
@Composable
fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> DarkOnSurfaceVariant.copy(alpha = 0.4f)
        danger -> DangerRed
        else -> AccentOrange
    }
    val labelColor = when {
        !enabled -> DarkOnSurface.copy(alpha = 0.4f)
        danger -> DangerRed
        else -> DarkOnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = labelColor
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DarkOnSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
                )
            }
        }
    }
}

/**
 * Wybór dnia tygodnia (Pon-Nd) jako lista pillów. Używane przy Przesuń/Zamień/Skopiuj/itp.
 *
 * @param sourceDay dzień źródłowy (1-7) — wyróżniony, opcjonalnie disabled
 * @param hintFor zwraca etykietę-podpowiedź pod dniem (np. "zajęty", "scali z istniejącymi")
 * @param enabledFor czy konkretny dzień ma być klikalny (default: każdy poza sourceDay)
 */
@Composable
fun DayPickerDialog(
    title: String,
    sourceDay: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    hintFor: (Int) -> String = { "" },
    enabledFor: (Int) -> Boolean = { it != sourceDay }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                (1..7).forEach { d ->
                    val isSource = d == sourceDay
                    val enabled = enabledFor(d)
                    val hint = if (isSource) "(ten dzień)" else hintFor(d)
                    DayPickerRow(
                        day = d,
                        hint = hint,
                        enabled = enabled && !isSource,
                        onClick = { onPick(d) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun DayPickerRow(day: Int, hint: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                DarkSurfaceVariant.copy(alpha = if (enabled) 0.4f else 0.15f),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                AccentOrange.copy(alpha = if (enabled) 0.25f else 0.0f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        AccentOrange.copy(alpha = 0.18f * alpha),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    DAY_SHORT_LABELS[day - 1],
                    color = AccentOrange.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                DAY_FULL_LABELS[day - 1],
                color = DarkOnSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium
            )
            if (hint.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    hint,
                    color = DarkOnSurfaceVariant.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
