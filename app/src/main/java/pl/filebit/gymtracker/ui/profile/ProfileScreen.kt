package pl.filebit.gymtracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.WeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenBackup: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenOneRm: () -> Unit,
    onOpenPlateCalc: () -> Unit,
    onOpenBody: () -> Unit,
    onOpenMuscles: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenStrength: () -> Unit,
    onOpenAiTrainer: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenGoals: () -> Unit,
    vm: ProfileViewModel = hiltViewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var draft by remember(profile) { mutableStateOf(profile) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.profile_saved)

    LaunchedEffect(profile) { draft = profile }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_profile)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = stringResource(R.string.profile_goal)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(TrainingGoal.entries.toList()) { g ->
                            FilterChip(
                                selected = draft.goal == g,
                                onClick = { draft = draft.copy(goal = g) },
                                label = { Text(g.label()) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_experience)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(ExperienceLevel.entries.toList()) { e ->
                            FilterChip(
                                selected = draft.experience == e,
                                onClick = { draft = draft.copy(experience = e) },
                                label = { Text(e.label()) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_unit)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(WeightUnit.entries.toList()) { u ->
                            FilterChip(
                                selected = draft.preferredUnit == u,
                                onClick = { draft = draft.copy(preferredUnit = u) },
                                label = { Text(u.name) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_weight_goal)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(WeightGoalType.entries.toList()) { g ->
                            FilterChip(
                                selected = draft.weightGoalType == g,
                                onClick = { draft = draft.copy(weightGoalType = g) },
                                label = { Text(g.label()) }
                            )
                        }
                    }
                    if (draft.weightGoalType != WeightGoalType.NONE) {
                        Spacer(Modifier.height(12.dp))
                        TargetWeightField(
                            value = draft.targetWeightKg,
                            onChange = { draft = draft.copy(targetWeightKg = it) }
                        )
                    }
                }
            }

            item {
                NumberFieldCard(
                    label = stringResource(R.string.profile_days_per_week),
                    value = draft.daysPerWeek,
                    range = 1..7,
                    onChange = { draft = draft.copy(daysPerWeek = it) }
                )
            }

            item {
                NumberFieldCard(
                    label = stringResource(R.string.profile_session_minutes),
                    value = draft.sessionMinutes,
                    range = 15..240,
                    onChange = { draft = draft.copy(sessionMinutes = it) }
                )
            }

            item {
                NumberFieldCard(
                    label = stringResource(R.string.profile_default_rest),
                    value = draft.defaultRestSeconds,
                    range = 15..600,
                    onChange = { draft = draft.copy(defaultRestSeconds = it) }
                )
            }

            item {
                SectionCard(title = stringResource(R.string.profile_flash_section)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Switch(
                            checked = draft.flashOnTimerEnd,
                            onCheckedChange = { draft = draft.copy(flashOnTimerEnd = it) }
                        )
                        Spacer(Modifier.padding(start = 12.dp))
                        Column {
                            Text(
                                stringResource(R.string.profile_flash_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.profile_flash_explain),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_unfinished_section)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Switch(
                            checked = draft.unfinishedWorkoutNotifyEnabled,
                            onCheckedChange = { draft = draft.copy(unfinishedWorkoutNotifyEnabled = it) }
                        )
                        Spacer(Modifier.padding(start = 12.dp))
                        Column {
                            Text(
                                stringResource(R.string.profile_unfinished_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.profile_unfinished_explain),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (draft.unfinishedWorkoutNotifyEnabled) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draft.unfinishedWorkoutNotifyHours.toString(),
                            onValueChange = { v ->
                                v.filter { it.isDigit() }.toIntOrNull()?.let { n ->
                                    if (n in 1..12) draft = draft.copy(unfinishedWorkoutNotifyHours = n)
                                }
                            },
                            label = { Text(stringResource(R.string.profile_unfinished_hours)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_advanced_section)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Switch(
                            checked = draft.showAdvancedSetFields,
                            onCheckedChange = { draft = draft.copy(showAdvancedSetFields = it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.padding(start = 12.dp)
                        )
                        Column {
                            Text(
                                stringResource(R.string.profile_advanced_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.profile_advanced_explain),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.save(draft) {
                            scope.launch { snackbar.showSnackbar(savedMsg) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.profile_save))
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenStats,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QueryStats, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.stats_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenOneRm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.onerm_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenPlateCalc,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.plate_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenBody,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MonitorWeight, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.body_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenMuscles,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessibilityNew, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.muscles_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenPhotos,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.photos_section_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenStrength,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.strength_section_title)}")
                }
            }
            item {
                Button(
                    onClick = onOpenGoals,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.goals_section_title)}")
                }
            }
            item {
                Button(
                    onClick = onOpenAiTrainer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.ai_trainer_section_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.ai_settings_section_title)}")
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenBackup,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("  ${stringResource(R.string.backup_section)}")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun NumberFieldCard(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() }
                    text.toIntOrNull()?.let { n ->
                        if (n in range) onChange(n)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TargetWeightField(
    value: Double?,
    onChange: (Double?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.let { "%.1f".format(it).replace(',', '.') } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            val parsed = v.replace(',', '.').toDoubleOrNull()
            onChange(parsed)
        },
        label = { Text(stringResource(R.string.profile_target_weight)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WeightGoalType.label(): String = when (this) {
    WeightGoalType.NONE -> stringResource(R.string.weight_goal_none)
    WeightGoalType.CUT -> stringResource(R.string.weight_goal_cut)
    WeightGoalType.BULK -> stringResource(R.string.weight_goal_bulk)
    WeightGoalType.MAINTAIN -> stringResource(R.string.weight_goal_maintain)
}

private fun TrainingGoal.label(): String = when (this) {
    TrainingGoal.STRENGTH -> "Siła"
    TrainingGoal.HYPERTROPHY -> "Masa"
    TrainingGoal.MIX -> "Siła + masa"
    TrainingGoal.GENERAL_FITNESS -> "Sprawność"
    TrainingGoal.CARDIO_LIFTING -> "Cardio + siłka"
}

private fun ExperienceLevel.label(): String = when (this) {
    ExperienceLevel.BEGINNER -> "Początkujący"
    ExperienceLevel.INTERMEDIATE -> "Średnio-zaawansowany"
    ExperienceLevel.ADVANCED -> "Zaawansowany"
}
