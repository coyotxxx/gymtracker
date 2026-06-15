package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                        "DZIEŃ PO DNIU · DOTKNIJ PO SZCZEGÓŁY",
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
                    val day = state.recentDays[idx]
                    DayLogRow(day, state.mealBreakdowns[day.dateMs].orEmpty())
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
private fun DayLogRow(log: AdherenceLog, meals: List<pl.filebit.gymtracker.data.repository.MealSlotStatus>) {
    val date = remember(log.dateMs) {
        SimpleDateFormat("EEEE, d MMM", Locale("pl", "PL")).format(Date(log.dateMs))
    }
    val expanded = androidx.compose.runtime.remember(log.dateMs) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded.value = !expanded.value },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(8.dp))
                Text(
                    if (expanded.value) "▾" else "▸",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
            }

            if (expanded.value) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DarkOutlineSoft))
                Spacer(Modifier.height(10.dp))
                MacroDetailRow("Kalorie", log.actualKcal, log.targetKcal, "kcal", log.kcalAdherencePct)
                MacroDetailRow("Białko", log.actualProteinG, log.targetProteinG, "g", log.proteinAdherencePct)
                MacroDetailRow("Węgle", log.actualCarbsG, log.targetCarbsG, "g", log.carbsAdherencePct)
                MacroDetailRow("Tłuszcz", log.actualFatG, log.targetFatG, "g", log.fatAdherencePct)
                Spacer(Modifier.height(8.dp))
                DetailLine("Posiłki", "${log.mealsLoggedCount} / ${log.mealsPlannedCount} zalogowane", DarkOnSurface)
                meals.forEach { slot -> MealSlotLine(slot) }
                if (log.wasTrainingPlanned) {
                    DetailLine(
                        "Trening",
                        if (log.wasTrainingDone) "wykonany" else "niewykonany",
                        if (log.wasTrainingDone) SuccessGreen else ErrorRed
                    )
                } else {
                    DetailLine("Trening", "dzień bez treningu", DarkOnSurfaceVariant)
                }
                if (log.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        log.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroDetailRow(label: String, actual: Int, target: Int, unit: String, pct: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            "$actual / $target $unit",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$pct%",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = colorForPct(pct)
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun MealSlotLine(slot: pl.filebit.gymtracker.data.repository.MealSlotStatus) {
    val (statusText, color) = when (slot.state) {
        pl.filebit.gymtracker.data.repository.MealSlotState.EATEN -> "✓ zjedzone" to SuccessGreen
        pl.filebit.gymtracker.data.repository.MealSlotState.SKIPPED -> "✗ pominięte" to ErrorRed
        pl.filebit.gymtracker.data.repository.MealSlotState.MISSING -> "— nie zalogowano" to DarkOnSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            slot.label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
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
