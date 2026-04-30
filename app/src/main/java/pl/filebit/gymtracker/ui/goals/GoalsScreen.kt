package pl.filebit.gymtracker.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.GoalUnit
import pl.filebit.gymtracker.data.repository.GoalProgress
import pl.filebit.gymtracker.util.formatDate
import pl.filebit.gymtracker.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    vm: GoalsViewModel = hiltViewModel()
) {
    val progresses by vm.progresses.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        containerColor = pl.filebit.gymtracker.ui.theme.DarkBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = pl.filebit.gymtracker.ui.theme.DarkBg,
                    titleContentColor = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                    navigationIconContentColor = pl.filebit.gymtracker.ui.theme.DarkOnSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = pl.filebit.gymtracker.ui.theme.AccentOrange,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (progresses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.goals_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(progresses, key = { it.goal.id }) { gp ->
                    GoalCard(
                        gp = gp,
                        onDelete = { pendingDelete = gp.goal }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onSave = { g ->
                vm.upsert(g)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(goal.title) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(goal)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun goalEmoji(type: GoalType, achieved: Boolean): String = when {
    achieved -> "🏆"
    type == GoalType.LOSE_WEIGHT -> "📉"
    type == GoalType.GAIN_MASS -> "💪"
    type == GoalType.INCREASE_STRENGTH -> "🏋️"
    type == GoalType.IMPROVE_CARDIO -> "🏃"
    else -> "🎯"
}

@Composable
private fun GoalCard(gp: GoalProgress, onDelete: () -> Unit) {
    val emoji = goalEmoji(gp.goal.type, gp.achieved)
    val accent = pl.filebit.gymtracker.ui.theme.AccentOrange

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Emoji tile po lewej
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .background(
                            pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        gp.goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurface
                    )
                    Text(
                        "Termin: ${formatDate(gp.goal.deadline)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // current vs target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatWeight(gp.currentValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pl.filebit.gymtracker.ui.theme.DarkOnSurface
                )
                Text(
                    "cel: ${formatWeight(gp.goal.targetValue)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))

            // Pasek postępu — żółty
            val progress = (gp.percentDone / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accent,
                trackColor = pl.filebit.gymtracker.ui.theme.DarkSurface3,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(10.dp))

            // Status — emoji + tekst w 1 linii
            val (statusEmoji, statusText, statusColor) = when {
                gp.achieved -> Triple("🏆", stringResource(R.string.goals_status_achieved), accent)
                gp.daysRemaining == 0 && !gp.achieved -> Triple(
                    "⏰",
                    stringResource(R.string.goals_status_overdue),
                    pl.filebit.gymtracker.ui.theme.ErrorRed
                )
                gp.pacePercent >= 110 -> Triple(
                    "🚀",
                    stringResource(R.string.goals_status_ahead, gp.pacePercent),
                    pl.filebit.gymtracker.ui.theme.SuccessGreen
                )
                gp.onTrack -> Triple(
                    "✅",
                    stringResource(R.string.goals_status_on_track, gp.pacePercent),
                    pl.filebit.gymtracker.ui.theme.SuccessGreen
                )
                else -> Triple(
                    "⚠️",
                    stringResource(R.string.goals_status_behind, gp.pacePercent),
                    pl.filebit.gymtracker.ui.theme.ErrorRed
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusEmoji, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit
) {
    var type by remember { mutableStateOf(GoalType.LOSE_WEIGHT) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startValue by remember { mutableStateOf("") }
    var targetValue by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("3") }

    fun parseD(s: String) = s.replace(',', '.').toDoubleOrNull()

    val unit = when (type) {
        GoalType.LOSE_WEIGHT, GoalType.GAIN_MASS, GoalType.INCREASE_STRENGTH -> GoalUnit.KG
        GoalType.IMPROVE_CARDIO -> GoalUnit.KM
        GoalType.CUSTOM -> GoalUnit.CUSTOM
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_add_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.height(440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(stringResource(R.string.goals_pick_type), fontWeight = FontWeight.SemiBold)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalType.entries.forEach { gt ->
                            FilterChip(
                                selected = type == gt,
                                onClick = {
                                    type = gt
                                    if (title.isBlank()) title = gt.defaultTitle()
                                },
                                label = { Text(gt.label()) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.goals_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startValue,
                            onValueChange = { startValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text(stringResource(R.string.goals_start_value)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetValue,
                            onValueChange = { targetValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text(stringResource(R.string.goals_target_value)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = months,
                        onValueChange = { months = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.goals_months_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.goals_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sv = parseD(startValue) ?: return@TextButton
                    val tv = parseD(targetValue) ?: return@TextButton
                    val m = months.toIntOrNull()?.coerceIn(1, 24) ?: 3
                    val now = System.currentTimeMillis()
                    val deadline = now + m * 30L * 24 * 60 * 60 * 1000L
                    onSave(
                        Goal(
                            type = type,
                            title = title.ifBlank { type.defaultTitle() },
                            description = description,
                            unit = unit,
                            startValue = sv,
                            targetValue = tv,
                            startDate = now,
                            deadline = deadline
                        )
                    )
                }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun GoalType.label(): String = when (this) {
    GoalType.LOSE_WEIGHT -> stringResource(R.string.goals_type_lose_weight)
    GoalType.GAIN_MASS -> stringResource(R.string.goals_type_gain_mass)
    GoalType.IMPROVE_CARDIO -> stringResource(R.string.goals_type_cardio)
    GoalType.INCREASE_STRENGTH -> stringResource(R.string.goals_type_strength)
    GoalType.CUSTOM -> stringResource(R.string.goals_type_custom)
}

private fun GoalType.defaultTitle(): String = when (this) {
    GoalType.LOSE_WEIGHT -> "Schudnąć"
    GoalType.GAIN_MASS -> "Nabrać masy"
    GoalType.IMPROVE_CARDIO -> "Poprawić kondycję"
    GoalType.INCREASE_STRENGTH -> "Zwiększyć siłę"
    GoalType.CUSTOM -> "Mój cel"
}

private fun GoalUnit.label(): String = when (this) {
    GoalUnit.KG -> "kg"
    GoalUnit.KM -> "km"
    GoalUnit.MINUTES -> "min"
    GoalUnit.REPS -> "powt."
    GoalUnit.CUSTOM -> ""
}
