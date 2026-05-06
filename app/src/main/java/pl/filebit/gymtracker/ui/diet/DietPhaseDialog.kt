package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.repository.PhaseSuggestion
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant

@Composable
fun DietPhaseDialog(
    suggestion: PhaseSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val (emoji, label) = when (suggestion.proposedType) {
        DietPhaseType.DIET_BREAK -> "🛑" to "Diet break — propozycja silnika"
        DietPhaseType.MAINTENANCE -> "⏸ Maintenance" to "Faza maintenance — propozycja silnika"
        DietPhaseType.REFEED_DAY -> "🍝" to "Refeed day — propozycja"
        DietPhaseType.CUT -> "↘️" to "Faza CUT"
        DietPhaseType.BULK -> "↗️" to "Faza BULK"
        null -> "" to "Faza"
    }
    ScrollableDialogShell(
        title = "$emoji $label",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
            TextButton(onClick = { onAccept(); onDismiss() }) {
                Text("Akceptuj", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        bodyArrangement = Arrangement.spacedBy(10.dp)
    ) {
                if (suggestion.proposedType != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                "Czas trwania: ${suggestion.durationDays} dni",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AccentOrange
                            )
                            if (suggestion.kcalAdjustment != 0) {
                                Text(
                                    "Korekta kcal: ${if (suggestion.kcalAdjustment > 0) "+" else ""}${suggestion.kcalAdjustment} kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentOrange
                                )
                            }
                        }
                    }
                }
                Text(
                    suggestion.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
                if (!suggestion.isStrong) {
                    Text(
                        "(opcjonalna sugestia — możesz pominąć)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
    }
}
