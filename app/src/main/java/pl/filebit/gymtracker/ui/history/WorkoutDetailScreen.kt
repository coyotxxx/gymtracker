package pl.filebit.gymtracker.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
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
import pl.filebit.gymtracker.util.formatDateLongPl
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onSavedAsPlan: (Long) -> Unit,
    onRepeated: () -> Unit,
    vm: WorkoutDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(workoutId) { vm.load(workoutId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        val workout = state.workout
        // Jeden ScreenHeader na zewnątrz — actions widoczne tylko gdy workout
        // załadowany (dropdown wymaga state.workout)
        ScreenHeader(
            title = "Trening",
            onBack = onBack,
            actions = {
                if (workout != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = DarkOnSurface
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_repeat_workout)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.repeatWorkout(onRepeated)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_save_as_plan)) },
                                leadingIcon = {
                                    Icon(Icons.Default.EventNote, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.saveAsPlan { newPlanId -> onSavedAsPlan(newPlanId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_delete_workout)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                }
                            )
                        }
                    }
                }
            }
        )
        if (state.loading || workout == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.stats_loading))
            }
        } else {
            val totalSets = state.groups.sumOf { it.sets.size }
            val totalVolume = state.groups.sumOf { g ->
                g.sets.sumOf { it.reps * it.weightKg }
            }
            // Cardio: gdy brak tonażu (kg×powt.), 4. kafelek pokazuje dystans/czas.
            val totalCardioDistanceM = state.groups.sumOf { g ->
                g.sets.sumOf { it.distanceM ?: 0.0 }
            }
            val totalCardioSec = state.groups.sumOf { g ->
                g.sets.sumOf { it.durationSec ?: 0 }
            }
            val planName = state.planName
            val dayLabel = state.planDayOfWeek?.let { dayLongLabel(it) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Origin label "Z PLANU: …" — żółty UPPERCASE bez karty
                if (planName != null) {
                    item {
                        val labelText = buildString {
                            append("Z PLANU: ")
                            append(planName.uppercase())
                            if (dayLabel != null) {
                                append(" · ")
                                append(dayLabel.uppercase())
                            }
                        }
                        Text(
                            labelText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = AccentOrange,
                            modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp),
                            maxLines = 1
                        )
                    }
                }

                // Tytuł: data + godzina
                item {
                    Text(
                        formatDateLongPl(workout.startedAt),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        ),
                        color = DarkOnSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Subtitle: "Trwał X · zakończony / w toku"
                item {
                    val statusLabel = if (workout.finishedAt != null) "zakończony" else "w toku"
                    Text(
                        "Trwał ${formatDuration(workout.durationMillis)} · $statusLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                }

                // Divider cienki
                item {
                    HorizontalDivider(color = DarkOutlineSoft)
                }

                // 4 kafelki bez border, w jednej linii
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCol("Czas", formatDuration(workout.durationMillis), null)
                        StatCol("Ćwicz.", "${state.groups.size}", null)
                        StatCol("Serie", "$totalSets", null)
                        when {
                            totalVolume > 0.0 ->
                                StatCol("Vol.", formatVolumeDisplay(totalVolume), "kg")
                            totalCardioDistanceM > 0.0 ->
                                StatCol(
                                    "Dystans",
                                    pl.filebit.gymtracker.util.formatCardioNumber(totalCardioDistanceM / 1000.0),
                                    "km"
                                )
                            totalCardioSec > 0 ->
                                StatCol("Cardio", "${totalCardioSec / 60}", "min")
                            else ->
                                StatCol("Vol.", formatVolumeDisplay(totalVolume), "kg")
                        }
                    }
                }

                // AI summary (jeśli jest)
                workout.aiSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AccentOrange.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.30f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "✨ PODSUMOWANIE TRENERA AI",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.4.sp
                                        ),
                                        color = AccentOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state.regeneratingSummary) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = AccentOrange
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { vm.regenerateAiSummary() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Replay,
                                                contentDescription = "Przegeneruj podsumowanie",
                                                tint = AccentOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkOnSurface
                                )
                            }
                        }
                    }
                }

                items(state.groups, key = { it.exercise.id }) { group ->
                    ExerciseDetailCard(
                        name = group.exercise.name,
                        metricType = group.exercise.metricType,
                        sets = group.sets,
                        previousSession = state.previousByExercise[group.exercise.id]
                    )
                }
            }
        }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Usunąć ten trening?") },
                text = { Text("Tej operacji nie można cofnąć.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDelete = false
                        vm.deleteWorkout(onBack)
                    }) { Text(stringResource(R.string.common_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    name: String,
    metricType: pl.filebit.gymtracker.data.entity.MetricType,
    sets: List<pl.filebit.gymtracker.data.entity.WorkoutSet>,
    previousSession: pl.filebit.gymtracker.data.repository.PreviousSession?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                // Chip "X SERIE"
                Box(
                    modifier = Modifier
                        .background(DarkSurfaceVariant, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${sets.size} SERIE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Mapa working setów z poprzedniego treningu (bez warmup), po setNumber
            val prevWorking = previousSession?.sets
                ?.filter { it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP }
                ?.sortedBy { it.setNumber }
                .orEmpty()
            // Indeksowanie: i-ty working set sets[] vs i-ty z prev
            val currentWorkingIdx = sets.mapIndexedNotNull { idx, s ->
                if (s.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP) idx to s else null
            }.mapIndexed { workingIdx, (origIdx, _) -> origIdx to workingIdx }.toMap()

            sets.forEachIndexed { idx, s ->
                val previousForRow = currentWorkingIdx[idx]?.let { wIdx -> prevWorking.getOrNull(wIdx) }
                SetRow(
                    setNumber = s.setNumber,
                    metricType = metricType,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    durationSec = s.durationSec,
                    distanceM = s.distanceM,
                    rpe = s.rpe,
                    previousSet = previousForRow
                )
                if (idx < sets.size - 1) {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = DarkOutlineSoft)
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    metricType: pl.filebit.gymtracker.data.entity.MetricType,
    weightKg: Double,
    reps: Int,
    durationSec: Int?,
    distanceM: Double?,
    rpe: Int?,
    previousSet: pl.filebit.gymtracker.data.entity.WorkoutSet?
) {
    val isCardioDist = metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION
    val isDuration = metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION

    fun setLabel(durSec: Int?, distM: Double?, w: Double, r: Int): String = when {
        isCardioDist -> listOfNotNull(
            durSec?.takeIf { it > 0 }?.let { "${it / 60} min" },
            pl.filebit.gymtracker.util.cardioSpeedKmh(durSec, distM)
                ?.let { "${pl.filebit.gymtracker.util.formatCardioNumber(it)} km/h" }
        ).joinToString(" · ").ifBlank { "—" }
        isDuration -> durSec?.takeIf { it > 0 }?.let { "${it / 60} min" } ?: "—"
        else -> "${formatWeight(w)}×$r"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Seria $setNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                setLabel(durationSec, distanceM, weightKg, reps),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = DarkOnSurface
            )
            // Subtitle z poprzedniego treningu — porównanie właściwej metryki
            previousSet?.let { prev ->
                val curVal = when {
                    isCardioDist -> pl.filebit.gymtracker.util.cardioSpeedKmh(durationSec, distanceM) ?: 0.0
                    isDuration -> (durationSec ?: 0).toDouble()
                    else -> weightKg
                }
                val prevVal = when {
                    isCardioDist -> pl.filebit.gymtracker.util.cardioSpeedKmh(prev.durationSec, prev.distanceM) ?: 0.0
                    isDuration -> (prev.durationSec ?: 0).toDouble()
                    else -> prev.weightKg
                }
                val color = when {
                    curVal > prevVal -> SuccessGreen
                    curVal < prevVal -> ErrorRed
                    else -> AccentOrange.copy(alpha = 0.7f)
                }
                val arrow = when {
                    curVal > prevVal -> "↑"
                    curVal < prevVal -> "↓"
                    else -> "="
                }
                Text(
                    "$arrow ${setLabel(prev.durationSec, prev.distanceM, prev.weightKg, prev.reps)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = color
                )
            }
        }
        if (rpe != null && rpe in 1..10) {
            Spacer(Modifier.width(8.dp))
            Text(
                "RPE$rpe",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = rpeColor(rpe)
            )
        }
    }
}

/**
 * Kolor RPE wg konwencji w sportach siłowych:
 * - 1-5: szary — zbyt lekko (nie buduje siły/masy)
 * - 6-7: zielony — efektywne, kontrolowane (rozgrzewka cięższa, top set lekki)
 * - 8: żółty — sweet spot dla hipertrofii (2-3 reps in reserve)
 * - 9: pomarańczowy — bardzo ciężko (1 RIR), górna granica progresji
 * - 10: czerwony — failure / max effort
 */
private fun rpeColor(rpe: Int): Color = when (rpe) {
    in 1..5 -> DarkOnSurfaceVariant
    6, 7 -> SuccessGreen
    8 -> AccentOrange
    9 -> Color(0xFFFF8C42)
    10 -> ErrorRed
    else -> DarkOnSurfaceVariant
}

@Composable
private fun StatCol(label: String, value: String, suffix: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = DarkOnSurface
            )
            if (suffix != null) {
                Text(
                    suffix,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
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

private fun formatVolumeDisplay(kg: Double): String = when {
    kg >= 10_000 -> "${"%.0f".format(kg)}"
    kg >= 1000 -> "${"%.0f".format(kg)}"
    else -> formatWeight(kg)
}

@Composable
private fun dayLongLabel(day: Int): String? = when (day) {
    1 -> stringResource(R.string.day_mon_long)
    2 -> stringResource(R.string.day_tue_long)
    3 -> stringResource(R.string.day_wed_long)
    4 -> stringResource(R.string.day_thu_long)
    5 -> stringResource(R.string.day_fri_long)
    6 -> stringResource(R.string.day_sat_long)
    7 -> stringResource(R.string.day_sun_long)
    else -> null
}
