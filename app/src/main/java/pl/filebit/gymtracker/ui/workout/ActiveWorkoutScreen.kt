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
    val context = LocalContext.current

    var showFinishDialog by remember { mutableStateOf(false) }
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
                                onAddSet = { reps, weight ->
                                    vm.addSet(group.exercise.id, reps, weight)
                                    safeTimer { RestTimerService.start(context, state.profile.defaultRestSeconds) }
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
                onAdd = { safeTimer { RestTimerService.addSeconds(context, 15) } },
                onSub = { safeTimer { RestTimerService.addSeconds(context, -15) } },
                onSkip = { safeTimer { RestTimerService.stop(context) } },
                onDismiss = { safeTimer { RestTimerService.stop(context) } }
            )
        }
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
private fun ExerciseGroupCard(
    group: ExerciseGroup,
    defaultRestSec: Int,
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
                    if (group.lastSessionWeight != null && group.lastSessionReps != null) {
                        Text(
                            "Ostatnio: ${formatWeight(group.lastSessionWeight)} kg × ${group.lastSessionReps}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell("Set", weight = 0.6f)
                HeaderCell("kg", weight = 1f)
                HeaderCell(stringResource(R.string.workout_reps), weight = 1f)
                Spacer(Modifier.width(48.dp)) // for ✓ button
            }

            Spacer(Modifier.height(4.dp))

            group.sets.forEach { set ->
                SetRow(
                    set = set,
                    onUpdate = onUpdateSet,
                    onDelete = onDeleteSet
                )
            }

            Spacer(Modifier.height(8.dp))

            // Add set button
            TextButton(
                onClick = {
                    val last = group.sets.lastOrNull()
                    onAddSet(
                        last?.reps ?: group.lastSessionReps ?: 8,
                        last?.weightKg ?: group.lastSessionWeight ?: 20.0
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
private fun SetRow(
    set: WorkoutSet,
    onUpdate: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(formatWeight(set.weightKg)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }
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

        // Weight
        OutlinedTextField(
            value = weightText,
            onValueChange = { v ->
                weightText = v
                v.replace(',', '.').toDoubleOrNull()?.let {
                    onUpdate(set.copy(weightKg = it))
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Spacer(Modifier.width(8.dp))

        // Reps
        OutlinedTextField(
            value = repsText,
            onValueChange = { v ->
                repsText = v
                v.toIntOrNull()?.let {
                    onUpdate(set.copy(reps = it))
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

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
