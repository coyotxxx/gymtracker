package pl.filebit.gymtracker.ui.workout

import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.service.RestTimerService
import pl.filebit.gymtracker.ui.components.RestTimerSheet
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight

private const val TAG = "ActiveWorkout"

private inline fun safeTimer(action: () -> Unit) {
    try {
        action()
    } catch (e: SecurityException) {
        Log.e(TAG, "Rest timer call failed (no permission)", e)
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Rest timer call failed (illegal state)", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onAddExerciseClick: () -> Unit,
    onWorkoutFinished: () -> Unit,
    onSavedAsPlan: (Long) -> Unit,
    vm: ActiveWorkoutViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timerState by RestTimerService.state.collectAsStateWithLifecycle()
    val pendingPRs by vm.pendingPRs.collectAsStateWithLifecycle()
    val pendingTips by vm.pendingTips.collectAsStateWithLifecycle()
    val pendingStagnation by vm.pendingStagnation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFinishDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Auto-tick czasu trwania treningu (Compose timer trigger)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.workout?.id) {
        if (state.workout != null) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                nowMillis = System.currentTimeMillis()
            }
        }
    }

    val durationText by remember(state.workout, nowMillis) {
        derivedStateOf {
            val w = state.workout ?: return@derivedStateOf "0:00"
            formatDuration(nowMillis - w.startedAt)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trening", style = MaterialTheme.typography.titleMedium)
                        Text(
                            durationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showNotesDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
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
                                text = { Text(stringResource(R.string.workout_finish)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showFinishDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.workout_no_exercises),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.groups, key = { it.exercise.id }) { group ->
                            ExerciseGroupCard(
                                group = group,
                                defaultRestSec = state.profile.defaultRestSeconds,
                                showAdvanced = state.profile.showAdvancedSetFields,
                                onAddSet = { reps, weight ->
                                    vm.addSet(group.exercise.id, reps, weight)
                                    safeTimer { RestTimerService.start(context, state.profile.defaultRestSeconds, state.profile.flashOnTimerEnd) }
                                },
                                onUpdateSet = vm::updateSet,
                                onDeleteSet = vm::deleteSet,
                                onRemoveExercise = { vm.removeExercise(group.exercise.id) }
                            )
                        }
                    }
                }
            }

            // FAB Add Exercise
            Button(
                onClick = onAddExerciseClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.workout_add_exercise))
            }

            // Pełnoekranowy timer (ModalBottomSheet) na poziomie Box
            RestTimerSheet(
                visible = timerState.running,
                remainingSec = timerState.remainingSec,
                totalSec = timerState.totalSec,
                paused = timerState.paused,
                onAdd = { safeTimer { RestTimerService.addSeconds(context, 15) } },
                onSub = { safeTimer { RestTimerService.addSeconds(context, -15) } },
                onTogglePause = {
                    safeTimer { RestTimerService.togglePause(context, timerState.paused) }
                },
                onSkip = { safeTimer { RestTimerService.stop(context) } },
                onDismiss = { safeTimer { RestTimerService.stop(context) } }
            )
        }
    }

    if (pendingPRs.isNotEmpty() || pendingTips.isNotEmpty() || pendingStagnation.isNotEmpty()) {
        FinishSummaryDialog(
            prs = pendingPRs,
            tips = pendingTips,
            stagnation = pendingStagnation,
            onDismiss = {
                vm.consumePendingPRs()
                vm.consumePendingTips()
                vm.consumePendingStagnation()
            }
        )
    }

    // Post-workout feedback sheet (po PR/Tips/Stagnation lub od razu jeśli ich nie było)
    val pendingFeedbackId by vm.pendingFeedbackId.collectAsStateWithLifecycle()
    val showFeedback = pendingFeedbackId != null &&
        pendingPRs.isEmpty() && pendingTips.isEmpty() && pendingStagnation.isEmpty()
    if (showFeedback) {
        PostWorkoutFeedbackSheet(
            onSkip = { vm.consumePendingFeedback(save = false) },
            onSave = { rating, area, notes ->
                vm.consumePendingFeedback(save = true, rating, area, notes)
            }
        )
    }

    if (showNotesDialog) {
        WorkoutNotesDialog(
            initial = state.workout?.notes ?: "",
            onSave = { newNotes ->
                vm.setNotes(newNotes)
                showNotesDialog = false
            },
            onDismiss = { showNotesDialog = false }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.workout_finish_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    safeTimer { RestTimerService.stop(context) }
                    vm.finishWorkout(onWorkoutFinished)
                }) { Text(stringResource(R.string.workout_finish_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(R.string.workout_finish_no))
                }
            }
        )
    }
}

@Composable
private fun FinishSummaryDialog(
    prs: List<NewPrWithName>,
    tips: List<pl.filebit.gymtracker.data.repository.ProgressionTip>,
    stagnation: List<pl.filebit.gymtracker.data.repository.StagnationAlert>,
    onDismiss: () -> Unit
) {
    val title = when {
        prs.isNotEmpty() -> stringResource(R.string.pr_dialog_title)
        tips.isNotEmpty() -> stringResource(R.string.tip_dialog_title)
        else -> stringResource(R.string.stagnation_dialog_title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (prs.isNotEmpty()) {
                    prs.forEach { p ->
                        Text(
                            "🏆 ${p.exerciseName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                R.string.pr_dialog_line,
                                pl.filebit.gymtracker.util.formatWeight(p.pr.weightKg),
                                p.pr.reps,
                                pl.filebit.gymtracker.util.formatWeight(p.pr.new1RM)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (tips.isNotEmpty()) {
                    if (prs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.tip_dialog_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = pl.filebit.gymtracker.ui.theme.SuccessGreen
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    tips.forEach { t ->
                        Text(
                            "💡 ${t.exerciseName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                R.string.tip_dialog_line,
                                pl.filebit.gymtracker.util.formatWeight(t.suggestedWeightKg),
                                pl.filebit.gymtracker.util.formatWeight(t.currentWeightKg)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = pl.filebit.gymtracker.ui.theme.SuccessGreen
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (stagnation.isNotEmpty()) {
                    if (prs.isNotEmpty() || tips.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.stagnation_dialog_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    stagnation.forEach { s ->
                        Text(
                            "⚠️ ${s.exerciseName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                R.string.stagnation_dialog_line,
                                pl.filebit.gymtracker.util.formatWeight(s.stuckAtKg),
                                s.workoutsAtSameWeight
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pr_dialog_ok))
            }
        }
    )
}

@Composable
private fun WorkoutNotesDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_notes_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.workout_notes_placeholder)) },
                minLines = 3,
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ExerciseGroupCard(
    group: ExerciseGroup,
    defaultRestSec: Int,
    showAdvanced: Boolean,
    onAddSet: (reps: Int, weight: Double) -> Unit,
    onUpdateSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
    onRemoveExercise: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    group.previousSessionSummary?.let { summary ->
                        Text(
                            "Ostatnio: $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: run {
                        if (group.lastSessionWeight != null && group.lastSessionReps != null) {
                            Text(
                                "Ostatnio: ${formatWeight(group.lastSessionWeight)} kg × ${group.lastSessionReps}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    group.suggestion?.let { sug ->
                        val arrow = when {
                            sug.suggestedWeightKg > sug.previousWeightKg -> "↑"
                            sug.suggestedReps > sug.previousReps -> "↑"
                            else -> "→"
                        }
                        Text(
                            "💡 Sugestia: $arrow ${formatWeight(sug.suggestedWeightKg)} kg × ${sug.suggestedReps}",
                            style = MaterialTheme.typography.bodySmall,
                            color = pl.filebit.gymtracker.ui.theme.SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workout_remove_exercise)) },
                            onClick = {
                                menuExpanded = false
                                onRemoveExercise()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Header row — różne dla różnych metricType
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell("Set", weight = 0.6f)
                when (group.exercise.metricType) {
                    pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS -> {
                        HeaderCell("kg", weight = 1f)
                        HeaderCell(stringResource(R.string.workout_reps), weight = 1f)
                    }
                    pl.filebit.gymtracker.data.entity.MetricType.REPS_ONLY -> {
                        HeaderCell(stringResource(R.string.workout_reps), weight = 2f)
                    }
                    pl.filebit.gymtracker.data.entity.MetricType.DURATION -> {
                        HeaderCell("Czas (s)", weight = 2f)
                    }
                    pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION -> {
                        HeaderCell("km", weight = 1f)
                        HeaderCell("min", weight = 1f)
                    }
                    pl.filebit.gymtracker.data.entity.MetricType.DURATION_WEIGHT -> {
                        HeaderCell("kg", weight = 1f)
                        HeaderCell("s", weight = 1f)
                    }
                }
                Spacer(Modifier.width(48.dp)) // for ✓ button
            }

            Spacer(Modifier.height(4.dp))

            group.sets.forEach { set ->
                SetRow(
                    set = set,
                    metricType = group.exercise.metricType,
                    onUpdate = onUpdateSet,
                    onDelete = onDeleteSet
                )
                if (showAdvanced) {
                    AdvancedFieldsRow(
                        set = set,
                        onUpdate = onUpdateSet
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add set button — auto-fill: ostatni set bieżącej sesji > sugestia AI > ostatnia sesja
            TextButton(
                onClick = {
                    val last = group.sets.lastOrNull()
                    onAddSet(
                        last?.reps ?: group.suggestion?.suggestedReps ?: group.lastSessionReps ?: 8,
                        last?.weightKg ?: group.suggestion?.suggestedWeightKg ?: group.lastSessionWeight ?: 20.0
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.workout_add_set))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    if (weight == 0f) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Box(modifier = Modifier.weight(weight)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdvancedFieldsRow(
    set: WorkoutSet,
    onUpdate: (WorkoutSet) -> Unit
) {
    var rpeText by remember(set.id) { mutableStateOf(set.rpe?.toString() ?: "") }
    var tempoText by remember(set.id) { mutableStateOf(set.tempo ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 48.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = rpeText,
            onValueChange = {
                rpeText = it.filter { c -> c.isDigit() }
                onUpdate(set.copy(rpe = rpeText.toIntOrNull()))
            },
            label = { Text("RPE", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = tempoText,
            onValueChange = {
                tempoText = it
                onUpdate(set.copy(tempo = if (tempoText.isBlank()) null else tempoText))
            },
            label = { Text("Tempo", style = MaterialTheme.typography.bodySmall) },
            placeholder = { Text("3-1-1-0", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun SetRow(
    set: WorkoutSet,
    metricType: pl.filebit.gymtracker.data.entity.MetricType,
    onUpdate: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(formatWeight(set.weightKg)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }
    var durationText by remember(set.id) { mutableStateOf(set.durationSec?.toString() ?: "") }
    var distanceKmText by remember(set.id) {
        mutableStateOf(set.distanceM?.let { (it / 1000.0).let { km -> "%.2f".format(km).replace(',', '.') } } ?: "")
    }
    var completed by remember(set.id) { mutableStateOf(set.isCompleted) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set number
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (completed) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${set.setNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.width(8.dp))

        when (metricType) {
            pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS -> {
                NumField(weightText, { v ->
                    val filtered = pl.filebit.gymtracker.util.filterWeightInput(v)
                    weightText = filtered
                    filtered.replace(',', '.').toDoubleOrNull()?.let { onUpdate(set.copy(weightKg = it)) }
                }, KeyboardType.Decimal, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NumField(repsText, { v ->
                    repsText = v
                    v.toIntOrNull()?.let { onUpdate(set.copy(reps = it)) }
                }, KeyboardType.Number, Modifier.weight(1f))
            }
            pl.filebit.gymtracker.data.entity.MetricType.REPS_ONLY -> {
                NumField(repsText, { v ->
                    repsText = v
                    v.toIntOrNull()?.let { onUpdate(set.copy(reps = it)) }
                }, KeyboardType.Number, Modifier.weight(2f))
            }
            pl.filebit.gymtracker.data.entity.MetricType.DURATION -> {
                NumField(durationText, { v ->
                    durationText = v
                    onUpdate(set.copy(durationSec = v.toIntOrNull()))
                }, KeyboardType.Number, Modifier.weight(2f))
            }
            pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION -> {
                NumField(distanceKmText, { v ->
                    distanceKmText = v
                    val km = v.replace(',', '.').toDoubleOrNull()
                    onUpdate(set.copy(distanceM = km?.let { it * 1000.0 }))
                }, KeyboardType.Decimal, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NumField(durationText, { v ->
                    durationText = v
                    onUpdate(set.copy(durationSec = v.toIntOrNull()?.let { it * 60 }))
                }, KeyboardType.Number, Modifier.weight(1f))
            }
            pl.filebit.gymtracker.data.entity.MetricType.DURATION_WEIGHT -> {
                NumField(weightText, { v ->
                    val filtered = pl.filebit.gymtracker.util.filterWeightInput(v)
                    weightText = filtered
                    filtered.replace(',', '.').toDoubleOrNull()?.let { onUpdate(set.copy(weightKg = it)) }
                }, KeyboardType.Decimal, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NumField(durationText, { v ->
                    durationText = v
                    onUpdate(set.copy(durationSec = v.toIntOrNull()))
                }, KeyboardType.Number, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.width(8.dp))

        // Done check button
        IconButton(
            onClick = {
                completed = !completed
                onUpdate(set.copy(isCompleted = completed))
            },
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (completed) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (completed) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NumField(
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
