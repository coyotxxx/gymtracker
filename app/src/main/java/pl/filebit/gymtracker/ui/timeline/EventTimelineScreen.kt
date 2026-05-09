package pl.filebit.gymtracker.ui.timeline

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.util.Date
import java.util.Locale

/**
 * v1.11.72 — Event Timeline. Oś czasu wszystkich eventów AI:
 * PR_SET / INJURY / DELOAD_DETECTED / PLAN_START / PLAN_END / GAP_RESUMED / CYCLE_MILESTONE.
 *
 * User widzi swój cykl jako linię czasu z kamieniami milowymi.
 */
@Composable
fun EventTimelineScreen(
    onBack: () -> Unit,
    vm: EventTimelineViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val df = remember { SimpleDateFormat("dd.MM.yyyy", Locale("pl")) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Oś czasu", onBack = onBack)

            // Filtr po typie
            FilterChips(
                currentFilter = state.filterType,
                countByType = state.countByType,
                totalCount = state.totalCount,
                onFilterChange = { vm.setFilter(it) }
            )

            if (state.filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.groupedByMonth.forEach { (monthKey, events) ->
                        item(key = "header_$monthKey") {
                            MonthHeader(monthKey)
                        }
                        items(events) { event ->
                            EventCard(event, df)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    currentFilter: TrainingEventType?,
    countByType: Map<TrainingEventType, Int>,
    totalCount: Int,
    onFilterChange: (TrainingEventType?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                        label = "${eventTypeLabel(type)} ($count)",
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

@Composable
private fun MonthHeader(monthKey: String) {
    val parts = monthKey.split("-")
    val polishMonths = listOf(
        "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
        "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"
    )
    val label = if (parts.size == 2) {
        val month = parts[1].toIntOrNull()?.let { polishMonths.getOrNull(it - 1) } ?: parts[1]
        "${month.replaceFirstChar { it.uppercase() }} ${parts[0]}"
    } else monthKey
    Text(
        label,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.4.sp,
            fontSize = 11.sp
        ),
        color = AccentOrange,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun EventCard(event: TrainingEvent, df: SimpleDateFormat) {
    val (emoji, color, title) = eventDecoration(event)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    df.format(Date(event.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
                if (event.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                }
            }
        }
    }
}

private fun eventTypeLabel(type: TrainingEventType): String = when (type) {
    TrainingEventType.PR_SET -> "PR-y"
    TrainingEventType.PLAN_START -> "Plan start"
    TrainingEventType.PLAN_END -> "Plan end"
    TrainingEventType.PLAN_CHANGE -> "Zmiana planu"
    TrainingEventType.DELOAD_DETECTED -> "Deload"
    TrainingEventType.INJURY -> "Kontuzje"
    TrainingEventType.GAP_RESUMED -> "Powrót"
    TrainingEventType.CYCLE_MILESTONE -> "Kamień milowy"
}

private fun eventDecoration(event: TrainingEvent): Triple<String, androidx.compose.ui.graphics.Color, String> {
    return when (event.type) {
        TrainingEventType.PR_SET -> Triple(
            "🏆",
            SuccessGreen,
            "PR ${event.exerciseName ?: "?"}: ${event.weightKg ?: 0} kg × ${event.reps ?: 0}" +
                (event.e1rmKg?.let { " (e1RM ${"%.1f".format(java.util.Locale.US, it)})" } ?: "")
        )
        TrainingEventType.PLAN_START -> Triple(
            "🚀",
            AccentOrange,
            "Plan rozpoczęty: ${event.planName ?: "?"}"
        )
        TrainingEventType.PLAN_END -> Triple(
            "🏁",
            DarkOnSurfaceVariant,
            "Plan zakończony: ${event.planName ?: "?"}"
        )
        TrainingEventType.PLAN_CHANGE -> Triple(
            "🔄",
            AccentOrange,
            "Zmiana planu: ${event.planName ?: "?"}"
        )
        TrainingEventType.DELOAD_DETECTED -> Triple(
            "🔋",
            AccentOrange,
            "Deload wykryty${event.weeksContext?.let { " (${it} tyg cyklu)" } ?: ""}"
        )
        TrainingEventType.INJURY -> Triple(
            "🩹",
            ErrorRed,
            "Kontuzja: ${event.area ?: "?"}"
        )
        TrainingEventType.GAP_RESUMED -> Triple(
            "↩️",
            DarkOnSurfaceVariant,
            "Powrót po przerwie${event.weeksContext?.let { " ${it} tyg" } ?: ""}"
        )
        TrainingEventType.CYCLE_MILESTONE -> Triple(
            "🎯",
            SuccessGreen,
            "Kamień milowy cyklu"
        )
    }
}

