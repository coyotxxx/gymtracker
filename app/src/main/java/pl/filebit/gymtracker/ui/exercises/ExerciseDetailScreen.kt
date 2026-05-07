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
import pl.filebit.gymtracker.ui.theme.ErrorRed
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
                                    .height(180.dp)
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
                            "${formatWeight(s.weightKg)} kg × ${s.reps}",
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
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxW = points.maxOf { it.maxWeightKg }
    val minW = points.minOf { it.maxWeightKg }
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
            val ratio = if (range > 0) ((p.maxWeightKg - minW) / range).toFloat() else 0.5f
            val y = padding + plotH * (1 - ratio)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
        // dots
        points.forEachIndexed { i, p ->
            val x = padding + i * stepX
            val ratio = if (range > 0) ((p.maxWeightKg - minW) / range).toFloat() else 0.5f
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

