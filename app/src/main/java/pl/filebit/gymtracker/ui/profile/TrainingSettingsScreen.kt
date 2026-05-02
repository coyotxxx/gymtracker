package pl.filebit.gymtracker.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.ScreenHeader

/**
 * Pod-ekran ustawień treningu, wydzielony z Profilu (v0.72.0).
 * W Profilu zostają tylko: cel treningowy, doświadczenie, jednostki —
 * to "kim jestem" (identity). Tutaj: parametry techniczne sesji.
 */
@Composable
fun TrainingSettingsScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = hiltViewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var draft by remember(profile) { mutableStateOf(profile) }

    LaunchedEffect(profile) { draft = profile }

    LaunchedEffect(draft) {
        if (draft != profile) {
            vm.save(draft) { /* auto-save */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ScreenHeader(title = "Ustawienia treningu", onBack = onBack) }

            // Cel wagowy + waga docelowa
            item {
                TsSectionCard(
                    title = stringResource(R.string.profile_weight_goal),
                    subtitle = "Określa kierunek bilansu kalorycznego sugerowanego przez AI."
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        androidx.compose.foundation.lazy.items(WeightGoalType.entries.toList()) { g ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = weightGoalLabel(g),
                                selected = draft.weightGoalType == g,
                                onClick = { draft = draft.copy(weightGoalType = g) }
                            )
                        }
                    }
                    if (draft.weightGoalType != WeightGoalType.NONE) {
                        Spacer(Modifier.height(12.dp))
                        TsTargetWeightField(
                            value = draft.targetWeightKg,
                            onChange = { draft = draft.copy(targetWeightKg = it) }
                        )
                    }
                }
            }

            // Dni / tydzień
            item {
                TsNumberFieldCard(
                    label = stringResource(R.string.profile_days_per_week),
                    value = draft.daysPerWeek,
                    range = 1..7,
                    onChange = { draft = draft.copy(daysPerWeek = it) }
                )
            }

            // Czas sesji
            item {
                TsNumberFieldCard(
                    label = stringResource(R.string.profile_session_minutes),
                    value = draft.sessionMinutes,
                    range = 15..240,
                    onChange = { draft = draft.copy(sessionMinutes = it) }
                )
            }

            // Domyślny czas odpoczynku
            item {
                TsNumberFieldCard(
                    label = stringResource(R.string.profile_default_rest),
                    value = draft.defaultRestSeconds,
                    range = 15..600,
                    onChange = { draft = draft.copy(defaultRestSeconds = it) }
                )
            }

            // Zaawansowane pola serii
            item {
                TsToggleSection(
                    title = stringResource(R.string.profile_advanced_section),
                    label = stringResource(R.string.profile_advanced_toggle),
                    explain = stringResource(R.string.profile_advanced_explain),
                    checked = draft.showAdvancedSetFields,
                    onChange = { draft = draft.copy(showAdvancedSetFields = it) }
                )
            }

            // Powiadomienia o niedokończonym treningu
            item {
                TsToggleSection(
                    title = stringResource(R.string.profile_unfinished_section),
                    label = stringResource(R.string.profile_unfinished_toggle),
                    explain = stringResource(R.string.profile_unfinished_explain),
                    checked = draft.unfinishedWorkoutNotifyEnabled,
                    onChange = { draft = draft.copy(unfinishedWorkoutNotifyEnabled = it) }
                ) {
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

            // Flash przy końcu timera
            item {
                TsToggleSection(
                    title = stringResource(R.string.profile_flash_section),
                    label = stringResource(R.string.profile_flash_toggle),
                    explain = stringResource(R.string.profile_flash_explain),
                    checked = draft.flashOnTimerEnd,
                    onChange = { draft = draft.copy(flashOnTimerEnd = it) }
                )
            }

            // AI overlay (pływający asystent)
            item {
                TsToggleSection(
                    title = stringResource(R.string.profile_ai_overlay_section),
                    label = stringResource(R.string.profile_ai_overlay_toggle),
                    explain = stringResource(R.string.profile_ai_overlay_explain),
                    checked = draft.aiOverlayEnabled,
                    onChange = { draft = draft.copy(aiOverlayEnabled = it) }
                )
            }
        }
    }
}

@Composable
private fun TsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = DarkOnSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TsNumberFieldCard(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    TsSectionCard(title = label) {
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

@Composable
private fun TsTargetWeightField(
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
private fun TsToggleSection(
    title: String,
    label: String,
    explain: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    extra: @Composable () -> Unit = {}
) {
    TsSectionCard(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = onChange)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = DarkOnSurface
                )
                Text(
                    explain,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        extra()
    }
}

@Composable
private fun weightGoalLabel(g: WeightGoalType): String = when (g) {
    WeightGoalType.NONE -> stringResource(R.string.weight_goal_none)
    WeightGoalType.CUT -> stringResource(R.string.weight_goal_cut)
    WeightGoalType.BULK -> stringResource(R.string.weight_goal_bulk)
    WeightGoalType.MAINTAIN -> stringResource(R.string.weight_goal_maintain)
}
