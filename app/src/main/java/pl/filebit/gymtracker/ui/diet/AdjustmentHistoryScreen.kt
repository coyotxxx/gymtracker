package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.DietAdjustment
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdjustmentHistoryScreen(
    onBack: () -> Unit,
    vm: AdjustmentHistoryViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Historia zmian planu", onBack = onBack)

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Brak zmian planu — silnik nie zaproponował jeszcze żadnej korekty.\n\n" +
                            "Loguj posiłki + waga → po 14 dniach pojawią się sugestie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items.size) { idx -> AdjustmentRow(items[idx]) }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentRow(adj: DietAdjustment) {
    val date = remember(adj.dateMs) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl", "PL")).format(Date(adj.dateMs))
    }
    val delta = adj.newKcal - adj.oldKcal
    val (label, color) = when (adj.actionCode) {
        "DECREASE_KCAL" -> "↓ Obniżono" to AccentOrange
        "INCREASE_KCAL" -> "↑ Dodano" to SuccessGreen
        "HOLD" -> "= Trzymaj plan" to DarkOnSurfaceVariant
        "SIMPLIFY_PLAN" -> "🛠 Uprość plan" to AccentOrange
        "DELOAD" -> "🔄 Deload" to AccentOrange
        else -> adj.actionCode to DarkOnSurfaceVariant
    }
    val statusBadge = when {
        adj.applied -> "✓ Zastosowane"
        adj.dismissed -> "✕ Anulowane"
        else -> "Oczekuje"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        statusBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                date,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            if (delta != 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${adj.oldKcal} → ${adj.newKcal} kcal (${if (delta > 0) "+" else ""}$delta)",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = DarkOnSurface
                )
            }
            adj.aiExplanation?.let { aiText ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            "✨ AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = AccentOrange
                        )
                        Text(aiText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                adj.engineExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )

            // Snapshot inputów (debug-friendly)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Snapshot: ")
                    adj.snapshotAvgWeight14d?.let { append("avg14d %.1fkg · ".format(it)) }
                    adj.snapshotSlopeKgPerWeek?.let { append("slope %.2f kg/tydz · ".format(it)) }
                    append("kcal ${adj.snapshotAdherence14dKcal}% · ")
                    append("białko ${adj.snapshotAdherence14dProtein}% · ")
                    append("treningi ${adj.snapshotWorkoutsDone}/${adj.snapshotWorkoutsPlanned}")
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = DarkOnSurfaceVariant
            )
        }
    }
}
