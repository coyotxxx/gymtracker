package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.SelectableChip

@Composable
fun DietSettingsDialog(
    initial: DietConfig,
    onSave: (DietConfig) -> Unit,
    onEditProfile: () -> Unit = {},
    onEditWeeklyKcal: () -> Unit = {},
    onHealthConnectEnableRequest: () -> Unit = {},
    onHealthConnectDisable: () -> Unit = {},
    // v1.24.25: szybka edycja profilu (cel + tempo + aktywność) w dialogu —
    // bez wymogu przechodzenia całego DietOnboarding wizard od nowa.
    currentDietProfile: pl.filebit.gymtracker.data.entity.UserDietProfile? = null,
    onSaveDietProfile: (
        pl.filebit.gymtracker.data.entity.DietGoalType,
        Double,
        pl.filebit.gymtracker.data.entity.ActivityLevel
    ) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    var meals by remember { mutableStateOf(initial.mealsPerDay) }
    var window by remember { mutableStateOf(initial.eatingWindowHours) }
    var startHour by remember { mutableStateOf(initial.windowStartHour) }
    var reminders by remember { mutableStateOf(initial.mealRemindersEnabled) }
    var autoCheck by remember { mutableStateOf(initial.autoCheckAdjustments) }
    var hcSync by remember { mutableStateOf(initial.healthConnectSyncEnabled) }
    // v1.24.25: state dla profilu dietetycznego.
    // v1.28.1 (Etap 2): klucz `currentDietProfile` — gdy profil doładuje się PO
    // otwarciu dialogu (flow async), stan re-inicjalizuje się prawdziwymi danymi
    // zamiast zostać na wartościach domyślnych.
    var goalType by remember(currentDietProfile) {
        mutableStateOf(
            currentDietProfile?.goalType
                ?: pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN
        )
    }
    var paceKgPerWeek by remember(currentDietProfile) {
        mutableStateOf(currentDietProfile?.paceKgPerWeek?.let { kotlin.math.abs(it) } ?: 0.5)
    }
    var activityLevel by remember(currentDietProfile) {
        mutableStateOf(
            currentDietProfile?.activityLevel
                ?: pl.filebit.gymtracker.data.entity.ActivityLevel.LIGHT
        )
    }

    val previewConfig = initial.copy(
        mealsPerDay = meals,
        eatingWindowHours = window,
        windowStartHour = startHour,
        mealRemindersEnabled = reminders,
        autoCheckAdjustments = autoCheck,
        healthConnectSyncEnabled = hcSync
    )
    val mealHours = previewConfig.mealHoursDecimal()

    ScrollableDialogShell(
        title = "Konfiguracja diety",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = DarkOnSurfaceVariant)
            }
            TextButton(onClick = {
                // v1.24.25: save zarówno DietConfig (preferences) jak i DietProfile (cel/tempo/aktywność)
                onSave(previewConfig)
                if (currentDietProfile != null) {
                    onSaveDietProfile(goalType, paceKgPerWeek, activityLevel)
                }
                onDismiss()
            }) {
                Text("Zapisz", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        bodyArrangement = Arrangement.spacedBy(14.dp)
    ) {
                // === v1.24.25: PROFIL DIETETYCZNY (cel + tempo + aktywność) ===
                // Filozofia: user widzi najważniejsze ustawienia diety na górze.
                // Bez tej sekcji trzeba było przechodzić cały 6-stopniowy DietOnboarding.
                if (currentDietProfile != null) {
                    // Cel diety
                    SettingSection("Cel diety") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pl.filebit.gymtracker.data.entity.DietGoalType.entries.toList()) { gt ->
                                SelectableChip(
                                    text = dietGoalLabel(gt),
                                    selected = goalType == gt,
                                    onClick = { goalType = gt }
                                )
                            }
                        }
                        Text(
                            dietGoalDescription(goalType),
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }

                    // Tempo (slider) — tylko dla typów wagowych
                    val isPaceRelevant = goalType == pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS ||
                        goalType == pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN ||
                        goalType == pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP
                    val isLoss = goalType == pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS ||
                        goalType == pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP
                    if (isPaceRelevant) {
                        val sign = if (isLoss) "−" else "+"
                        SettingSection("Tempo: $sign${"%.2f".format(paceKgPerWeek)} kg/tydz") {
                            Slider(
                                value = paceKgPerWeek.toFloat(),
                                onValueChange = { paceKgPerWeek = (it * 100).toInt() / 100.0 },
                                valueRange = 0.0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentOrange,
                                    activeTrackColor = AccentOrange
                                )
                            )
                            Text(
                                paceDescription(paceKgPerWeek, isLoss),
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentOrange
                            )
                        }
                    }

                    // Aktywność POZA treningiem
                    SettingSection("Aktywność POZA treningiem") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pl.filebit.gymtracker.data.entity.ActivityLevel.entries.toList()) { lvl ->
                                SelectableChip(
                                    text = activityLabel(lvl),
                                    selected = activityLevel == lvl,
                                    onClick = { activityLevel = lvl }
                                )
                            }
                        }
                        Text(
                            "Liczy się TYLKO aktywność poza siłownią. Treningi są doliczane osobno (×30 kcal/dzień).",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Liczba posiłków
                SettingSection("Liczba posiłków: $meals") {
                    Slider(
                        value = meals.toFloat(),
                        onValueChange = { meals = it.toInt().coerceIn(2, 6) },
                        valueRange = 2f..6f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentOrange,
                            activeTrackColor = AccentOrange
                        )
                    )
                }

                // Długość okna
                SettingSection("Długość okna żywieniowego") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(6, 8, 10, 12, 24)) { hours ->
                            SelectableChip(
                                text = if (hours == 24) "24h (bez IF)" else "${hours}h",
                                selected = window == hours,
                                onClick = { window = hours }
                            )
                        }
                    }
                }

                // Start okna (godzina)
                SettingSection("Start okna: %02d:00".format(startHour)) {
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt().coerceIn(4, 18) },
                        valueRange = 4f..18f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentOrange,
                            activeTrackColor = AccentOrange
                        )
                    )
                    Text(
                        "Okno: %02d:00 - %02d:00".format(startHour, (startHour + window).coerceAtMost(24)),
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Powiadomienia
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminders,
                        onCheckedChange = { reminders = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text("Powiadomienia o posiłkach", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                        Text(
                            "Pora posiłku przypomni notyfikacja",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Auto-korekty co 14 dni
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = { autoCheck = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text("Auto-sprawdzanie planu", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                        Text(
                            "Co 14 dni AI analizuje trend wagi i sugeruje korekty kcal (notyfikacja)",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Health Connect sync (kroki)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = hcSync,
                        onCheckedChange = { newValue ->
                            hcSync = newValue
                            // Kluczowa rzecz: gdy user WŁĄCZA → trigger permission flow w VM
                            // (ten dialog tylko aktualizuje lokalną zmienną, faktyczny zapis
                            // dzieje się przy "Zapisz" po wywołaniu onSave)
                            if (newValue) {
                                onHealthConnectEnableRequest()
                            } else {
                                onHealthConnectDisable()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text("🔗 Health Connect (kroki)", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                        Text(
                            "Auto-sync kroków z Google Health Connect (zamiast wpisywania ręcznie). Wymaga aplikacji Health Connect i zgody.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Preview godzin
                SettingSection("Pory posiłków") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            mealHours.joinToString("  ·  ") { previewConfig.formatTime(it) },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = AccentOrange
                        )
                    }
                }

                // Edytuj profil dietetyczny (alergie, preferencje, budżet, choroby)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                        .clickable {
                            onEditProfile()
                            onDismiss()
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "🩺 Edytuj profil dietetyczny",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = DarkOnSurface
                        )
                        Text(
                            "Wiek, wzrost, alergie, preferencje, budżet, stany zdrowia",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }

                // Refeed/deficyt cyklicznie
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                        .clickable {
                            onEditWeeklyKcal()
                            onDismiss()
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        val activeOverrides = previewConfig.weeklyKcalOverrides.size
                        Text(
                            if (activeOverrides > 0) "🔁 Refeed/deficyt: $activeOverrides dni"
                            else "🔁 Refeed / deficyt cyklicznie",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = DarkOnSurface
                        )
                        Text(
                            "Różne kcal w różne dni tygodnia (np. wyższe pn/wt/nd, niższe śr/czw)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
    }
}

@Composable
private fun SettingSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.padding(top = 4.dp))
        content()
    }
}

// v1.24.25: helper labels dla profilu dietetycznego
private fun dietGoalLabel(g: pl.filebit.gymtracker.data.entity.DietGoalType): String = when (g) {
    pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS -> "Redukcja"
    pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN -> "Masa"
    pl.filebit.gymtracker.data.entity.DietGoalType.RECOMP -> "Recomp"
    pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN -> "Utrzymanie"
    pl.filebit.gymtracker.data.entity.DietGoalType.STRENGTH -> "Siła"
    pl.filebit.gymtracker.data.entity.DietGoalType.ENDURANCE -> "Wytrzymałość"
    pl.filebit.gymtracker.data.entity.DietGoalType.HEALTH -> "Zdrowie"
    pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP -> "Event prep"
}

private fun dietGoalDescription(g: pl.filebit.gymtracker.data.entity.DietGoalType): String = when (g) {
    pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS -> "Redukcja tkanki tłuszczowej (deficyt z tempa)"
    pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN -> "Budowa masy mięśniowej (surplus z tempa)"
    pl.filebit.gymtracker.data.entity.DietGoalType.RECOMP -> "Lekki deficyt −300 kcal — chudniesz na tłuszczu, budujesz mięśnie"
    pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN -> "Utrzymanie wagi (kcal = TDEE)"
    pl.filebit.gymtracker.data.entity.DietGoalType.STRENGTH -> "Maintenance + dużo węgli (regeneracja CNS)"
    pl.filebit.gymtracker.data.entity.DietGoalType.ENDURANCE -> "+100 kcal + min 5 g/kg węgli (glikogen)"
    pl.filebit.gymtracker.data.entity.DietGoalType.HEALTH -> "Maintenance + focus na jakość"
    pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP -> "Agresywna redukcja przed zawodami/sesją"
}

private fun paceDescription(pace: Double, isLoss: Boolean): String {
    if (pace == 0.0) return "Brak zmiany wagi"
    val direction = if (isLoss) "redukcji" else "przyrostu"
    return when {
        pace >= 1.0 && isLoss -> "⚠ Agresywne tempo — ryzyko utraty mięśni"
        pace >= 0.5 -> "Klasyczne tempo $direction (zdrowe)"
        pace > 0 -> "Łagodne tempo $direction (ochrona masy mięśniowej)"
        else -> "Brak"
    }
}

private fun activityLabel(a: pl.filebit.gymtracker.data.entity.ActivityLevel): String = when (a) {
    pl.filebit.gymtracker.data.entity.ActivityLevel.SEDENTARY -> "Siedzący"
    pl.filebit.gymtracker.data.entity.ActivityLevel.LIGHT -> "Lekko aktywny"
    pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE -> "Umiarkowanie"
    pl.filebit.gymtracker.data.entity.ActivityLevel.VERY_ACTIVE -> "Bardzo aktywny"
    pl.filebit.gymtracker.data.entity.ActivityLevel.EXTREME -> "Ekstremalnie"
}
