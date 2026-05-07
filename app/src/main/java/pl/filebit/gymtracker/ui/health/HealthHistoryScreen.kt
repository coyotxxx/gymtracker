package pl.filebit.gymtracker.ui.health

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthHistoryScreen(
    onBack: () -> Unit,
    vm: HealthHistoryViewModel = hiltViewModel()
) {
    val entries by vm.entries.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Historia zdrowia", onBack = onBack)

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Brak wpisów. Wgraj zrzuty z zegarka (ikona AI → \"Wyślij screen z zegarka\") żeby zobaczyć historię.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.log.id }) { entry ->
                    HealthEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HealthEntryCard(entry: HealthHistoryEntry) {
    val log = entry.log
    val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl", "PL")).format(Date(log.dateMs))
        .replaceFirstChar { it.titlecase(Locale("pl", "PL")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                date,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = AccentOrange
            )
            Spacer(Modifier.height(8.dp))
            // Sen + waga + stres na pierwszej linii (najważniejsze)
            log.sleepHours?.let { Metric("💤 Sen", "%.1fh".format(it)) }
            entry.weightKg?.let { Metric("⚖️ Waga", "%.1f kg".format(it)) }
            log.stressLevel?.let { Metric("😰 Stres", "$it/5") }
            // Reszta z zegarka
            log.restingHeartRateBpm?.let { Metric("❤️ Tętno spoczynkowe", "$it bpm") }
            log.spO2Pct?.let { Metric("🩸 SpO2", "$it%") }
            log.hrvMs?.let { Metric("📊 HRV", "%.0f ms".format(it)) }
            log.vo2max?.let { Metric("🏃 VO2Max", "%.1f ml/kg/min".format(it)) }
            log.stepsCount?.let { Metric("👟 Kroki", "$it") }
            log.activeCalories?.let { Metric("🔥 Kalorie", "$it kcal") }
            // Subiektywne (jeśli wpisał ręcznie przez Dieta → Regeneracja)
            log.energyLevel?.let { Metric("⚡ Energia", "$it/5") }
            log.sleepQuality?.let { Metric("🛏️ Jakość snu", "$it/5") }
            log.sorenessLevel?.let { Metric("💪 DOMS", "$it/5") }

            // Brak żadnych metryk = info
            val hasAny = log.sleepHours != null || entry.weightKg != null ||
                log.stressLevel != null || log.restingHeartRateBpm != null ||
                log.spO2Pct != null || log.hrvMs != null || log.vo2max != null ||
                log.stepsCount != null || log.activeCalories != null ||
                log.energyLevel != null || log.sleepQuality != null || log.sorenessLevel != null
            if (!hasAny) {
                Text(
                    "(Pusty wpis)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            color = DarkOnSurface
        )
    }
}
