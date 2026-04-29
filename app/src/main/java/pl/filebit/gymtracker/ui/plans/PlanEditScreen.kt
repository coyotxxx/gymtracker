package pl.filebit.gymtracker.ui.plans

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.plan_new)
                        else state.name.ifBlank { stringResource(R.string.plan_new) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = { vm.save(onSaved) },
                        enabled = state.name.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            item { SectionHeader(stringResource(R.string.plan_days)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayChip(1, R.string.day_mon_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(2, R.string.day_tue_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(3, R.string.day_wed_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(4, R.string.day_thu_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(5, R.string.day_fri_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(6, R.string.day_sat_short, state.daysOfWeek, vm::toggleDay)
                    DayChip(7, R.string.day_sun_short, state.daysOfWeek, vm::toggleDay)
                }
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
            item { SectionHeader(stringResource(R.string.plan_exercises_header)) }

            items(state.exercises, key = { it.planEx.id }) { item ->
                PlanExerciseCard(
                    item = item,
                    onSets = { v -> vm.updatePlanExercise(item.planEx.id, sets = v) },
                    onReps = { v -> vm.updatePlanExercise(item.planEx.id, reps = v) },
                    onWeight = { v -> vm.updatePlanExercise(item.planEx.id, weightKg = v, clearWeight = v == null) },
                    onRest = { v -> vm.updatePlanExercise(item.planEx.id, restSeconds = v, clearRest = v == null) },
                    onRemove = { vm.removeExercise(item.planEx.id) }
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
                    Text(stringResource(R.string.plan_add_exercise))
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
private fun DayChip(
    day: Int,
    labelRes: Int,
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    Box(modifier = Modifier.padding(end = 4.dp)) {
        FilterChip(
            selected = selected.contains(day),
            onClick = { onToggle(day) },
            label = { Text(stringResource(labelRes)) }
        )
    }
}

@Composable
private fun PlanExerciseCard(
    item: PlanExerciseWithDetail,
    onSets: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Double?) -> Unit,
    onRest: (Int?) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.exercise?.name ?: "(?)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            var setsText by remember(item.planEx.id) {
                mutableStateOf(item.planEx.plannedSets.toString())
            }
            var repsText by remember(item.planEx.id) {
                mutableStateOf(item.planEx.plannedReps.toString())
            }
            var weightText by remember(item.planEx.id) {
                mutableStateOf(item.planEx.plannedWeightKg?.toString() ?: "")
            }
            var restText by remember(item.planEx.id) {
                mutableStateOf(item.planEx.restSeconds?.toString() ?: "")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallNumberField(
                    value = setsText,
                    label = stringResource(R.string.plan_planned_sets),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        setsText = it
                        it.toIntOrNull()?.let(onSets)
                    }
                )
                SmallNumberField(
                    value = repsText,
                    label = stringResource(R.string.plan_planned_reps),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        repsText = it
                        it.toIntOrNull()?.let(onReps)
                    }
                )
                SmallNumberField(
                    value = weightText,
                    label = stringResource(R.string.plan_planned_weight),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        weightText = it
                        if (it.isBlank()) onWeight(null)
                        else it.replace(',', '.').toDoubleOrNull()?.let { v -> onWeight(v) }
                    }
                )
                SmallNumberField(
                    value = restText,
                    label = stringResource(R.string.plan_rest),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        restText = it
                        if (it.isBlank()) onRest(null)
                        else it.toIntOrNull()?.let { v -> onRest(v) }
                    }
                )
            }
        }
    }
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
