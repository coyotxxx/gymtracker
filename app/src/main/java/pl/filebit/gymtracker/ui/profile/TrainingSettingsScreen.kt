package pl.filebit.gymtracker.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.WeightUnit
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

            // Cel treningu
            item {
                TsSectionCard(
                    title = stringResource(R.string.profile_goal),
                    subtitle = "Wpływa na sugestie planów, dobór obciążeń i intensywności w analizie AI."
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(TrainingGoal.entries.toList()) { g ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = trainingGoalLabel(g),
                                selected = draft.goal == g,
                                onClick = { draft = draft.copy(goal = g) }
                            )
                        }
                    }
                }
            }

            // Doświadczenie
            item {
                TsSectionCard(title = stringResource(R.string.profile_experience)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(ExperienceLevel.entries.toList()) { e ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = experienceLabel(e),
                                selected = draft.experience == e,
                                onClick = { draft = draft.copy(experience = e) }
                            )
                        }
                    }
                }
            }

            // Jednostka wagi
            item {
                TsSectionCard(title = stringResource(R.string.profile_unit)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(WeightUnit.entries.toList()) { u ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = u.name,
                                selected = draft.preferredUnit == u,
                                onClick = { draft = draft.copy(preferredUnit = u) }
                            )
                        }
                    }
                }
            }

            // Cel wagowy + waga docelowa
            item {
                TsSectionCard(
                    title = stringResource(R.string.profile_weight_goal),
                    subtitle = "Określa kierunek bilansu kalorycznego sugerowanego przez AI."
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(WeightGoalType.entries.toList()) { g ->
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

            // Mój sprzęt — używany przez AI generator planu
            item {
                EquipmentPickerCard(
                    selectedCsv = draft.availableEquipmentCsv,
                    onChange = { draft = draft.copy(availableEquipmentCsv = it) }
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

            // Codzienny check AI (v0.87)
            item {
                TsToggleSection(
                    title = "Codzienny check AI",
                    label = "Włącz powiadomienia",
                    explain = "AI raz dziennie sprawdza Twoje treningi i wysyła pojedyncze powiadomienie " +
                        "gdy wykryje sygnał: powtarzający się ból, stagnacja, partia >7 dni bez treningu. " +
                        "Tap otwiera AI Trener z preselected akcją.",
                    checked = draft.aiProactiveChecksEnabled,
                    onChange = { draft = draft.copy(aiProactiveChecksEnabled = it) }
                )
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

private fun trainingGoalLabel(g: TrainingGoal): String = when (g) {
    TrainingGoal.STRENGTH -> "Siła"
    TrainingGoal.HYPERTROPHY -> "Masa mięśniowa"
    TrainingGoal.MIX -> "Siła + masa"
    TrainingGoal.GENERAL_FITNESS -> "Sprawność ogólna"
    TrainingGoal.CARDIO_LIFTING -> "Cardio + siłownia"
}

private fun experienceLabel(e: ExperienceLevel): String = when (e) {
    ExperienceLevel.BEGINNER -> "Początkujący"
    ExperienceLevel.INTERMEDIATE -> "Średnio zaawansowany"
    ExperienceLevel.ADVANCED -> "Zaawansowany"
}

@Composable
private fun EquipmentPickerCard(
    selectedCsv: String,
    onChange: (String) -> Unit
) {
    val selected = remember(selectedCsv) {
        selectedCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }
    val all = pl.filebit.gymtracker.data.entity.Equipment.values()
        .filter { it != pl.filebit.gymtracker.data.entity.Equipment.OTHER }

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = pl.filebit.gymtracker.ui.theme.DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, pl.filebit.gymtracker.ui.theme.DarkOutline
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🏋 Mój sprzęt",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Zaznacz co masz dostępne. AI będzie generował plany TYLKO z ćwiczeń " +
                    "na tym sprzęcie. Pusty wybór = brak ograniczeń (siłownia z pełnym wyposażeniem).",
                style = MaterialTheme.typography.bodySmall,
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // Grid 2 kolumny
            val pairs = all.chunked(2)
            for (pair in pairs) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { eq ->
                        val isSelected = eq.name in selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) pl.filebit.gymtracker.ui.theme.AccentOrange.copy(alpha = 0.18f)
                                    else pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val newSet = if (isSelected) selected - eq.name else selected + eq.name
                                    onChange(newSet.joinToString(","))
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "${if (isSelected) "✓" else "○"} ${equipmentLabel(eq)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) pl.filebit.gymtracker.ui.theme.AccentOrange
                                       else pl.filebit.gymtracker.ui.theme.DarkOnSurface
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Quick presets
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(pl.filebit.gymtracker.ui.theme.SuccessGreen.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .clickable {
                            // Maciej preset (z xlsx)
                            onChange("BARBELL,DUMBBELLS,BODYWEIGHT,CABLE")
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🏠 Dom (sztanga + sztangielki + drążek)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = pl.filebit.gymtracker.ui.theme.SuccessGreen
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(pl.filebit.gymtracker.ui.theme.SuccessGreen.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .clickable { onChange("BODYWEIGHT") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💪 Tylko masa ciała",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = pl.filebit.gymtracker.ui.theme.SuccessGreen
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(pl.filebit.gymtracker.ui.theme.SuccessGreen.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .clickable { onChange("") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🏋 Pełna siłownia (wszystko)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = pl.filebit.gymtracker.ui.theme.SuccessGreen
                    )
                }
            }
        }
    }
}

private fun equipmentLabel(e: pl.filebit.gymtracker.data.entity.Equipment): String = when (e) {
    pl.filebit.gymtracker.data.entity.Equipment.BARBELL -> "Sztanga olimpijska"
    pl.filebit.gymtracker.data.entity.Equipment.DUMBBELLS -> "Sztangielki / hantle"
    pl.filebit.gymtracker.data.entity.Equipment.MACHINE -> "Maszyny siłowe"
    pl.filebit.gymtracker.data.entity.Equipment.CABLE -> "Wyciąg / linki"
    pl.filebit.gymtracker.data.entity.Equipment.BODYWEIGHT -> "Masa ciała / drążek"
    pl.filebit.gymtracker.data.entity.Equipment.OTHER -> "Inne"
}
