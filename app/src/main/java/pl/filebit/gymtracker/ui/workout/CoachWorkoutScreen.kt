package pl.filebit.gymtracker.ui.workout

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.outlined.Info
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
    val pendingStagnation by vm.pendingStagnation.collectAsStateWithLifecycle()
    val aiOpinion by vm.aiOpinion.collectAsStateWithLifecycle()
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
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
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
                    onSkip = { vm.skipCurrentSet() },
                    onAskAiOpinion = { vm.askAiOpinion() }
                )
            }

            // Pełnoekranowy timer
            RestTimerSheet(
                visible = timerState.running,
                remainingSec = timerState.remainingSec,
                totalSec = timerState.totalSec,
                paused = timerState.paused,
                exerciseName = state.currentExercise?.name,
                onAdd = { safeCoachTimer { RestTimerService.addSeconds(context, 15) } },
                onSub = { safeCoachTimer { RestTimerService.addSeconds(context, -15) } },
                onTogglePause = {
                    safeCoachTimer { RestTimerService.togglePause(context, timerState.paused) }
                },
                onSkip = { safeCoachTimer { RestTimerService.stop(context) } },
                onDismiss = { safeCoachTimer { RestTimerService.stop(context) } }
            )
        }
    }

    if (showConfirmDialog) {
        val planned = state.currentSet?.reps ?: 0
        val plannedWeight = state.currentSet?.weightKg ?: 0.0
        val setNum = state.currentSetIndexInExercise + 1
        val totalSets = state.totalSetsInCurrentExercise
        val restSec = state.defaultRestSeconds
        ConfirmRepsDialog(
            plannedReps = planned,
            plannedWeightKg = plannedWeight,
            setNumber = setNum,
            totalSets = totalSets,
            onConfirm = { actualReps, actualRpe ->
                showConfirmDialog = false
                val flash = state.flashOnTimerEnd
                vm.confirmCurrentSet(actualReps, actualRpe) {
                    safeCoachTimer {
                        RestTimerService.start(context, restSec, flash)
                    }
                }
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    if (pendingPRs.isNotEmpty() || pendingTips.isNotEmpty() || pendingStagnation.isNotEmpty()) {
        val title = when {
            pendingPRs.isNotEmpty() -> stringResource(R.string.pr_dialog_title)
            pendingTips.isNotEmpty() -> stringResource(R.string.tip_dialog_title)
            else -> stringResource(R.string.stagnation_dialog_title)
        }
        AlertDialog(
            onDismissRequest = {
                vm.consumePendingPRs()
                vm.consumePendingTips()
                vm.consumePendingStagnation()
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
                            val tipKind = t.kind
                            val tipText = when (tipKind) {
                                pl.filebit.gymtracker.data.repository.ProgressionKind.INCREASE_WEIGHT ->
                                    "${pl.filebit.gymtracker.util.formatWeight(t.currentWeightKg)} → ${pl.filebit.gymtracker.util.formatWeight(t.suggestedWeightKg)} kg"
                                pl.filebit.gymtracker.data.repository.ProgressionKind.INCREASE_REPS ->
                                    "${t.currentReps} → ${t.suggestedReps} powt. (${pl.filebit.gymtracker.util.formatWeight(t.suggestedWeightKg)} kg)"
                                pl.filebit.gymtracker.data.repository.ProgressionKind.DELOAD ->
                                    "${pl.filebit.gymtracker.util.formatWeight(t.currentWeightKg)} → ${pl.filebit.gymtracker.util.formatWeight(t.suggestedWeightKg)} kg (deload)"
                                pl.filebit.gymtracker.data.repository.ProgressionKind.NO_CHANGE ->
                                    "utrzymaj plan (${pl.filebit.gymtracker.util.formatWeight(t.currentWeightKg)} kg × ${t.currentReps})"
                            }
                            val tipIcon = when (tipKind) {
                                pl.filebit.gymtracker.data.repository.ProgressionKind.DELOAD -> "🔻"
                                pl.filebit.gymtracker.data.repository.ProgressionKind.NO_CHANGE -> "⏸"
                                else -> "💡"
                            }
                            Text(
                                "$tipIcon ${t.exerciseName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                tipText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                t.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tipKind != pl.filebit.gymtracker.data.repository.ProgressionKind.NO_CHANGE) {
                                Spacer(Modifier.height(2.dp))
                                TextButton(
                                    onClick = { vm.applyTip(t) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp, vertical = 0.dp
                                    )
                                ) {
                                    Text(
                                        "✓ Zastosuj do planu",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (pendingStagnation.isNotEmpty()) {
                        if (pendingPRs.isNotEmpty() || pendingTips.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.stagnation_dialog_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        pendingStagnation.forEach { s ->
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
                Row {
                    val applicable = pendingTips.filter {
                        it.kind != pl.filebit.gymtracker.data.repository.ProgressionKind.NO_CHANGE
                    }
                    if (applicable.size >= 2) {
                        TextButton(onClick = { vm.applyAllTips() }) {
                            Text(
                                "✓ Zastosuj wszystkie",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(onClick = {
                        vm.consumePendingPRs()
                        vm.consumePendingTips()
                        vm.consumePendingStagnation()
                    }) { Text(stringResource(R.string.pr_dialog_ok)) }
                }
            }
        )
    }

    // Post-workout feedback sheet — pojawia się po PR/Tips/Stagnation dialogach
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

    // Dialog z opinią AI o aktualnej sugestii
    val opinion = aiOpinion
    var aiFollowUp by remember { mutableStateOf("") }
    if (opinion !is AiOpinionState.Idle) {
        AlertDialog(
            onDismissRequest = {
                if (opinion !is AiOpinionState.Loading) {
                    aiFollowUp = ""
                    vm.dismissAiOpinion()
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = pl.filebit.gymtracker.ui.theme.AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Drugie zdanie trenera AI")
                }
            },
            text = {
                when (opinion) {
                    is AiOpinionState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = pl.filebit.gymtracker.ui.theme.AccentOrange
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("AI analizuje historię ćwiczenia…")
                        }
                    }
                    is AiOpinionState.Result -> {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.verticalScroll(scroll)) {
                            Text(
                                opinion.opinion.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (opinion.opinion.hasActionableSuggestion) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.15f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.45f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            vm.applyAiSuggestion(
                                                weightKg = opinion.opinion.parsedKg!!,
                                                reps = opinion.opinion.parsedReps!!
                                            )
                                            aiFollowUp = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val kgFmt = opinion.opinion.parsedKg!!.let {
                                        if (it == it.toLong().toDouble()) it.toInt().toString()
                                        else "%.1f".format(it)
                                    }
                                    Text(
                                        "✨ Zastosuj sugestię: $kgFmt kg × ${opinion.opinion.parsedReps}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = pl.filebit.gymtracker.ui.theme.AccentOrange
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = pl.filebit.gymtracker.ui.theme.DarkOutlineSoft)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = aiFollowUp,
                                onValueChange = { aiFollowUp = it },
                                label = {
                                    Text(
                                        "Drążę dalej (opcjonalnie)",
                                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "np. źle dziś śpię / boli mnie bark",
                                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 2
                            )
                        }
                    }
                    is AiOpinionState.Error -> Text(
                        opinion.message,
                        color = pl.filebit.gymtracker.ui.theme.ErrorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AiOpinionState.Idle -> {}
                }
            },
            confirmButton = {
                if (opinion is AiOpinionState.Result && aiFollowUp.isNotBlank()) {
                    TextButton(onClick = {
                        val msg = aiFollowUp
                        aiFollowUp = ""
                        vm.askAiOpinion(followUpMessage = msg)
                    }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = pl.filebit.gymtracker.ui.theme.AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Zapytaj ponownie", color = pl.filebit.gymtracker.ui.theme.AccentOrange)
                    }
                } else {
                    TextButton(onClick = {
                        aiFollowUp = ""
                        vm.dismissAiOpinion()
                    }) { Text("OK") }
                }
            },
            dismissButton = if (opinion is AiOpinionState.Result && aiFollowUp.isNotBlank()) {
                {
                    TextButton(onClick = {
                        aiFollowUp = ""
                        vm.dismissAiOpinion()
                    }) { Text("Zamknij", color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant) }
                }
            } else null
        )
    }
}

@Composable
private fun CoachActiveContent(
    state: CoachUiState,
    onConfirmTap: () -> Unit,
    onSkip: () -> Unit,
    onAskAiOpinion: () -> Unit
) {
    val current = state.currentSet ?: return
    val exercise = state.currentExercise ?: return
    val progress = if (state.totalSets > 0) state.completedSets.toFloat() / state.totalSets else 0f

    // v1.25.4: bottom sheet "Jak wykonać" — GIF + technika + mięśnie + sprzęt
    var showExerciseInfo by remember(exercise.id) { mutableStateOf(false) }
    if (showExerciseInfo) {
        pl.filebit.gymtracker.ui.exercises.ExerciseInfoBottomSheet(
            exercise = exercise,
            onDismiss = { showExerciseInfo = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ──── Postęp treningu — na samej górze (najważniejszy kontekst) ────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ĆWICZENIE ${state.currentExerciseIndex + 1} / ${state.totalExercises}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${state.completedSets} / ${state.totalSets} setów · ${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // ──── Nazwa ćwiczenia + ikona "Jak wykonać?" (v1.25.4) ────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            // Ikona widoczna tylko gdy ćwiczenie ma GIF lub instrukcje w bazie
            if (!exercise.gifUrl.isNullOrBlank() ||
                !exercise.instructionsPlJson.isNullOrBlank() ||
                !exercise.instructionsEnJson.isNullOrBlank()) {
                IconButton(onClick = { showExerciseInfo = true }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Jak wykonać",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // ──── Etykieta serii ────
        Text(
            "SERIA ${state.currentSetIndexInExercise + 1} / ${state.totalSetsInCurrentExercise}",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.weight(1f))

        // ──── Wielki blok: waga × powt. ────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.10f),
                    RoundedCornerShape(20.dp)
                )
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ikona AI w rogu (drugie zdanie)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .background(
                        pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.20f),
                        CircleShape
                    )
                    .clickable { onAskAiOpinion() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Zapytaj AI",
                    tint = pl.filebit.gymtracker.ui.theme.AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (current.weightKg > 0) formatWeight(current.weightKg) else "—",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pl.filebit.gymtracker.ui.theme.AccentOrange,
                        letterSpacing = (-2).sp
                    )
                    Text(
                        " kg",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "× ${current.reps} powt.",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ──── Subtitle: ostatnio (mały, szary) ────
        val lastSummary = state.previousSessionSummaryForCurrent
        val lastFallback = state.lastSetForCurrent?.let { ls ->
            "${formatWeight(ls.weightKg)} kg × ${ls.reps}"
        }
        val lastText = lastSummary ?: lastFallback
        if (lastText != null) {
            Text(
                "🕐 Ostatnio: $lastText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        // ──── Przycisk Wykonana ────
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Pomiń (tekstowy podlinkowany)
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.coach_skip_button),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
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
private fun ConfirmRepsDialog(
    plannedReps: Int,
    plannedWeightKg: Double,
    setNumber: Int,
    totalSets: Int,
    onConfirm: (Int, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var actualReps by remember { mutableStateOf(plannedReps) }
    var rpe by remember { mutableStateOf(0) } // 0 = nie ustawione

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header z kontekstem (numer setu + waga)
                Text(
                    "Seria $setNumber/$totalSets · ${pl.filebit.gymtracker.util.formatWeight(plannedWeightKg)} kg",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.coach_confirm_title, plannedReps),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.coach_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                // ──── REPS ────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.18f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$actualReps",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = pl.filebit.gymtracker.ui.theme.AccentOrange
                    )
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = actualReps.toFloat(),
                    onValueChange = { actualReps = it.roundToInt() },
                    valueRange = 0f..30f,
                    steps = 29
                )
                Text(
                    "Powtórzenia (0-30)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // ──── RPE ────
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trudność (RPE)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    pl.filebit.gymtracker.ui.glossary.InfoIcon(glossaryKey = "RPE")
                    Text(
                        if (rpe == 0) "—" else "$rpe/10",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (rpe == 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = rpe.toFloat(),
                    onValueChange = { rpe = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9
                )
                Text(
                    rpeHint(rpe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Opcjonalne — 0 = pomiń. Pomaga aplikacji sugerować lepsze ciężary.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(actualReps, if (rpe == 0) null else rpe)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.coach_confirm_button, actualReps))
                    }
                }
            }
        }
    }
}

/**
 * Krótka wskazówka opisowa dla danej wartości RPE.
 */
private fun rpeHint(rpe: Int): String = when (rpe) {
    0 -> "Pomiń ocenę trudności"
    1, 2, 3 -> "Bardzo lekko — rozgrzewka"
    4, 5 -> "Lekko — wciąż dużo w zapasie"
    6 -> "Średnio — 4 powt. w zapasie"
    7 -> "Trudno — 3 powt. w zapasie"
    8 -> "Bardzo trudno — 2 powt. w zapasie"
    9 -> "Prawie max — 1 powt. w zapasie"
    10 -> "Padłem — bez zapasu, do upadku"
    else -> ""
}
