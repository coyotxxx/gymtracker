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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.service.RestTimerService
import pl.filebit.gymtracker.ui.components.RestTimerSheet
import pl.filebit.gymtracker.util.formatDuration
import pl.filebit.gymtracker.util.formatWeight
import kotlin.math.roundToInt

private const val COACH_TAG = "CoachWorkout"

private inline fun safeCoachTimer(action: () -> Unit) {
    try {
        action()
    } catch (e: SecurityException) {
        Log.e(COACH_TAG, "Rest timer call failed", e)
    } catch (e: IllegalStateException) {
        Log.e(COACH_TAG, "Rest timer call failed", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachWorkoutScreen(
    onWorkoutFinished: () -> Unit,
    vm: CoachWorkoutViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timerState by RestTimerService.state.collectAsStateWithLifecycle()
    val pendingPRs by vm.pendingPRs.collectAsStateWithLifecycle()
    val pendingTips by vm.pendingTips.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }

    // Live duration tick
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
                        Text(
                            state.planName.ifBlank { stringResource(R.string.coach_workout_title) },
                            style = MaterialTheme.typography.titleMedium
                        )
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
                    TextButton(onClick = { showFinishDialog = true }) {
                        Text(stringResource(R.string.workout_finish))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.workout == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.coach_no_active))
                    }
                }
                state.isComplete -> CoachCompleteContent(
                    onFinish = { vm.finishWorkout(onWorkoutFinished) }
                )
                state.currentSet != null && state.currentExercise != null -> CoachActiveContent(
                    state = state,
                    onConfirmTap = { showConfirmDialog = true },
                    onSkip = { vm.skipCurrentSet() }
                )
            }

            // Pełnoekranowy timer
            RestTimerSheet(
                visible = timerState.running,
                remainingSec = timerState.remainingSec,
                totalSec = timerState.totalSec,
                onAdd = { safeCoachTimer { RestTimerService.addSeconds(context, 15) } },
                onSub = { safeCoachTimer { RestTimerService.addSeconds(context, -15) } },
                onSkip = { safeCoachTimer { RestTimerService.stop(context) } },
                onDismiss = { safeCoachTimer { RestTimerService.stop(context) } }
            )
        }
    }

    if (showConfirmDialog) {
        val planned = state.currentSet?.reps ?: 0
        val restSec = state.defaultRestSeconds
        // showAdvanced trzymane w state.workout? — niedostępne; pobieramy z VM.profile
        // Dla MVP: zawsze pokazuj RPE slider w Coach mode (opcjonalny, można pominąć)
        ConfirmRepsDialog(
            plannedReps = planned,
            onConfirm = { actualReps, actualRpe ->
                showConfirmDialog = false
                vm.confirmCurrentSet(actualReps, actualRpe) {
                    safeCoachTimer {
                        RestTimerService.start(context, restSec)
                    }
                }
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    if (pendingPRs.isNotEmpty() || pendingTips.isNotEmpty()) {
        val title = if (pendingPRs.isNotEmpty())
            stringResource(R.string.pr_dialog_title)
        else stringResource(R.string.tip_dialog_title)
        AlertDialog(
            onDismissRequest = {
                vm.consumePendingPRs()
                vm.consumePendingTips()
                onWorkoutFinished()
            },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (pendingPRs.isNotEmpty()) {
                        pendingPRs.forEach { p ->
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
                    if (pendingTips.isNotEmpty()) {
                        if (pendingPRs.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.tip_dialog_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        pendingTips.forEach { t ->
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
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.consumePendingPRs()
                    vm.consumePendingTips()
                    onWorkoutFinished()
                }) { Text(stringResource(R.string.pr_dialog_ok)) }
            }
        )
    }

    if (showNotesDialog) {
        var text by remember { mutableStateOf(state.workout?.notes ?: "") }
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
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
                TextButton(onClick = {
                    vm.setNotes(text)
                    showNotesDialog = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.workout_finish_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    safeCoachTimer { RestTimerService.stop(context) }
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
private fun CoachActiveContent(
    state: CoachUiState,
    onConfirmTap: () -> Unit,
    onSkip: () -> Unit
) {
    val current = state.currentSet ?: return
    val exercise = state.currentExercise ?: return
    val progress = if (state.totalSets > 0) state.completedSets.toFloat() / state.totalSets else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exercise progress
        Text(
            stringResource(
                R.string.coach_exercise_n_of_m,
                state.currentExerciseIndex + 1,
                state.totalExercises
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Big card with current set
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        R.string.coach_set_n_of_m,
                        state.currentSetIndexInExercise + 1,
                        state.totalSetsInCurrentExercise
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BigStatColumn(
                        label = stringResource(R.string.coach_weight_label),
                        value = if (current.weightKg > 0)
                            "${formatWeight(current.weightKg)} kg" else "—"
                    )
                    BigStatColumn(
                        label = stringResource(R.string.coach_reps_label),
                        value = "${current.reps}"
                    )
                }
                state.lastSetForCurrent?.let { last ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(
                            R.string.coach_last_session,
                            formatWeight(last.weightKg),
                            last.reps
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Action buttons
        Button(
            onClick = onConfirmTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.coach_done_button),
                style = MaterialTheme.typography.titleLarge
            )
        }

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.coach_skip_button))
        }

        Spacer(Modifier.height(8.dp))

        // Progress indicator
        Column {
            Text(
                stringResource(
                    R.string.coach_progress,
                    state.completedSets,
                    state.totalSets,
                    (progress * 100).roundToInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoachCompleteContent(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "🏆",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.coach_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.coach_complete_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.coach_complete_finish))
        }
    }
}

@Composable
private fun BigStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ConfirmRepsDialog(
    plannedReps: Int,
    onConfirm: (Int, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var actualReps by remember { mutableStateOf(plannedReps) }
    var rpe by remember { mutableStateOf(0) } // 0 = nie ustawione

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.coach_confirm_title, plannedReps))
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.coach_confirm_text),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$actualReps",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = actualReps.toFloat(),
                    onValueChange = { actualReps = it.roundToInt() },
                    valueRange = 0f..30f,
                    steps = 29
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.coach_rpe_label, if (rpe == 0) "—" else rpe.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = rpe.toFloat(),
                    onValueChange = { rpe = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(actualReps, if (rpe == 0) null else rpe)
            }) {
                Text(stringResource(R.string.coach_confirm_button, actualReps))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
