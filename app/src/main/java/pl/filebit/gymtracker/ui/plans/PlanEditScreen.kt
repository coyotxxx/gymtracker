package pl.filebit.gymtracker.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton
import pl.filebit.gymtracker.ui.theme.LabelUp
import pl.filebit.gymtracker.data.entity.MetricType
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.util.filterWeightInput
import pl.filebit.gymtracker.util.summarizeSets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    pickedExerciseId: Long? = null,
    onConsumePickedExerciseId: () -> Unit = {},
    vm: PlanEditViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val auditState by vm.auditState.collectAsStateWithLifecycle()
    val improvementState by vm.improvementState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRpeHelp by remember { mutableStateOf(false) }
    var dayActionFor by remember { mutableStateOf<Int?>(null) }
    var followUpQuestion by remember { mutableStateOf("") }

    LaunchedEffect(pickedExerciseId) {
        pickedExerciseId?.let { id ->
            vm.addExercise(id)
            onConsumePickedExerciseId()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = if (state.isNew) stringResource(R.string.plan_new)
                    else state.name.ifBlank { stringResource(R.string.plan_new) },
                    onBack = onBack,
                    actions = {
                        if (!state.isNew) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = DarkOnSurface)
                            }
                        }
                        IconButton(
                            onClick = { vm.save(onSaved) },
                            enabled = state.name.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DarkOnSurface)
                        }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text(stringResource(R.string.plan_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = vm::setNotes,
                    label = { Text(stringResource(R.string.plan_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item { LabelUp(stringResource(R.string.plan_day_tab_header)) }
            item {
                DayTabRow(
                    selected = state.selectedDay,
                    daysWithExercises = state.exercises.map { it.planEx.dayOfWeek }.toSet(),
                    onSelect = { day -> dayActionFor = day }
                )
            }
            item {
                val dayName = stringResource(dayLongRes(state.selectedDay))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabelUp("${stringResource(R.string.plan_exercises_header)} · $dayName")
                    }
                    if (state.showAdvancedFields) {
                        Row(
                            modifier = Modifier
                                .background(
                                    AccentOrange.copy(alpha = 0.10f),
                                    RoundedCornerShape(999.dp)
                                )
                                .border(
                                    1.dp,
                                    AccentOrange.copy(alpha = 0.35f),
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { showRpeHelp = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Co to RPE?",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.6.sp
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
            }

            val visibleExercises = state.exercisesForSelectedDay
            if (visibleExercises.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.plan_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(visibleExercises, key = { _, it -> it.planEx.id }) { idx, item ->
                // Oblicz label A1/A2 dla superserii
                val supersetLabel = computeSupersetLabel(visibleExercises, idx)
                val isLinkedToPrev = idx > 0 &&
                    visibleExercises[idx - 1].planEx.supersetGroup == item.planEx.supersetGroup &&
                    item.planEx.supersetGroup != null
                PlanExerciseCard(
                    position = idx + 1,
                    item = item,
                    previousSession = item.exercise?.id?.let { state.previousSessions[it] },
                    canMoveUp = idx > 0,
                    canMoveDown = idx < visibleExercises.size - 1,
                    canSuperset = idx > 0,
                    isLinkedToPrev = isLinkedToPrev,
                    supersetLabel = supersetLabel,
                    onMoveUp = { vm.moveExerciseUp(item.planEx.id) },
                    onMoveDown = { vm.moveExerciseDown(item.planEx.id) },
                    onToggleSuperset = { vm.toggleSupersetWithPrev(item.planEx.id) },
                    onUpdateSet = { setId, reps, weight, rest, clearWeight, clearRest ->
                        vm.updatePlanSet(
                            planExerciseId = item.planEx.id,
                            setId = setId,
                            reps = reps,
                            weightKg = weight,
                            restSeconds = rest,
                            clearWeight = clearWeight,
                            clearRest = clearRest
                        )
                    },
                    onUpdateSetCardio = { setId, durationSec, distanceM, clearDuration, clearDistance ->
                        vm.updatePlanSet(
                            planExerciseId = item.planEx.id,
                            setId = setId,
                            durationSec = durationSec,
                            distanceM = distanceM,
                            clearDuration = clearDuration,
                            clearDistance = clearDistance
                        )
                    },
                    onUpdateSetAdvanced = { setId, rpe, rir, tempo, clearRpe, clearRir, clearTempo ->
                        vm.updatePlanSetAdvanced(
                            planExerciseId = item.planEx.id,
                            setId = setId,
                            rpe = rpe,
                            rir = rir,
                            tempo = tempo,
                            clearRpe = clearRpe,
                            clearRir = clearRir,
                            clearTempo = clearTempo
                        )
                    },
                    onAddSet = { vm.addSetToExercise(item.planEx.id) },
                    onRemoveSet = { setId -> vm.removeSetFromExercise(item.planEx.id, setId) },
                    onRemoveExercise = { vm.removeExercise(item.planEx.id) }
                )
            }
            item {
                GymPrimaryButton(
                    onClick = onAddExercise,
                    text = stringResource(R.string.plan_add_exercise_to_day),
                    leadingIcon = Icons.Default.Add
                )
            }

            // === AI audyt planu (Opcja C) ===
            if (!state.isNew && state.exercises.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .border(1.dp, AccentOrange.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                            .clickable(enabled = auditState !is PlanAuditState.Loading) {
                                vm.runPlanAudit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (auditState is PlanAuditState.Loading) "AI analizuje plan…"
                                else "🤖 Audyt planu AI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
            }
            if (visibleExercises.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .border(1.dp, ErrorRed.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                            .clickable { vm.clearDay(state.selectedDay) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Usuń ten dzień z planu",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.plan_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.delete(onDeleted)
                }) { Text(stringResource(R.string.plan_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showRpeHelp) {
        AlertDialog(
            onDismissRequest = { showRpeHelp = false },
            title = { Text("Skala RPE") },
            text = {
                Column {
                    Text(
                        "Subiektywna ocena ile siły dałeś z siebie po skończonej serii. Po wypełnieniu kilku serii system sugeruje wagę i powtórzenia na następny trening.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    RpeRow("RPE 6", "Lekko — mogłem jeszcze 4+ powtórzeń")
                    RpeRow("RPE 7", "Mogłem jeszcze 3 powt.")
                    RpeRow("RPE 8", "Mogłem jeszcze 2 powt. (sweet spot)")
                    RpeRow("RPE 9", "Mogłem jeszcze 1 powt.")
                    RpeRow("RPE 10", "Do upadku — nie wycisnąłbym ani jednej więcej")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Wypełnianie RPE jest opcjonalne. Bez RPE system progresuje na ślepo (+waga z każdym treningiem).",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRpeHelp = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Dialog z wynikiem audytu AI
    val audit = auditState
    if (audit !is PlanAuditState.Idle && improvementState !is PlanImprovementState.Preview) {
        AlertDialog(
            onDismissRequest = {
                if (improvementState !is PlanImprovementState.Loading) vm.dismissAudit()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Audyt planu AI")
                }
            },
            text = {
                when (audit) {
                    is PlanAuditState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentOrange
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("AI analizuje plan…")
                        }
                    }
                    is PlanAuditState.Result -> {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.verticalScroll(scroll)) {
                            audit.text.split("\n").forEach { line ->
                                AuditMarkdownLine(line)
                            }
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = DarkOutlineSoft)
                            Spacer(Modifier.height(12.dp))
                            // Pole follow-up — opcjonalne doprecyzowanie
                            OutlinedTextField(
                                value = followUpQuestion,
                                onValueChange = { followUpQuestion = it },
                                label = { Text("Doprecyzuj (opcjonalnie)", color = DarkOnSurfaceVariant) },
                                placeholder = {
                                    Text(
                                        "np. nie chcę dipów / dodaj dzień nóg",
                                        color = DarkOnSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 3,
                                enabled = improvementState !is PlanImprovementState.Loading
                            )
                            if (improvementState is PlanImprovementState.Error) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    (improvementState as PlanImprovementState.Error).message,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (improvementState is PlanImprovementState.Loading) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = AccentOrange
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "AI generuje poprawiony plan…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DarkOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is PlanAuditState.Error -> Text(
                        audit.message,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PlanAuditState.Idle -> {}
                }
            },
            confirmButton = {
                if (audit is PlanAuditState.Result) {
                    TextButton(
                        onClick = {
                            vm.requestImprovement(followUpQuestion.takeIf { it.isNotBlank() })
                        },
                        enabled = improvementState !is PlanImprovementState.Loading
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Wygeneruj poprawiony plan", color = AccentOrange)
                    }
                } else {
                    TextButton(onClick = { vm.dismissAudit() }) { Text("OK") }
                }
            },
            dismissButton = if (audit is PlanAuditState.Result) {
                {
                    TextButton(onClick = {
                        followUpQuestion = ""
                        vm.dismissAudit()
                    }) { Text("Zamknij", color = DarkOnSurfaceVariant) }
                }
            } else null
        )
    }

    // Preview poprawionego planu
    val improvement = improvementState
    if (improvement is PlanImprovementState.Preview) {
        val currentByDay = state.exercises
            .groupBy { it.planEx.dayOfWeek }
            .mapValues { (_, list) ->
                list.sortedBy { it.planEx.orderIndex }
                    .mapNotNull { it.exercise?.name }
            }
        PlanImprovementSheet(
            proposal = improvement.proposal,
            currentExercisesByDay = currentByDay,
            isApplying = false,
            onDismiss = {
                vm.dismissImprovement()
                followUpQuestion = ""
            },
            onReplace = {
                vm.applyImprovement(asCopy = false) {
                    followUpQuestion = ""
                }
            },
            onSaveAsCopy = {
                vm.applyImprovement(asCopy = true) { newId ->
                    followUpQuestion = ""
                    if (newId != null) onSaved()
                }
            }
        )
    } else if (improvement is PlanImprovementState.Applying) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Zapisuję…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AccentOrange
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Stosuję zmiany w planie…")
                }
            },
            confirmButton = {}
        )
    }

    dayActionFor?.let { dayPicked ->
        val daysWithEx = state.exercises.map { it.planEx.dayOfWeek }.toSet()
        val countInDay = state.exercises.count { it.planEx.dayOfWeek == dayPicked }
        PlanDayActionsSheet(
            day = dayPicked,
            exercisesCount = countInDay,
            daysWithExercises = daysWithEx,
            onDismiss = { dayActionFor = null },
            onEditDay = { d -> vm.setSelectedDay(d) },
            onMoveDay = { from, to -> vm.moveDayExercises(from, to) },
            onSwapDays = { a, b -> vm.swapDays(a, b) },
            onCopyDay = { from, to -> vm.copyDayExercises(from, to) },
            onClearDay = { d -> vm.clearDay(d) }
        )
    }
}

@Composable
private fun AuditMarkdownLine(line: String) {
    when {
        line.startsWith("## ") -> {
            Spacer(Modifier.height(8.dp))
            Text(
                line.removePrefix("## "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = AccentOrange
            )
        }
        line.startsWith("### ") -> {
            Text(
                line.removePrefix("### "),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = DarkOnSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        line.startsWith("- ") || line.startsWith("• ") -> {
            Text(
                "• " + line.removePrefix("- ").removePrefix("• "),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = DarkOnSurface,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        line.isBlank() -> Spacer(Modifier.height(4.dp))
        else -> {
            val cleaned = line.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            Text(
                cleaned,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = if (line.contains("**")) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = DarkOnSurface
            )
        }
    }
}

@Composable
private fun RpeRow(label: String, description: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = AccentOrange,
            modifier = Modifier.width(60.dp)
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )
    }
}

private fun dayLongRes(day: Int): Int = when (day) {
    1 -> R.string.day_mon_long
    2 -> R.string.day_tue_long
    3 -> R.string.day_wed_long
    4 -> R.string.day_thu_long
    5 -> R.string.day_fri_long
    6 -> R.string.day_sat_long
    else -> R.string.day_sun_long
}

@Composable
private fun DayTabRow(
    selected: Int,
    daysWithExercises: Set<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            1 to R.string.day_mon_short,
            2 to R.string.day_tue_short,
            3 to R.string.day_wed_short,
            4 to R.string.day_thu_short,
            5 to R.string.day_fri_short,
            6 to R.string.day_sat_short,
            7 to R.string.day_sun_short
        ).forEach { (day, labelRes) ->
            DayPillChip(
                label = stringResource(labelRes),
                isActive = day == selected,
                hasExercises = day in daysWithExercises,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayPillChip(
    label: String,
    isActive: Boolean,
    hasExercises: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3 stany:
    // - Active: ciemne tło, żółta obwódka, żółty tekst (aktualnie przeglądany dzień)
    // - Has exercises: ciemnożółte wypełnienie, żółty tekst
    // - Empty: ciemne tło bez obwódki, szary tekst
    val bg = when {
        isActive -> AccentOrange.copy(alpha = 0.10f)
        hasExercises -> AccentOrange.copy(alpha = 0.22f)
        else -> DarkSurface
    }
    val borderColor = if (isActive) AccentOrange else androidx.compose.ui.graphics.Color.Transparent
    val textColor = if (isActive || hasExercises) AccentOrange else DarkOnSurfaceVariant
    Box(
        modifier = modifier
            .height(48.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .border(if (isActive) 2.dp else 0.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = textColor
        )
    }
}


@Composable
private fun PlanExerciseCard(
    position: Int,
    item: PlanExerciseWithDetail,
    previousSession: pl.filebit.gymtracker.data.repository.PreviousSession?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canSuperset: Boolean,
    isLinkedToPrev: Boolean,
    supersetLabel: String?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleSuperset: () -> Unit,
    onUpdateSet: (setId: Long, reps: Int?, weight: Double?, rest: Int?, clearWeight: Boolean, clearRest: Boolean) -> Unit,
    onUpdateSetCardio: (setId: Long, durationSec: Int?, distanceM: Double?, clearDuration: Boolean, clearDistance: Boolean) -> Unit,
    onUpdateSetAdvanced: (setId: Long, rpe: Int?, rir: Int?, tempo: String?, clearRpe: Boolean, clearRir: Boolean, clearTempo: Boolean) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    onRemoveExercise: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (supersetLabel != null) AccentOrange.copy(alpha = 0.10f)
            else DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (supersetLabel != null) AccentOrange.copy(alpha = 0.40f) else DarkOutlineSoft
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Numer pozycji ćwiczenia (lub label A1/A2 dla superserii)
                Text(
                    text = supersetLabel ?: "$position.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    ),
                    color = AccentOrange,
                    modifier = Modifier.width(40.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.exercise?.name ?: "(?)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = DarkOnSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summarizeSets(item.sets),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DarkOnSurfaceVariant,
                        maxLines = 1
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = DarkOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isLinkedToPrev) "Rozłącz superserię" else "Połącz superserię") },
                            leadingIcon = {
                                Icon(
                                    if (isLinkedToPrev) Icons.Default.LinkOff else Icons.Default.Link,
                                    contentDescription = null
                                )
                            },
                            enabled = canSuperset,
                            onClick = {
                                menuExpanded = false
                                onToggleSuperset()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Przenieś w górę") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            enabled = canMoveUp,
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Przenieś w dół") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            enabled = canMoveDown,
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Usuń ćwiczenie") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRemoveExercise()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarkOutlineSoft)
            Spacer(Modifier.height(10.dp))

            // Header kolumn — etykiety dopasowane do typu metryki ćwiczenia.
            // v1.29.10: cardio (bieżnia, rower) → Czas/Dystans zamiast Powt./Waga.
            val metric = item.exercise?.metricType
                ?: pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS
            val col1Label = when (metric) {
                pl.filebit.gymtracker.data.entity.MetricType.DURATION,
                pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION,
                pl.filebit.gymtracker.data.entity.MetricType.DURATION_WEIGHT -> "Czas"
                else -> "Powt."
            }
            val col2Label = when (metric) {
                pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS,
                pl.filebit.gymtracker.data.entity.MetricType.DURATION_WEIGHT -> "Waga"
                pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION -> "Dystans"
                else -> "—"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(28.dp))
                ColumnHeader(col1Label, Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                ColumnHeader(col2Label, Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                ColumnHeader("Odp.", Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                ColumnHeader("RPE", Modifier.weight(0.7f))
                Spacer(Modifier.width(36.dp))
            }
            Spacer(Modifier.height(4.dp))

            // Mapa setNumber → previous WorkoutSet (working only, bez warmup).
            // Pomijamy warmup, więc setNumber 2,3,4,5 → renumerujemy na 1,2,3,4.
            val previousWorking = previousSession?.sets
                ?.filter { it.setType != pl.filebit.gymtracker.data.entity.SetType.WARMUP }
                ?.sortedBy { it.setNumber }
                .orEmpty()

            item.sets.forEachIndexed { rowIdx, setSpec ->
                SetEditRow(
                    setSpec = setSpec,
                    metricType = metric,
                    previousWorkingSet = previousWorking.getOrNull(rowIdx),
                    onReps = { v -> onUpdateSet(setSpec.id, v, null, null, false, false) },
                    onWeight = { v ->
                        onUpdateSet(setSpec.id, null, v, null, v == null, false)
                    },
                    onRest = { v ->
                        onUpdateSet(setSpec.id, null, null, v, false, v == null)
                    },
                    onRpe = { v ->
                        onUpdateSetAdvanced(setSpec.id, v, null, null, v == null, false, false)
                    },
                    onDuration = { v ->
                        onUpdateSetCardio(setSpec.id, v, null, v == null, false)
                    },
                    onDistance = { v ->
                        onUpdateSetCardio(setSpec.id, null, v, false, v == null)
                    },
                    onDelete = { onRemoveSet(setSpec.id) }
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .border(1.dp, AccentOrange.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onAddSet),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.plan_add_set),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        color = DarkOnSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun MiniNumField(
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = DarkOnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(AccentOrange),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .height(38.dp)
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(
                            color = DarkOnSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun SetEditRow(
    setSpec: pl.filebit.gymtracker.data.entity.PlanExerciseSet,
    metricType: pl.filebit.gymtracker.data.entity.MetricType,
    previousWorkingSet: pl.filebit.gymtracker.data.entity.WorkoutSet?,
    onReps: (Int?) -> Unit,
    onWeight: (Double?) -> Unit,
    onRest: (Int?) -> Unit,
    onRpe: (Int?) -> Unit,
    onDuration: (Int?) -> Unit,
    onDistance: (Double?) -> Unit,
    onDelete: () -> Unit
) {
    // KOLUMNA 1 = czas (dla cardio/izometr.) lub powtórzenia. Dla DISTANCE_DURATION
    // czas wpisuje się w minutach (zapis ×60 s), dla DURATION w sekundach.
    val col1IsDuration = metricType == MetricType.DURATION ||
        metricType == MetricType.DISTANCE_DURATION || metricType == MetricType.DURATION_WEIGHT
    val durationInMinutes = metricType == MetricType.DISTANCE_DURATION
    val col2IsWeight = metricType == MetricType.WEIGHT_REPS ||
        metricType == MetricType.DURATION_WEIGHT
    val col2IsDistance = metricType == MetricType.DISTANCE_DURATION

    var repsText by remember(setSpec.id) { mutableStateOf(setSpec.reps.toString()) }
    var weightText by remember(setSpec.id) { mutableStateOf(setSpec.weightKg?.toString() ?: "") }
    var restText by remember(setSpec.id) { mutableStateOf(setSpec.restSeconds?.toString() ?: "") }
    var rpeText by remember(setSpec.id) { mutableStateOf(setSpec.rpe?.toString() ?: "") }
    var durationText by remember(setSpec.id) {
        val dur = setSpec.durationSec
        mutableStateOf(
            when {
                dur != null -> if (durationInMinutes) (dur / 60).toString() else dur.toString()
                // AI/starsze plany: cardio z wartością wpisaną w polu `reps`
                col1IsDuration && setSpec.reps > 0 -> setSpec.reps.toString()
                else -> ""
            }
        )
    }
    var distanceText by remember(setSpec.id) {
        mutableStateOf(setSpec.distanceM?.let { (it / 1000.0).toString() } ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Numer serii — wycentrowany pionowo na poziomie pól (~38dp)
        Text(
            "${setSpec.setNumber}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = AccentOrange,
            modifier = Modifier.width(28.dp).padding(top = 10.dp),
            textAlign = TextAlign.Center
        )
        // === KOLUMNA 1: powtórzenia LUB czas ===
        if (col1IsDuration) {
            FieldWithHistory(
                modifier = Modifier.weight(1f),
                historyValue = null,
                historyColorHint = HistoryColor.NEUTRAL
            ) {
                MiniNumField(
                    value = durationText,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = {
                        durationText = it.filter { c -> c.isDigit() }
                        val n = durationText.toIntOrNull()
                        onDuration(n?.let { v -> if (durationInMinutes) v * 60 else v })
                    }
                )
            }
        } else {
            FieldWithHistory(
                modifier = Modifier.weight(1f),
                historyValue = previousWorkingSet?.reps?.toString(),
                historyColorHint = compareIntForColor(setSpec.reps, previousWorkingSet?.reps)
            ) {
                MiniNumField(
                    value = repsText,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = {
                        repsText = it.filter { c -> c.isDigit() }
                        if (repsText.isBlank()) onReps(null) else repsText.toIntOrNull()?.let(onReps)
                    }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // === KOLUMNA 2: waga / dystans / pusta ===
        when {
            col2IsWeight -> FieldWithHistory(
                modifier = Modifier.weight(1f),
                historyValue = previousWorkingSet?.weightKg?.let { pl.filebit.gymtracker.util.formatWeight(it) },
                historyColorHint = compareDoubleForColor(setSpec.weightKg, previousWorkingSet?.weightKg)
            ) {
                MiniNumField(
                    value = weightText,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = {
                        val filtered = filterWeightInput(it)
                        weightText = filtered
                        if (filtered.isBlank()) onWeight(null)
                        else filtered.replace(',', '.').toDoubleOrNull()?.let(onWeight)
                    }
                )
            }
            col2IsDistance -> FieldWithHistory(
                modifier = Modifier.weight(1f),
                historyValue = null,
                historyColorHint = HistoryColor.NEUTRAL
            ) {
                MiniNumField(
                    value = distanceText,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = {
                        val filtered = filterWeightInput(it)
                        distanceText = filtered
                        if (filtered.isBlank()) onDistance(null)
                        else filtered.replace(',', '.').toDoubleOrNull()?.let { km -> onDistance(km * 1000.0) }
                    }
                )
            }
            else -> Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(4.dp))
        // ODP — bez historii (rest nie jest progresowany)
        FieldWithHistory(
            modifier = Modifier.weight(1f),
            historyValue = null,
            historyColorHint = HistoryColor.NEUTRAL
        ) {
            MiniNumField(
                value = restText,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = {
                    restText = it.filter { c -> c.isDigit() }
                    if (restText.isBlank()) onRest(null) else restText.toIntOrNull()?.let(onRest)
                }
            )
        }
        Spacer(Modifier.width(4.dp))
        // RPE — historia w neutralnym szarym (RPE nie jest "progresowane" liniowo)
        FieldWithHistory(
            modifier = Modifier.weight(0.7f),
            historyValue = previousWorkingSet?.rpe?.takeIf { it > 0 }?.toString(),
            historyColorHint = HistoryColor.NEUTRAL
        ) {
            MiniNumField(
                value = rpeText,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "—",
                onValueChange = {
                    rpeText = it.filter { c -> c.isDigit() }
                    if (rpeText.isBlank()) onRpe(null)
                    else rpeText.toIntOrNull()?.takeIf { v -> v in 1..10 }?.let(onRpe)
                }
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp).padding(top = 4.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DarkOnSurfaceVariant
            )
        }
    }
}

private enum class HistoryColor { PROGRESS, REGRESS, EQUAL, NEUTRAL }

private fun compareIntForColor(plan: Int?, prev: Int?): HistoryColor = when {
    plan == null || prev == null -> HistoryColor.NEUTRAL
    plan > prev -> HistoryColor.PROGRESS
    plan < prev -> HistoryColor.REGRESS
    else -> HistoryColor.EQUAL
}

private fun compareDoubleForColor(plan: Double?, prev: Double?): HistoryColor = when {
    plan == null || prev == null -> HistoryColor.NEUTRAL
    plan > prev -> HistoryColor.PROGRESS
    plan < prev -> HistoryColor.REGRESS
    else -> HistoryColor.EQUAL
}

@Composable
private fun FieldWithHistory(
    modifier: Modifier = Modifier,
    historyValue: String?,
    historyColorHint: HistoryColor,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
        if (historyValue != null) {
            val color = when (historyColorHint) {
                HistoryColor.PROGRESS -> Color(0xFF22C55E) // zielony — plan > ostatnio
                HistoryColor.REGRESS -> pl.filebit.gymtracker.ui.theme.ErrorRed
                HistoryColor.EQUAL -> AccentOrange.copy(alpha = 0.7f)
                HistoryColor.NEUTRAL -> DarkOnSurfaceVariant
            }
            Text(
                text = historyValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = color,
                modifier = Modifier.padding(top = 1.dp)
            )
        } else {
            // Pusty placeholder — utrzymuje wyrównanie wszystkich pól nawet gdy
            // niektóre nie mają historii (np. ODP)
            Spacer(Modifier.height(13.dp))
        }
    }
}

/**
 * Oblicza label A1/A2/B1 dla pozycji w obrębie supersetGroup. Null gdy nie w grupie 2+.
 */
private fun computeSupersetLabel(list: List<PlanExerciseWithDetail>, idx: Int): String? {
    val current = list[idx]
    // Filtruj string "null" (legacy bug — AI parser zwracał "null" zamiast null)
    val group = current.planEx.supersetGroup
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: return null
    val groupMembers = list.filter {
        it.planEx.supersetGroup
            ?.takeIf { v -> v.isNotBlank() && !v.equals("null", ignoreCase = true) } == group
    }
    if (groupMembers.size < 2) return null
    val positionInGroup = groupMembers.indexOfFirst { it.planEx.id == current.planEx.id } + 1
    return "$group$positionInGroup"
}

