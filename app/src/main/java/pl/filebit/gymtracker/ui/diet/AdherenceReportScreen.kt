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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdherenceReportScreen(
    onBack: () -> Unit,
    vm: AdherenceReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Raport zgodności", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SummaryCard("OSTATNIE 7 DNI", state.last7Days) }
                item { SummaryCard("OSTATNIE 14 DNI", state.last14Days) }
                item {
                    Text(
                        "DZIENNE WYKRESY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                items(state.recentDays.size) { idx ->
                    DayLogRow(state.recentDays[idx])
                }
                if (state.recentDays.isEmpty() && !state.loading) {
                    item {
                        Text(
                            "Brak danych. Zaloguj posiłki przez kilka dni żeby zobaczyć trend.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, sum: AdherenceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${sum.avgScore}%",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorForScore(sum.avgScore)
                )
                Text(
                    " ogólna zgodność",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Text(
                "Próba: ${sum.sampleDays} ${if (sum.sampleDays == 1) "dzień" else "dni"} z zalogowaną dietą",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCol("kcal", "${sum.avgKcalPct}%", colorForPct(sum.avgKcalPct))
                MetricCol("białko", "${sum.avgProteinPct}%", colorForPct(sum.avgProteinPct))
                MetricCol("węgle", "${sum.avgCarbsPct}%", colorForPct(sum.avgCarbsPct))
                MetricCol("tłuszcz", "${sum.avgFatPct}%", colorForPct(sum.avgFatPct))
            }

            if (sum.workoutsPlanned > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Treningi: ${sum.workoutsDone} / ${sum.workoutsPlanned} wykonane",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sum.workoutsDone >= sum.workoutsPlanned) SuccessGreen
                            else DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DayLogRow(log: AdherenceLog) {
    val date = remember(log.dateMs) {
        SimpleDateFormat("EEEE, d MMM", Locale("pl", "PL")).format(Date(log.dateMs))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorForScore(log.overallScore), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    date,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DarkOnSurface,
                    maxLines = 1
                )
                Text(
                    "${log.actualKcal}/${log.targetKcal} kcal · " +
                        "B${log.actualProteinG}/${log.targetProteinG} · " +
                        "${log.mealsLoggedCount}/${log.mealsPlannedCount} posiłków",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = DarkOnSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                "${log.overallScore}%",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = colorForScore(log.overallScore)
            )
        }
    }
}

private fun colorForScore(score: Int): Color = when {
    score >= 85 -> SuccessGreen
    score >= 70 -> AccentOrange
    else -> ErrorRed
}

private fun colorForPct(pct: Int): Color = when {
    pct in 90..110 -> SuccessGreen
    pct in 70..130 -> AccentOrange
    else -> ErrorRed
}

@Composable
private fun remember(key: Any, calculation: () -> String): String =
    androidx.compose.runtime.remember(key) { calculation() }
