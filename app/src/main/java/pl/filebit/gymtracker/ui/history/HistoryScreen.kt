package pl.filebit.gymtracker.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight
import pl.filebit.gymtracker.ui.periodization.color
import pl.filebit.gymtracker.ui.periodization.emoji
import pl.filebit.gymtracker.ui.periodization.shortLabelPl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HistoryFilter(val labelPl: String) {
    ALL("Wszystkie"),
    FROM_PLAN("Z planu"),
    ADHOC("Ad-hoc"),
    WITH_PR("Z PR"),
    LAST_30_DAYS("Ostatnie 30 dni")
}

@Composable
fun HistoryScreen(
    onOpenWorkout: (Long) -> Unit,
    vm: HistoryViewModel = hiltViewModel()
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filtered = remember(workouts, query, filter) {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 60L * 60L * 1000L
        workouts.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                // proste wyszukiwanie po nazwie planu lub notatce
                (item.planName?.contains(query, ignoreCase = true) == true) ||
                    item.workout.notes.contains(query, ignoreCase = true)
            }
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.FROM_PLAN -> item.workout.fromPlanId != null
                HistoryFilter.ADHOC -> item.workout.fromPlanId == null
                HistoryFilter.WITH_PR -> item.hasPR
                HistoryFilter.LAST_30_DAYS -> item.workout.startedAt >= thirtyDaysAgo
            }
            matchesQuery && matchesFilter
        }
    }

    val grouped = remember(filtered) {
        // Grupuj po miesiącu, zachowując kolejność DESC
        val fmt = SimpleDateFormat("LLLL yyyy", Locale("pl", "PL"))
        val map = linkedMapOf<String, MutableList<HistoryItem>>()
        filtered.forEach { item ->
            val key = fmt.format(Date(item.workout.startedAt))
                .replaceFirstChar { it.titlecase(Locale("pl", "PL")) }
            map.getOrPut(key) { mutableListOf() }.add(item)
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Historia",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    "Szukaj po planie, ćwiczeniu…",
                    color = DarkOnSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = DarkOnSurfaceVariant)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = DarkOutline,
                cursorColor = AccentOrange
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        Spacer(Modifier.height(12.dp))

        // Chip-y filtrów
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HistoryFilter.entries.size) { idx ->
                val f = HistoryFilter.entries[idx]
                FilterPillChip(
                    text = f.labelPl,
                    selected = filter == f,
                    onClick = { filter = f }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (workouts.isEmpty()) "Brak treningów w historii"
                    else "Brak wyników dla wybranych filtrów",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (month, monthItems) ->
                    item(key = "header_$month") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            month.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = DarkOnSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    monthItems.forEach { historyItem ->
                        item(key = historyItem.workout.id) {
                            HistoryRow(item = historyItem, onClick = { onOpenWorkout(historyItem.workout.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) AccentOrange.copy(alpha = 0.12f) else DarkSurfaceVariant
    val fg = if (selected) AccentOrange else DarkOnSurfaceVariant
    val borderColor = if (selected) AccentOrange.copy(alpha = 0.50f) else Color.Transparent
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFmt = remember {
                    SimpleDateFormat("EEEE · d MMM", Locale("pl", "PL"))
                }
                val raw = dateFmt.format(Date(item.workout.startedAt))
                val nice = raw.replaceFirstChar { it.titlecase(Locale("pl", "PL")) }
                Text(
                    nice,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    modifier = Modifier.weight(1f)
                )
                // v1.19.0 — PhaseBadge przed PR (jeśli trening trafił w aktywny mesocykl)
                item.mesocyclePhase?.let { phase ->
                    PhaseBadge(phase)
                    Spacer(Modifier.size(6.dp))
                }
                if (item.hasPR) {
                    PrBadge()
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip("Czas", formatDuration(item.workout.durationMillis))
                StatChip("Ćwicz.", "${item.exerciseCount}")
                StatChip("Serie", "${item.totalSets}")
                if (item.totalVolumeKg <= 0.0 && item.cardioDistanceM > 0.0) {
                    StatChip(
                        "Dystans",
                        "${pl.filebit.gymtracker.util.formatCardioNumber(item.cardioDistanceM / 1000.0)}km"
                    )
                } else {
                    StatChip("Vol", "${formatWeight(item.totalVolumeKg)}kg")
                }
            }
        }
    }
}

/**
 * v1.19.0 — odznaka fazy mesocyklu obok daty treningu w historii.
 * Style: rounded pill z kolorem fazy + emoji + skrót.
 */
@Composable
private fun PhaseBadge(phase: pl.filebit.gymtracker.data.entity.MesocyclePhase) {
    val color = phase.color()
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(phase.emoji(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
            Spacer(Modifier.size(4.dp))
            Text(
                phase.shortLabelPl(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                color = color
            )
        }
    }
}

@Composable
private fun PrBadge() {
    Box(
        modifier = Modifier
            .border(1.dp, AccentOrange.copy(alpha = 0.50f), RoundedCornerShape(50))
            .background(AccentOrange.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
            Spacer(Modifier.size(4.dp))
            Text(
                "PR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    val mainText = value.removeSuffix("kg").trim()
    val hasKg = value != mainText
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                mainText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = DarkOnSurface
            )
            if (hasKg) {
                Spacer(Modifier.width(2.dp))
                Text(
                    "kg",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}
