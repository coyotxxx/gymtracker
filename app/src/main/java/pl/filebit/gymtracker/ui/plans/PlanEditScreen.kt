package pl.filebit.gymtracker.ui.plans

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader

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
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            item { SectionHeader(stringResource(R.string.plan_day_tab_header)) }
            item {
                DayTabRow(
                    selected = state.selectedDay,
                    daysWithExercises = state.exercises.map { it.planEx.dayOfWeek }.toSet(),
                    onSelect = vm::setSelectedDay
                )
            }
            item { SectionHeader(stringResource(R.string.plan_exercises_header)) }

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
                val isInSuperset = item.planEx.supersetGroup != null
                val isLinkedToPrev = idx > 0 &&
                    visibleExercises[idx - 1].planEx.supersetGroup == item.planEx.supersetGroup &&
                    item.planEx.supersetGroup != null
                PlanExerciseCard(
                    item = item,
                    showAdvanced = state.showAdvancedFields,
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
                OutlinedButton(
                    onClick = onAddExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plan_add_exercise_to_day))
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
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DayTabRow(
    selected: Int,
    daysWithExercises: Set<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            val hasExercises = daysWithExercises.contains(day)
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                label = {
                    Text(
                        if (hasExercises) "● " + stringResource(labelRes)
                        else stringResource(labelRes)
                    )
                }
            )
        }
    }
}

@Composable
private fun PlanExerciseCard(
    item: PlanExerciseWithDetail,
    showAdvanced: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canSuperset: Boolean,
    isLinkedToPrev: Boolean,
    supersetLabel: String?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleSuperset: () -> Unit,
    onUpdateSet: (setId: Long, reps: Int?, weight: Double?, rest: Int?, clearWeight: Boolean, clearRest: Boolean) -> Unit,
    onUpdateSetAdvanced: (setId: Long, rpe: Int?, rir: Int?, tempo: String?, clearRpe: Boolean, clearRir: Boolean, clearTempo: Boolean) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    onRemoveExercise: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (supersetLabel != null)
                pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.10f)
            else pl.filebit.gymtracker.ui.theme.DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (supersetLabel != null) pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.40f)
            else pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (supersetLabel != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                pl.filebit.gymtracker.ui.theme.AccentOrange,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            supersetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                    }
                }
                Text(
                    item.exercise?.name ?: "(?)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onToggleSuperset,
                    enabled = canSuperset,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isLinkedToPrev) Icons.Default.LinkOff else Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isLinkedToPrev) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRemoveExercise) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Seria",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    stringResource(R.string.plan_planned_reps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.plan_planned_weight),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.plan_rest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(40.dp)) // place for delete icon
            }
            Spacer(Modifier.height(4.dp))

            item.sets.forEach { setSpec ->
                SetEditRow(
                    setSpec = setSpec,
                    onReps = { v -> onUpdateSet(setSpec.id, v, null, null, false, false) },
                    onWeight = { v ->
                        onUpdateSet(setSpec.id, null, v, null, v == null, false)
                    },
                    onRest = { v ->
                        onUpdateSet(setSpec.id, null, null, v, false, v == null)
                    },
                    onDelete = { onRemoveSet(setSpec.id) }
                )
                if (showAdvanced) {
                    AdvancedSetRow(
                        setSpec = setSpec,
                        onRpe = { v -> onUpdateSetAdvanced(setSpec.id, v, null, null, v == null, false, false) },
                        onRir = { v -> onUpdateSetAdvanced(setSpec.id, null, v, null, false, v == null, false) },
                        onTempo = { v -> onUpdateSetAdvanced(setSpec.id, null, null, v, false, false, v.isNullOrBlank()) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.plan_add_set))
            }
        }
    }
}

@Composable
private fun SetEditRow(
    setSpec: pl.filebit.gymtracker.data.entity.PlanExerciseSet,
    onReps: (Int?) -> Unit,
    onWeight: (Double?) -> Unit,
    onRest: (Int?) -> Unit,
    onDelete: () -> Unit
) {
    var repsText by remember(setSpec.id) { mutableStateOf(setSpec.reps.toString()) }
    var weightText by remember(setSpec.id) { mutableStateOf(setSpec.weightKg?.toString() ?: "") }
    var restText by remember(setSpec.id) { mutableStateOf(setSpec.restSeconds?.toString() ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${setSpec.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp)
        )
        SetNumberField(
            value = repsText,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
            onValueChange = {
                repsText = it
                if (it.isBlank()) onReps(null) else it.toIntOrNull()?.let(onReps)
            }
        )
        Spacer(Modifier.width(4.dp))
        SetNumberField(
            value = weightText,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
            onValueChange = {
                weightText = it
                if (it.isBlank()) onWeight(null)
                else it.replace(',', '.').toDoubleOrNull()?.let(onWeight)
            }
        )
        Spacer(Modifier.width(4.dp))
        SetNumberField(
            value = restText,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
            onValueChange = {
                restText = it
                if (it.isBlank()) onRest(null) else it.toIntOrNull()?.let(onRest)
            }
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Oblicza label A1/A2/B1 dla pozycji w obrębie supersetGroup. Null gdy nie w grupie 2+.
 */
private fun computeSupersetLabel(list: List<PlanExerciseWithDetail>, idx: Int): String? {
    val current = list[idx]
    val group = current.planEx.supersetGroup ?: return null
    val groupMembers = list.filter { it.planEx.supersetGroup == group }
    if (groupMembers.size < 2) return null
    val positionInGroup = groupMembers.indexOfFirst { it.planEx.id == current.planEx.id } + 1
    return "$group$positionInGroup"
}

@Composable
private fun AdvancedSetRow(
    setSpec: pl.filebit.gymtracker.data.entity.PlanExerciseSet,
    onRpe: (Int?) -> Unit,
    onRir: (Int?) -> Unit,
    onTempo: (String?) -> Unit
) {
    var rpeText by remember(setSpec.id) { mutableStateOf(setSpec.rpe?.toString() ?: "") }
    var rirText by remember(setSpec.id) { mutableStateOf(setSpec.rir?.toString() ?: "") }
    var tempoText by remember(setSpec.id) { mutableStateOf(setSpec.tempo ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 2.dp, end = 40.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = rpeText,
            onValueChange = {
                rpeText = it.filter { c -> c.isDigit() }
                if (rpeText.isBlank()) onRpe(null)
                else rpeText.toIntOrNull()?.let(onRpe)
            },
            label = { Text("RPE", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = rirText,
            onValueChange = {
                rirText = it.filter { c -> c.isDigit() }
                if (rirText.isBlank()) onRir(null)
                else rirText.toIntOrNull()?.let(onRir)
            },
            label = { Text("RIR", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = tempoText,
            onValueChange = {
                tempoText = it
                if (tempoText.isBlank()) onTempo(null) else onTempo(tempoText)
            },
            label = { Text("Tempo", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            placeholder = { Text("3-1-1-0", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun SetNumberField(
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun SmallNumberField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
