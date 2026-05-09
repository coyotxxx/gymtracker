package pl.filebit.gymtracker.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ErrorRed
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v1.11.73 — Event Timeline z vertical line + dot'y per typ.
 *
 * Layout:
 *  - Stats bar: 3 kafle (eventów / PR-y / dni od pierwszego)
 *  - Filter chips: Wszystkie / per typ
 *  - Marker DZIŚ
 *  - Vertical timeline: linia po lewej + dot'y + karty po prawej
 *  - Headers miesięcy ("MAJ 2026")
 */
@Composable
fun EventTimelineScreen(
    onBack: () -> Unit,
    vm: EventTimelineViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val df = remember { SimpleDateFormat("dd.MM", Locale("pl")) }
    val dayOfWeekFmt = remember { SimpleDateFormat("EEE", Locale("pl")) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Oś czasu", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)  // 0dp żeby linie się łączyły
            ) {
                // Stats bar
                item {
                    StatsBar(
                        totalCount = state.totalCount,
                        prCount = state.countByType[TrainingEventType.PR_SET] ?: 0,
                        firstEventDate = state.allEvents.minByOrNull { it.date }?.date
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Filter chips
                item {
                    FilterChips(
                        currentFilter = state.filterType,
                        countByType = state.countByType,
                        totalCount = state.totalCount,
                        onFilterChange = { vm.setFilter(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Marker DZIŚ
                item {
                    TodayMarker()
                    Spacer(Modifier.height(8.dp))
                }

                if (state.filteredEvents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (state.isLoading) "Ładowanie..." else
                                    "Brak eventów. Eventy pojawią się po treningach (PR-y, kontuzje) " +
                                        "i przy zmianach planu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                } else {
                    state.groupedByMonth.entries.forEachIndexed { idx, (monthKey, events) ->
                        item(key = "header_$monthKey") {
                            MonthHeader(monthKey, isFirst = idx == 0)
                        }
                        items(events, key = { e -> "event_${e.id}" }) { event ->
                            TimelineItem(
                                event = event,
                                df = df,
                                dayOfWeekFmt = dayOfWeekFmt
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Stats Bar — 3 kafle u góry
// ============================================================================

@Composable
private fun StatsBar(totalCount: Int, prCount: Int, firstEventDate: Long?) {
    val daysFromFirst = firstEventDate?.let {
        ((System.currentTimeMillis() - it) / (24L * 3600_000)).toInt().coerceAtLeast(0)
    } ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(value = totalCount.toString(), label = "EVENTÓW", color = DarkOnSurface)
        StatCell(value = prCount.toString(), label = "PR-Y", color = AccentOrange)
        StatCell(value = daysFromFirst.toString(), label = "DNI OD\nPIERWSZEGO", color = DarkOnSurface)
    }
}

@Composable
private fun StatCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            ),
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = DarkOnSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ============================================================================
// Filter Chips
// ============================================================================

@Composable
private fun FilterChips(
    currentFilter: TrainingEventType?,
    countByType: Map<TrainingEventType, Int>,
    totalCount: Int,
    onFilterChange: (TrainingEventType?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(
                label = "Wszystkie ($totalCount)",
                selected = currentFilter == null,
                onClick = { onFilterChange(null) }
            )
        }
        TrainingEventType.values().forEach { type ->
            val count = countByType[type] ?: 0
            if (count > 0) {
                item {
                    FilterChip(
                        label = "${eventTypeShortLabel(type)} ($count)",
                        selected = currentFilter == type,
                        onClick = { onFilterChange(type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AccentOrange.copy(alpha = 0.20f) else DarkSurface,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) AccentOrange else DarkOnSurfaceVariant
        )
    }
}

// ============================================================================
// Today marker — pomarańczowy badge "DZIŚ • DD MIESIĄC"
// ============================================================================

@Composable
private fun TodayMarker() {
    val today = remember { Calendar.getInstance() }
    val day = today.get(Calendar.DAY_OF_MONTH)
    val polishMonths = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia"
    )
    val month = polishMonths[today.get(Calendar.MONTH)]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .background(AccentOrange.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "DZIŚ · $day $month",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = AccentOrange
            )
        }
    }
}

// ============================================================================
// Month header — "MAJ 2026" z kontynuowaną linią timeline
// ============================================================================

@Composable
private fun MonthHeader(monthKey: String, isFirst: Boolean) {
    val parts = monthKey.split("-")
    val polishMonths = listOf(
        "STYCZEŃ", "LUTY", "MARZEC", "KWIECIEŃ", "MAJ", "CZERWIEC",
        "LIPIEC", "SIERPIEŃ", "WRZESIEŃ", "PAŹDZIERNIK", "LISTOPAD", "GRUDZIEŃ"
    )
    val label = if (parts.size == 2) {
        val month = parts[1].toIntOrNull()?.let { polishMonths.getOrNull(it - 1) } ?: parts[1]
        "$month ${parts[0]}"
    } else monthKey

    Row(modifier = Modifier.height(32.dp)) {
        // Lewa kolumna z linią (kontynuowana ale bez dot'a)
        Box(modifier = Modifier.width(40.dp).fillMaxHeight()) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.TopCenter)
                        .background(DarkOutlineSoft.copy(alpha = 0.5f))
                )
            }
        }
        // Header tekst
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontSize = 11.sp
            ),
            color = DarkOnSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

// ============================================================================
// Timeline item — Row [linia+dot] + [karta]
// ============================================================================

@Composable
private fun TimelineItem(
    event: TrainingEvent,
    df: SimpleDateFormat,
    dayOfWeekFmt: SimpleDateFormat
) {
    val deco = eventDecoration(event)
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Lewa kolumna — linia ciągła + dot na środku
        Box(modifier = Modifier.width(40.dp).fillMaxHeight()) {
            // Linia ciągła wzdłuż całego itemu
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .background(DarkOutlineSoft.copy(alpha = 0.5f))
            )
            // Dot u góry karty (offset top 16dp)
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(14.dp)
                    .align(Alignment.TopCenter)
                    .background(deco.dotColor, CircleShape)
            )
        }
        // Karta po prawej
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, deco.dotColor.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header — typ + data
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${deco.emoji} ${eventTypeBadge(event.type)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = deco.dotColor,
                        modifier = Modifier.weight(1f)
                    )
                    val dayShort = dayOfWeekFmt.format(Date(event.date)).take(2).lowercase()
                    Text(
                        "${df.format(Date(event.date))} · $dayShort",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Tytuł główny
                Text(
                    deco.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DarkOnSurface
                )
                // Notatki (jeśli są)
                if (event.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        event.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// Helpery: dekoracja eventu (kolor / emoji / tytuł / krótka etykieta)
// ============================================================================

private data class EventDeco(
    val emoji: String,
    val dotColor: Color,
    val title: String
)

// Kolory dot'ów per typ — distinct, łatwe do rozpoznania
private val ColorDeloadPurple = Color(0xFFB47AE0)   // jasny fiolet
private val ColorPlanBlue = Color(0xFF5BA0E0)        // jasny niebieski
private val ColorMilestoneGreen = SuccessGreen
private val ColorPrOrange = AccentOrange
private val ColorInjuryRed = ErrorRed
private val ColorGapGray = DarkOnSurfaceVariant

private fun eventDecoration(event: TrainingEvent): EventDeco {
    return when (event.type) {
        TrainingEventType.PR_SET -> EventDeco(
            emoji = "🏆",
            dotColor = ColorPrOrange,
            title = run {
                val name = event.exerciseName ?: "?"
                val w = event.weightKg
                val r = event.reps
                val e1 = event.e1rmKg
                if (w != null && r != null) {
                    "NOWY PR · ${name.uppercase()}\n$w kg × $r" +
                        (e1?.let { " · e1RM ${"%.1f".format(java.util.Locale.US, it)} kg" } ?: "")
                } else "Nowy PR: $name"
            }
        )
        TrainingEventType.PLAN_START -> EventDeco(
            emoji = "🚀",
            dotColor = ColorPlanBlue,
            title = "Plan rozpoczęty: ${event.planName ?: "?"}"
        )
        TrainingEventType.PLAN_END -> EventDeco(
            emoji = "🏁",
            dotColor = ColorPlanBlue,
            title = "Plan zakończony: ${event.planName ?: "?"}"
        )
        TrainingEventType.PLAN_CHANGE -> EventDeco(
            emoji = "🔄",
            dotColor = ColorPlanBlue,
            title = "Zmiana planu: ${event.planName ?: "?"}"
        )
        TrainingEventType.DELOAD_DETECTED -> EventDeco(
            emoji = "🔋",
            dotColor = ColorDeloadPurple,
            title = "Tydzień deload" + (event.weeksContext?.let { " (${it} tyg cyklu)" } ?: "")
        )
        TrainingEventType.INJURY -> EventDeco(
            emoji = "🩹",
            dotColor = ColorInjuryRed,
            title = "Kontuzja: ${event.area ?: "?"}"
        )
        TrainingEventType.GAP_RESUMED -> EventDeco(
            emoji = "↩️",
            dotColor = ColorGapGray,
            title = "Powrót po przerwie" + (event.weeksContext?.let { " ${it} tyg" } ?: "")
        )
        TrainingEventType.CYCLE_MILESTONE -> EventDeco(
            emoji = "🎯",
            dotColor = ColorMilestoneGreen,
            title = "Kamień milowy cyklu"
        )
    }
}

private fun eventTypeBadge(type: TrainingEventType): String = when (type) {
    TrainingEventType.PR_SET -> "NOWY PR"
    TrainingEventType.PLAN_START -> "PLAN START"
    TrainingEventType.PLAN_END -> "PLAN KONIEC"
    TrainingEventType.PLAN_CHANGE -> "ZMIANA PLANU"
    TrainingEventType.DELOAD_DETECTED -> "DELOAD"
    TrainingEventType.INJURY -> "KONTUZJA"
    TrainingEventType.GAP_RESUMED -> "POWRÓT"
    TrainingEventType.CYCLE_MILESTONE -> "KAMIEŃ MILOWY"
}

private fun eventTypeShortLabel(type: TrainingEventType): String = when (type) {
    TrainingEventType.PR_SET -> "PR-y"
    TrainingEventType.PLAN_START, TrainingEventType.PLAN_END, TrainingEventType.PLAN_CHANGE -> "Plan"
    TrainingEventType.DELOAD_DETECTED -> "Deload"
    TrainingEventType.INJURY -> "Kontuzje"
    TrainingEventType.GAP_RESUMED -> "Powrót"
    TrainingEventType.CYCLE_MILESTONE -> "Kamienie"
}
