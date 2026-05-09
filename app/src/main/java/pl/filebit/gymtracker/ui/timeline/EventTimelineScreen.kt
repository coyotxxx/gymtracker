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
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
            ScreenHeader(
                title = "Oś czasu",
                onBack = onBack,
                actions = {
                    // v1.11.74: placeholder Customize — full functionality in v1.11.78
                    OutlinedButton(
                        onClick = { /* TODO v1.11.78 */ },
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Customize",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AccentOrange
                        )
                    }
                }
            )

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
                                dayOfWeekFmt = dayOfWeekFmt,
                                isExpanded = state.expandedEventId == event.id,
                                expandedDetails = if (state.expandedEventId == event.id)
                                    state.expandedDetails else null,
                                onToggleExpand = { vm.toggleExpand(event.id) }
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
                        label = "${eventTypeEmoji(type)} ${eventTypeShortLabel(type)} ($count)",
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
    dayOfWeekFmt: SimpleDateFormat,
    isExpanded: Boolean,
    expandedDetails: ExpandedPrDetails?,
    onToggleExpand: () -> Unit
) {
    val deco = eventDecoration(event)
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Lewa kolumna — linia ciągła + dot na środku
        Box(modifier = Modifier.width(40.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .background(DarkOutlineSoft.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(14.dp)
                    .align(Alignment.TopCenter)
                    .background(deco.dotColor, CircleShape)
            )
        }
        // Karta po prawej (klikalna — toggle expand)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
                .clickable { onToggleExpand() },
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, deco.dotColor.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header — typ + data + caret
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
                        "${df.format(Date(event.date))} · $dayShort  ${if (isExpanded) "▲" else "▼"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = DarkOnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))

                // Tytuł — różny format dla PR (waga × reps H1) vs reszty (description)
                if (event.type == TrainingEventType.PR_SET) {
                    PrCardCollapsedBody(event)
                } else {
                    Text(
                        deco.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = DarkOnSurface
                    )
                    if (event.notes.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            event.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Sekcja rozwinięta
                if (isExpanded) {
                    Spacer(Modifier.height(10.dp))
                    when (event.type) {
                        TrainingEventType.PR_SET -> ExpandedPrView(expandedDetails)
                        else -> {
                            // Inne typy — placeholder, w v1.11.75-77 dodam expand per typ
                            Text(
                                "Szczegóły dla tego typu eventu pojawią się w kolejnych wersjach.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * v1.11.74 — collapsed body karty PR.
 *
 * Format wzoru:
 *   150 kg × 5 powtórzeń           ← H1
 *   Nowy life-time PR. Poprzedni: 140 kg × 5 (przed 38 dni)   ← note
 *   [140kg → 150kg +10kg]   e1RM 169kg                          ← chip + e1rm
 */
@Composable
private fun PrCardCollapsedBody(event: TrainingEvent) {
    val w = event.weightKg
    val r = event.reps
    if (w == null || r == null) {
        Text("Nowy PR", color = DarkOnSurface)
        return
    }
    val exerciseName = event.exerciseName?.uppercase() ?: ""
    if (exerciseName.isNotBlank()) {
        // Już mamy badge ${type} — dodajemy też ćwiczenie w samym headerze byłby duplikat
        // Format: tytuł podrzędny "NOWY PR · NAZWA"  → robimy to w eventTypeBadge
    }

    Text(
        "${formatWeight(w)} kg × $r powtórzeń",
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        ),
        color = DarkOnSurface
    )

    // Note — life-time PR + poprzedni
    val noteText = run {
        val parts = mutableListOf<String>()
        parts.add("Nowy life-time PR.")
        if (event.notes.isNotBlank()) parts.add(event.notes)
        parts.joinToString(" ")
    }
    Spacer(Modifier.height(2.dp))
    Text(
        noteText,
        style = MaterialTheme.typography.bodySmall,
        color = DarkOnSurfaceVariant
    )

    // Highlight chip + e1RM
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Chip — używamy notes jeśli zawiera "Poprzedni: X kg × Y" do wyciągnięcia delta
        val previousWeightKg = parsePreviousWeightKg(event.notes)
        if (previousWeightKg != null && previousWeightKg > 0) {
            val delta = w - previousWeightKg
            val deltaSign = if (delta >= 0) "+" else ""
            Box(
                modifier = Modifier
                    .background(AccentOrange.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${formatWeight(previousWeightKg)}kg → ${formatWeight(w)}kg ${deltaSign}${formatWeight(delta)}kg",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = AccentOrange
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        event.e1rmKg?.let { e1 ->
            Text(
                "e1RM: ${formatWeight(e1)}kg",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
        }
    }
}

private fun formatWeight(w: Double): String {
    return if (w == w.toInt().toDouble()) "${w.toInt()}"
    else "%.1f".format(java.util.Locale.US, w)
}

/** Wyciąga poprzedni ciężar z notes formatu "Poprzedni: 140 kg × 5 ..." */
private fun parsePreviousWeightKg(notes: String): Double? {
    if (notes.isBlank()) return null
    val regex = Regex("""Poprzedni:\s*(\d+(?:[.,]\d+)?)\s*kg""", RegexOption.IGNORE_CASE)
    val match = regex.find(notes) ?: return null
    return match.groupValues[1].replace(",", ".").toDoubleOrNull()
}

// ============================================================================
// ExpandedPrView — sekcje workout / sety / wykres / buttons
// ============================================================================

@Composable
private fun ExpandedPrView(details: ExpandedPrDetails?) {
    if (details == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            Text("Ładowanie szczegółów...", style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        }
        return
    }

    // Sekcja 1 — workout details
    SectionLabel("WORKOUT W KTÓRYM PADŁ PR")
    DetailRow("Czas", "${details.workoutDurationMin} min")
    DetailRow("Łączny tonaż", "${formatWeight(details.totalVolumeKg)} kg")
    details.wellbeing?.let { w ->
        val stars = "★".repeat(w.coerceIn(0, 5)) + "☆".repeat((5 - w).coerceIn(0, 5))
        DetailRow("Wellbeing przed", "$stars ($w/5)", valueColor = SuccessGreen)
    }
    details.avgRpe?.let { r ->
        DetailRow("RPE (sesja)", "%.1f".format(java.util.Locale.US, r))
    }

    // Sekcja 2 — sety
    Spacer(Modifier.height(10.dp))
    SectionLabel("SETY")
    details.sets.forEachIndexed { idx, s ->
        SetRow(s, isPr = idx == details.prSetIndex)
    }

    // Sekcja 3 — wykres trendu
    if (details.trendPoints.size >= 2) {
        Spacer(Modifier.height(10.dp))
        SectionLabel("TREND CIĘŻARU (12 TYG)")
        TrendLineChart(details.trendPoints)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp
        ),
        color = DarkOnSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = DarkOnSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun SetRow(s: SetInfo, isPr: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (isPr) AccentOrange.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (isPr) AccentOrange else DarkOutlineSoft.copy(alpha = 0.4f),
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${s.setNumber}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = if (isPr) DarkBg else DarkOnSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatWeight(s.weightKg)} kg × ${s.reps}",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        s.rpe?.let {
            Text(
                "RPE $it",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        if (isPr) {
            Text(
                "⭐ PR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun TrendLineChart(points: List<TrendPoint>) {
    if (points.size < 2) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(DarkBg.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minY = points.minOf { it.e1rmKg }
            val maxY = points.maxOf { it.e1rmKg }
            val rangeY = (maxY - minY).coerceAtLeast(1.0)
            val w = size.width
            val h = size.height
            val n = points.size
            val stepX = w / (n - 1).coerceAtLeast(1)

            // Linia
            val path = Path()
            points.forEachIndexed { idx, p ->
                val x = idx * stepX
                val y = (h - ((p.e1rmKg - minY) / rangeY * h).toFloat()).toFloat()
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = AccentOrange,
                style = Stroke(width = 3f)
            )

            // Punkty
            points.forEachIndexed { idx, p ->
                val x = idx * stepX
                val y = (h - ((p.e1rmKg - minY) / rangeY * h).toFloat()).toFloat()
                drawCircle(color = AccentOrange, radius = 4f, center = Offset(x, y))
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

private fun eventTypeEmoji(type: TrainingEventType): String = when (type) {
    TrainingEventType.PR_SET -> "🏋️"
    TrainingEventType.PLAN_START, TrainingEventType.PLAN_END, TrainingEventType.PLAN_CHANGE -> "📋"
    TrainingEventType.DELOAD_DETECTED -> "😴"
    TrainingEventType.INJURY -> "🩹"
    TrainingEventType.GAP_RESUMED -> "↩️"
    TrainingEventType.CYCLE_MILESTONE -> "🎯"
}
