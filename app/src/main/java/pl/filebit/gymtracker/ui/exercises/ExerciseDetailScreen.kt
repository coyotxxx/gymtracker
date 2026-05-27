package pl.filebit.gymtracker.ui.exercises

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.HelpOutline
import pl.filebit.gymtracker.ai.ExerciseTrend
import pl.filebit.gymtracker.ai.ProgressionStatus
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.repository.ExerciseProgressionPoint
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.util.formatDate
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onAskAi: (String) -> Unit = {},
    vm: ExerciseDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editingNotes by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        val ex = state.exercise
        // Jeden ScreenHeader na zewnątrz — pozycja stała, tytuł aktualizuje się
        // gdy dane się załadują (z fallback 'Ćwiczenie' na nazwę ex)
        ScreenHeader(
            title = ex?.name ?: stringResource(R.string.exercise_detail_title),
            onBack = onBack
        )
        if (state.loading || ex == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.stats_loading))
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // v2.0.0: GIF animacja wykonania (Cloudflare R2 CDN)
            if (!ex.gifUrl.isNullOrBlank()) {
                item { ExerciseGifCard(gifUrl = ex.gifUrl) }
            }
            // v2.0.0: chipsy klasyfikacji canonical (movement_pattern, level, difficulty)
            if (ex.movementPattern != null || ex.levelMin != null || ex.difficulty1To10 != null || ex.category != null) {
                item { CanonicalMetadataChips(ex) }
            }
            // v1.25.0: Technika krok po kroku (PL z canonical, EN fallback)
            if (!ex.instructionsEnJson.isNullOrBlank() || !ex.instructionsPlJson.isNullOrBlank()) {
                item {
                    ExerciseInstructionsCard(
                        instructionsPlJson = ex.instructionsPlJson,
                        instructionsEnJson = ex.instructionsEnJson,
                        translating = state.translating,
                        onTranslate = { vm.translateInstructionsToPl() }
                    )
                }
            }
            // v2.0.0: Wskazówki coachingowe (setup + execution + oddech) z canonical
            if (!ex.coachingCuesJson.isNullOrBlank() && ex.coachingCuesJson != "{}") {
                item { CoachingCuesCard(coachingJson = ex.coachingCuesJson) }
            }
            // v2.0.0: Najczęstsze błędy z canonical
            if (!ex.commonFaultsJson.isNullOrBlank() && ex.commonFaultsJson != "[]") {
                item { CommonFaultsCard(faultsJson = ex.commonFaultsJson) }
            }
            // v2.0.0: Przeciwwskazania z canonical
            if (!ex.contraindicationsJson.isNullOrBlank() && ex.contraindicationsJson != "[]") {
                item { ContraindicationsCard(contraindicationsJson = ex.contraindicationsJson) }
            }
            // v2.2.0: Powiązane ćwiczenia (prerequisites, progression, alternatives)
            val hasPrereq = !ex.prerequisitesJson.isNullOrBlank() && ex.prerequisitesJson != "[]"
            val hasProg = !ex.progressionToJson.isNullOrBlank() && ex.progressionToJson != "[]"
            val hasAlt = !ex.alternativesJson.isNullOrBlank() && ex.alternativesJson != "[]"
            if (hasPrereq || hasProg || hasAlt) {
                item {
                    RelatedExercisesCard(
                        prerequisitesJson = ex.prerequisitesJson,
                        progressionToJson = ex.progressionToJson,
                        alternativesJson = ex.alternativesJson,
                        relatedNames = state.relatedExerciseNames
                    )
                }
            }
            // v1.25.0: Mięśnie i sprzęt z ExerciseDB (dokładniejsze niż enum)
            if (!ex.targetMusclesCsv.isNullOrBlank() ||
                !ex.secondaryMusclesCsv.isNullOrBlank() ||
                !ex.equipmentDbCsv.isNullOrBlank()
            ) {
                item {
                    ExerciseMusclesEquipmentCard(
                        targetMuscles = ex.targetMusclesCsv?.split(",").orEmpty(),
                        secondaryMuscles = ex.secondaryMusclesCsv?.split(",").orEmpty(),
                        equipment = ex.equipmentDbCsv?.split(",").orEmpty()
                    )
                }
            }
            // Przycisk "Zapytaj AI o to ćwiczenie"
            item {
                AskAiAboutExerciseRow(
                    exerciseName = ex.name,
                    onAskAi = onAskAi
                )
            }
            // Karta preferencji — Lubię / Unikaj. AI używa tych flag przy generowaniu planu.
            item {
                ExercisePreferenceCard(
                    isFavorite = ex.isFavorite,
                    isAvoided = ex.isAvoided,
                    onToggleFavorite = { vm.toggleFavorite() },
                    onToggleAvoided = { vm.toggleAvoided() }
                )
            }
            // Karta statusu progresji (e1RM trend ostatnich 6 tygodni)
            state.trend?.let { trend ->
                item { ProgressionStatusCard(trend = trend) }
            }
            // Opis ćwiczenia (jeśli wbudowany)
            if (ex.description.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, DarkOutlineSoft
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "OPIS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp
                                ),
                                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                ex.description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 22.sp
                                ),
                                color = DarkOnSurface
                            )
                        }
                    }
                }
            }

            // PR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentOrange.copy(alpha = 0.10f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, AccentOrange.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.exercise_pr_section).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = AccentOrange
                        )
                        Spacer(Modifier.height(4.dp))
                        val pr = state.pr
                        if (pr == null) {
                            Text(
                                stringResource(R.string.exercise_no_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkOnSurface
                            )
                        } else if (pr.isCardio) {
                            Text(
                                if (pr.bestSpeedKmh > 0)
                                    "🏆 ${pl.filebit.gymtracker.util.formatCardioNumber(pr.bestSpeedKmh)} km/h"
                                else "🏆 ${pr.maxDurationSec / 60} min",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            )
                            Text(
                                buildString {
                                    append("Najdłużej ${pr.maxDurationSec / 60} min")
                                    if (pr.totalDistanceM > 0) {
                                        append(" · łącznie ")
                                        append(pl.filebit.gymtracker.util.formatCardioNumber(pr.totalDistanceM / 1000.0))
                                        append(" km")
                                    }
                                    append(" · ${pr.totalSetsLogged} serii")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurface.copy(alpha = 0.85f)
                            )
                        } else {
                            Text(
                                "🏆 ${formatWeight(pr.maxWeightKg)} kg × ${pr.repsAtMaxWeight}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnSurface
                            )
                            Text(
                                stringResource(
                                    R.string.exercise_pr_extra,
                                    formatWeight(pr.estimated1RM),
                                    formatWeight(pr.maxVolumeKg),
                                    pr.totalSetsLogged
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Wykres
            if (state.progression.size >= 2) {
                item {
                    Text(
                        stringResource(R.string.exercise_chart_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ProgressionLineChart(
                                points = state.progression,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                valueOf = when (ex.metricType) {
                                    pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ->
                                        { p -> p.avgSpeedKmh }
                                    pl.filebit.gymtracker.data.entity.MetricType.DURATION ->
                                        { p -> p.totalDurationSec.toDouble() }
                                    else -> { p -> p.maxWeightKg }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatDate(state.progression.first().workoutDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatDate(state.progression.last().workoutDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Notatka
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.exercise_notes_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                notesText = ex.notes
                                editingNotes = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        }
                        Text(
                            ex.notes.ifBlank { stringResource(R.string.exercise_notes_empty) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ex.notes.isBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Historia
            item {
                Text(
                    stringResource(R.string.exercise_history_title, state.history.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.exercise_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.history.size) { idx ->
                    val s = state.history[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDate(s.createdAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            when (ex.metricType) {
                                pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ->
                                    listOfNotNull(
                                        s.durationSec?.takeIf { it > 0 }?.let { "${it / 60} min" },
                                        pl.filebit.gymtracker.util.cardioSpeedKmh(s.durationSec, s.distanceM)
                                            ?.let { "${pl.filebit.gymtracker.util.formatCardioNumber(it)} km/h" }
                                    ).joinToString(" · ").ifBlank { "—" }
                                pl.filebit.gymtracker.data.entity.MetricType.DURATION ->
                                    s.durationSec?.takeIf { it > 0 }?.let { "${it / 60} min" } ?: "—"
                                else -> "${formatWeight(s.weightKg)} kg × ${s.reps}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
        }
    }

    if (editingNotes) {
        AlertDialog(
            onDismissRequest = { editingNotes = false },
            title = { Text(stringResource(R.string.exercise_notes_title)) },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.exercise_notes_placeholder)) },
                    minLines = 3,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveNotes(notesText)
                    editingNotes = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNotes = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProgressionLineChart(
    points: List<ExerciseProgressionPoint>,
    modifier: Modifier = Modifier,
    valueOf: (ExerciseProgressionPoint) -> Double = { it.maxWeightKg }
) {
    if (points.isEmpty()) return
    val maxW = points.maxOf { valueOf(it) }
    val minW = points.minOf { valueOf(it) }
    val range = (maxW - minW).coerceAtLeast(1.0)

    val lineColor = AccentOrange
    val pointColor = AccentOrange
    val gridColor = DarkOutlineSoft

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 24f
        val plotW = w - 2 * padding
        val plotH = h - 2 * padding

        // grid horizontal (3 lines)
        for (i in 0..2) {
            val y = padding + plotH * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(w - padding, y),
                strokeWidth = 1f
            )
        }

        if (points.size < 2) return@Canvas
        val stepX = plotW / (points.size - 1).toFloat()
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = padding + i * stepX
            val ratio = if (range > 0) ((valueOf(p) - minW) / range).toFloat() else 0.5f
            val y = padding + plotH * (1 - ratio)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
        // dots
        points.forEachIndexed { i, p ->
            val x = padding + i * stepX
            val ratio = if (range > 0) ((valueOf(p) - minW) / range).toFloat() else 0.5f
            val y = padding + plotH * (1 - ratio)
            drawCircle(color = pointColor, radius = 5f, center = Offset(x, y))
        }
    }
}


@Composable
private fun AskAiAboutExerciseRow(
    exerciseName: String,
    onAskAi: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.40f),
                RoundedCornerShape(12.dp)
            )
            .clickable {
                onAskAi(
                    "Mam pytanie o ćwiczenie '$exerciseName'. Jak idzie mój progres? " +
                        "Czy są jakieś sygnały stagnacji lub bólu? Co zrobić żeby rosnąć?"
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = pl.filebit.gymtracker.ui.theme.AccentOrange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                "Zapytaj AI o to ćwiczenie",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = pl.filebit.gymtracker.ui.theme.AccentOrange
            )
            androidx.compose.material3.Text(
                "Analiza progresu, sugestie, pytania",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
        }
    }
}


@Composable
private fun ExercisePreferenceCard(
    isFavorite: Boolean,
    isAvoided: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAvoided: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "PREFERENCJE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "AI uwzględnia te flagi przy generowaniu i poprawianiu planu",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreferenceChip(
                    label = if (isFavorite) "Lubię ✓" else "Lubię",
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    active = isFavorite,
                    activeColor = SuccessGreen,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                PreferenceChip(
                    label = if (isAvoided) "Unikam ✓" else "Unikam",
                    icon = Icons.Filled.Block,
                    active = isAvoided,
                    activeColor = ErrorRed,
                    onClick = onToggleAvoided,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreferenceChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (active) activeColor.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
    val border = if (active) activeColor.copy(alpha = 0.5f) else DarkOutlineSoft
    val textColor = if (active) activeColor else DarkOnSurface
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}



/**
 * v1.25.0 — GIF animacja wykonania ćwiczenia z CDN ExerciseDB (Coil 3 + GifDecoder).
 * Cloudflare-cache'owane, Coil disk-cache po pierwszym wyświetleniu (offline).
 */
@Composable
private fun ExerciseGifCard(gifUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "ANIMACJA WYKONANIA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                ),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            coil3.compose.AsyncImage(
                model = gifUrl,
                contentDescription = "Animacja ćwiczenia",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(DarkBg, RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}

/**
 * v1.25.0 — Technika krok po kroku. PL z AI cache jeśli istnieje, inaczej EN fallback +
 * przycisk "Tłumacz AI". Po tłumaczeniu cache w `instructionsPlJson` (offline next time).
 */
@Composable
private fun ExerciseInstructionsCard(
    instructionsPlJson: String?,
    instructionsEnJson: String?,
    translating: Boolean,
    onTranslate: () -> Unit
) {
    // v1.25.0: parsuj JSON list jako JsonArray (prostsze niż ListSerializer<String>)
    val plSteps: List<String> = remember(instructionsPlJson) {
        runCatching {
            instructionsPlJson?.let { json ->
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(json)
                    as? kotlinx.serialization.json.JsonArray
                arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        }.getOrNull().orEmpty()
    }
    val enSteps: List<String> = remember(instructionsEnJson) {
        runCatching {
            instructionsEnJson?.let { json ->
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(json)
                    as? kotlinx.serialization.json.JsonArray
                arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        }.getOrNull().orEmpty()
    }
    val showPl = plSteps.isNotEmpty()
    val steps = if (showPl) plSteps else enSteps.map { it.removePrefix("Step:").trimStart { it.isDigit() || it == ' ' } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TECHNIKA KROK PO KROKU" + if (!showPl && enSteps.isNotEmpty()) " (EN)" else "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!showPl && enSteps.isNotEmpty()) {
                    if (translating) {
                        Text(
                            "Tłumaczę...",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentOrange
                        )
                    } else {
                        TextButton(onClick = onTranslate) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Tłumacz AI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AccentOrange
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { idx, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${idx + 1}.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentOrange,
                        modifier = Modifier.size(width = 24.dp, height = 22.dp)
                    )
                    Text(
                        step.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = DarkOnSurface
                    )
                }
                if (idx < steps.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * v1.25.0 — Mięśnie docelowe + drugorzędne + sprzęt z ExerciseDB.
 * Dokładniejsze niż lokalne enum MuscleGroup/Equipment (np. "pectorals", "rotator cuff").
 */
@Composable
private fun ExerciseMusclesEquipmentCard(
    targetMuscles: List<String>,
    secondaryMuscles: List<String>,
    equipment: List<String>
) {
    // v1.25.1: tłumacz EN → PL przez statyczny mapping (zero AI cost)
    val mapMuscle: (String) -> String = { pl.filebit.gymtracker.util.ExerciseDbLabels.muscle(it) }
    val mapEquip: (String) -> String = { pl.filebit.gymtracker.util.ExerciseDbLabels.equipment(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (targetMuscles.isNotEmpty()) {
                Text(
                    "🎯 GŁÓWNE MIĘŚNIE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    targetMuscles.joinToString(", ") { mapMuscle(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(10.dp))
            }
            if (secondaryMuscles.isNotEmpty()) {
                Text(
                    "💪 MIĘŚNIE DRUGORZĘDNE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    secondaryMuscles.joinToString(", ") { mapMuscle(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(10.dp))
            }
            if (equipment.isNotEmpty()) {
                Text(
                    "🛠 SPRZĘT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    equipment.joinToString(", ") { mapEquip(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
            }
        }
    }
}

@Composable
private fun ProgressionStatusCard(trend: ExerciseTrend) {
    val (accent, icon, headline) = when (trend.status) {
        ProgressionStatus.PROGRESSING -> Triple(
            SuccessGreen,
            Icons.Filled.TrendingUp,
            "Postęp"
        )
        ProgressionStatus.STAGNATING -> Triple(
            AccentOrange,
            Icons.Filled.TrendingFlat,
            "Stagnacja"
        )
        ProgressionStatus.REGRESSING -> Triple(
            ErrorRed,
            Icons.Filled.TrendingDown,
            "Regres"
        )
        ProgressionStatus.INSUFFICIENT_DATA -> Triple(
            DarkOnSurfaceVariant,
            Icons.Filled.HelpOutline,
            "Za mało danych"
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "STATUS PROGRESJI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    headline,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = accent
                )
            }
            if (trend.status != ProgressionStatus.INSUFFICIENT_DATA) {
                Spacer(Modifier.height(8.dp))
                val pctStr = if (trend.percentChange >= 0) "+%.1f%%".format(trend.percentChange)
                else "%.1f%%".format(trend.percentChange)
                Text(
                    "e1RM $pctStr w 6 tyg. (${trend.sessionsAnalyzed} sesji)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(4.dp))
                val msg = when (trend.status) {
                    ProgressionStatus.PROGRESSING -> "Świetnie. Trzymaj plan, kontynuuj progresję."
                    ProgressionStatus.STAGNATING -> "Plateau >4 tyg. Rozważ deload, zmianę repów lub techniki."
                    ProgressionStatus.REGRESSING -> "Spadek formy. Sprawdź sen, dietę, stres. Możliwy deload."
                    else -> ""
                }
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Potrzeba ≥3 sesji w 6 tyg. żeby ocenić trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// v2.0.0 — CANONICAL EXERCISE-DB UI SECTIONS
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanonicalMetadataChips(ex: pl.filebit.gymtracker.data.entity.Exercise) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ex.movementPattern?.let { mp ->
                    MetadataChip("🎯 ${mp.displayName()}")
                }
                ex.category?.let { c ->
                    MetadataChip(c.displayName())
                }
                ex.levelMin?.let { l ->
                    MetadataChip("📊 ${l.displayName()}")
                }
                ex.difficulty1To10?.let { d ->
                    MetadataChip("⚡ ${d}/10")
                }
                ex.mechanic?.let { m ->
                    MetadataChip(m.displayName())
                }
                ex.force?.let { f ->
                    MetadataChip(f.displayName())
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(label: String) {
    Box(
        modifier = Modifier
            .background(DarkSurfaceVariant, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DarkOnSurface)
    }
}

/**
 * v2.0.0 — wskazówki coaching z canonical: setup + execution + oddech.
 * Parsing tolerujący: jeśli pole nie istnieje albo malformed, pomija sekcję.
 */
@Composable
private fun CoachingCuesCard(coachingJson: String) {
    val (setupCues, executionCues, breathing) = remember(coachingJson) {
        parseCoachingCues(coachingJson)
    }
    if (setupCues.isEmpty() && executionCues.isEmpty() && breathing.isNullOrBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "WSKAZÓWKI TECHNICZNE",
                style = MaterialTheme.typography.labelLarge,
                color = DarkOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (setupCues.isNotEmpty()) {
                Text(
                    "Setup",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                setupCues.forEach { cue ->
                    Text("• $cue", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (executionCues.isNotEmpty()) {
                Text(
                    "Wykonanie",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                executionCues.forEach { cue ->
                    Text("• $cue", style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!breathing.isNullOrBlank()) {
                Text(
                    "Oddech",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(breathing, style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
            }
        }
    }
}

private fun parseCoachingCues(json: String): Triple<List<String>, List<String>, String?> {
    return try {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        val setup = root["setup_cues_pl"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: root["setup_cues"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        val execution = root["execution_cues_pl"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: root["execution_cues"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        val breathing = root["breathingPl"]?.jsonPrimitive?.content
            ?: root["breathing"]?.jsonPrimitive?.content
        Triple(setup, execution, breathing)
    } catch (_: Throwable) {
        Triple(emptyList(), emptyList(), null)
    }
}

@Composable
private fun CommonFaultsCard(faultsJson: String) {
    val faults = remember(faultsJson) { parseCommonFaults(faultsJson) }
    if (faults.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "NAJCZĘSTSZE BŁĘDY",
                style = MaterialTheme.typography.labelLarge,
                color = DarkOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            faults.forEachIndexed { idx, fault ->
                Column {
                    Text(
                        "⚠️ ${fault.first}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "💡 ${fault.second}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurface
                    )
                }
                if (idx < faults.size - 1) {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun parseCommonFaults(json: String): List<Pair<String, String>> {
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val fault = o["faultPl"]?.jsonPrimitive?.content
                ?: o["fault"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val cue = o["cuePl"]?.jsonPrimitive?.content
                ?: o["cue"]?.jsonPrimitive?.content
                ?: ""
            fault to cue
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

@Composable
private fun ContraindicationsCard(contraindicationsJson: String) {
    val items = remember(contraindicationsJson) { parseContraindications(contraindicationsJson) }
    if (items.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "PRZECIWWSKAZANIA",
                style = MaterialTheme.typography.labelLarge,
                color = DarkOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { idx, (condition, severity, modification) ->
                val severityEmoji = when (severity.lowercase()) {
                    "avoid" -> "🚫"
                    "modify" -> "✏️"
                    "caution" -> "⚠️"
                    else -> "ℹ️"
                }
                Column {
                    Text(
                        "$severityEmoji ${condition.replace("_", " ").replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (severity == "avoid") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    if (modification.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(modification, style = MaterialTheme.typography.bodyMedium, color = DarkOnSurface)
                    }
                }
                if (idx < items.size - 1) {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun parseContraindications(json: String): List<Triple<String, String, String>> {
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val condition = o["condition"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val severity = o["severity"]?.jsonPrimitive?.content ?: "caution"
            val modification = o["modificationPl"]?.jsonPrimitive?.content
                ?: o["modification"]?.jsonPrimitive?.content
                ?: ""
            Triple(condition, severity, modification)
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

/**
 * v2.2.0 — karta powiązanych ćwiczeń z canonical.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedExercisesCard(
    prerequisitesJson: String?,
    progressionToJson: String?,
    alternativesJson: String?,
    relatedNames: Map<String, String>
) {
    val prereq = remember(prerequisitesJson) { parseSlugArray(prerequisitesJson) }
    val progression = remember(progressionToJson) { parseSlugArray(progressionToJson) }
    val alternatives = remember(alternativesJson) { parseSlugArray(alternativesJson) }

    if (prereq.isEmpty() && progression.isEmpty() && alternatives.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "POWIĄZANE ĆWICZENIA",
                style = MaterialTheme.typography.labelLarge,
                color = DarkOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            if (prereq.isNotEmpty()) {
                RelatedSection("📋 Wymagane wcześniej (prerequisites)", prereq, relatedNames)
            }
            if (progression.isNotEmpty()) {
                RelatedSection("📈 Cięższe wersje (progresja)", progression, relatedNames)
            }
            if (alternatives.isNotEmpty()) {
                RelatedSection("🔁 Alternatywy", alternatives, relatedNames)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedSection(title: String, slugs: List<String>, names: Map<String, String>) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            slugs.forEach { slug ->
                val name = names[slug] ?: slug
                Box(
                    modifier = Modifier
                        .background(DarkSurfaceVariant, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = DarkOnSurface)
                }
            }
        }
    }
}

private fun parseSlugArray(json: String?): List<String> {
    if (json.isNullOrBlank() || json == "[]") return emptyList()
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray
            .mapNotNull { it.jsonPrimitive.content }
    } catch (_: Throwable) {
        emptyList()
    }
}

