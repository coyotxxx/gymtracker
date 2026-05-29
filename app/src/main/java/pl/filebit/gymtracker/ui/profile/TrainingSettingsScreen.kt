package pl.filebit.gymtracker.ui.profile

import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightUnit
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader

/**
 * Centralny ekran KONFIGURACJI (v1.28.2 — Etap 3 refaktoru "jedno źródło prawdy").
 * Jedno miejsce dla całej konfiguracji potrzebnej do uruchomienia planów i diety:
 * cel, trening, periodyzacja, dane do BMR, aktywność, preferencje żywieniowe,
 * zdrowie. Wszystkie pola żyją w scalonej encji UserProfile (Etap 1).
 * Nazwa kompozytu/route historyczna (TrainingSettings) — user widzi "Konfiguracja".
 */
@Composable
fun TrainingSettingsScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = hiltViewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val periodization by vm.periodization.collectAsStateWithLifecycle()
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
            item { ScreenHeader(title = "Konfiguracja", onBack = onBack) }

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
                    // v1.28.1 (Etap 2): jeden cel — edytujemy `goalType` (8 wartości),
                    // to samo pole co "Cel diety". Legacy `weightGoalType` jest
                    // auto-normalizowany przy zapisie (UserProfileRepository.save).
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(DietGoalType.entries.toList()) { g ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = dietGoalLabel(g),
                                selected = draft.goalType == g,
                                onClick = { draft = draft.copy(goalType = g) }
                            )
                        }
                    }
                    if (draft.goalType == DietGoalType.FAT_LOSS ||
                        draft.goalType == DietGoalType.MUSCLE_GAIN ||
                        draft.goalType == DietGoalType.EVENT_PREP
                    ) {
                        Spacer(Modifier.height(12.dp))
                        TsTargetWeightField(
                            value = draft.targetWeightKg,
                            onChange = { draft = draft.copy(targetWeightKg = it) }
                        )
                        Spacer(Modifier.height(12.dp))
                        CfgDecimalField(
                            label = "Tempo zmiany wagi (kg/tydzień)",
                            value = draft.paceKgPerWeek,
                            onChange = { draft = draft.copy(paceKgPerWeek = kotlin.math.abs(it)) }
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

            // v1.26.2 — Obszar zainteresowania (preferowane partie mięśniowe)
            item {
                MuscleGroupPickerCard(
                    selectedCsv = draft.preferredMuscleGroupsCsv,
                    onChange = { draft = draft.copy(preferredMuscleGroupsCsv = it) }
                )
            }

            // Mój sprzęt — używany przez AI generator planu
            item {
                EquipmentPickerCard(
                    selectedCsv = draft.equipmentCategoriesCsv,
                    onChange = { draft = draft.copy(equipmentCategoriesCsv = it) }
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

            // v2.10.0 — własny dźwięk końca przerwy
            item {
                RestSoundSection(
                    currentUri = draft.restSoundUri,
                    onPick = { draft = draft.copy(restSoundUri = it) }
                )
            }

            // v1.19.0 — Periodyzacja (v1.20.1: flat layout zamiast nested cards)
            item {
                TsSectionCard(
                    title = "Periodyzacja",
                    subtitle = "Preferencje cyklu treningowego (mesocykl + deload)."
                ) {
                    Spacer(Modifier.height(8.dp))
                    PeriodizationNumberField(
                        label = "Długość cyklu (tygodni)",
                        value = periodization.mesocycleWeeks,
                        range = pl.filebit.gymtracker.data.repository.PeriodizationPreferences.MIN_CYCLE_WEEKS..
                            pl.filebit.gymtracker.data.repository.PeriodizationPreferences.MAX_CYCLE_WEEKS,
                        onChange = { vm.setMesocycleWeeks(it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    PeriodizationNumberField(
                        label = "Długość deloadu (dni)",
                        value = periodization.deloadDays,
                        range = pl.filebit.gymtracker.data.repository.PeriodizationPreferences.MIN_DELOAD_DAYS..
                            pl.filebit.gymtracker.data.repository.PeriodizationPreferences.MAX_DELOAD_DAYS,
                        onChange = { vm.setDeloadDays(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = periodization.autoDeloadEnabled,
                            onCheckedChange = { vm.setAutoDeloadEnabled(it) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-deload (algorytm decyduje)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkOnSurface
                            )
                            Text(
                                "Algorytm proponuje deload przy stagnacji ≥30% ćwiczeń lub gdy " +
                                    "ACWR > 1.5 przez 2 tyg.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ===== KONFIGURACJA DIETY (v1.28.2 — Etap 3: jeden centralny ekran) =====

            item {
                TsSectionCard(
                    title = "Dane podstawowe",
                    subtitle = "Potrzebne do dokładnego wyliczenia zapotrzebowania kalorycznego (BMR)."
                ) {
                    Text(
                        "Płeć",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = DarkOnSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Gender.entries.toList()) { g ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = genderLabel(g),
                                selected = draft.gender == g,
                                onClick = { draft = draft.copy(gender = g) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PeriodizationNumberField(
                        label = "Wiek (lata)",
                        value = draft.ageYears,
                        range = 13..100,
                        onChange = { draft = draft.copy(ageYears = it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    PeriodizationNumberField(
                        label = "Wzrost (cm)",
                        value = draft.heightCm,
                        range = 120..230,
                        onChange = { draft = draft.copy(heightCm = it) }
                    )
                }
            }

            item {
                TsSectionCard(
                    title = "Aktywność poza treningiem",
                    subtitle = "Liczy się TYLKO ruch poza siłownią — treningi są doliczane osobno."
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ActivityLevel.entries.toList()) { a ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = activityLabel(a),
                                selected = draft.activityLevel == a,
                                onClick = { draft = draft.copy(activityLevel = a) }
                            )
                        }
                    }
                }
            }

            item {
                TsSectionCard(title = "Styl diety") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DietPreference.entries.toList()) { p ->
                            pl.filebit.gymtracker.ui.theme.SelectableChip(
                                text = dietPrefLabel(p),
                                selected = draft.dietPreference == p,
                                onClick = { draft = draft.copy(dietPreference = p) }
                            )
                        }
                    }
                }
            }

            item {
                TsSectionCard(
                    title = "Preferencje żywieniowe",
                    subtitle = "Produkty po przecinku. AI uwzględnia to przy generowaniu posiłków."
                ) {
                    CfgTextField("Alergeny", draft.allergies, "np. laktoza, orzechy") {
                        draft = draft.copy(allergies = it)
                    }
                    Spacer(Modifier.height(10.dp))
                    CfgTextField("Nietolerancje", draft.intolerances, "np. gluten") {
                        draft = draft.copy(intolerances = it)
                    }
                    Spacer(Modifier.height(10.dp))
                    CfgTextField("Produkty których unikam", draft.dislikedFoods, "np. brokuł, wątróbka") {
                        draft = draft.copy(dislikedFoods = it)
                    }
                    Spacer(Modifier.height(10.dp))
                    CfgTextField("Ulubione produkty", draft.lovedFoods, "np. twaróg, ryż, kurczak") {
                        draft = draft.copy(lovedFoods = it)
                    }
                }
            }

            item {
                TsSectionCard(title = "Dieta — praktyczne") {
                    PeriodizationNumberField(
                        label = "Czas gotowania na posiłek (min)",
                        value = draft.cookingTimePerMealMin,
                        range = 5..120,
                        onChange = { draft = draft.copy(cookingTimePerMealMin = it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    CfgNullableNumberField(
                        label = "Budżet tygodniowy na jedzenie (zł) — opcjonalnie",
                        value = draft.weeklyBudgetPln,
                        onChange = { draft = draft.copy(weeklyBudgetPln = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    CfgInlineToggle("Jadam posiłki w pracy", draft.eatsAtWork) {
                        draft = draft.copy(eatsAtWork = it)
                    }
                    Spacer(Modifier.height(8.dp))
                    CfgInlineToggle("Mam mikrofalówkę w pracy", draft.hasMicrowaveAtWork) {
                        draft = draft.copy(hasMicrowaveAtWork = it)
                    }
                    Spacer(Modifier.height(8.dp))
                    CfgInlineToggle("Interesuje mnie meal prep", draft.mealPrepInterested) {
                        draft = draft.copy(mealPrepInterested = it)
                    }
                }
            }

            item {
                TsSectionCard(
                    title = "Zdrowie i kontuzje",
                    subtitle = "AI uwzględnia to przy planach treningu i diety. To nie zastępuje konsultacji z lekarzem."
                ) {
                    CfgTextField("Stany zdrowotne", draft.medicalConditions, "np. nadciśnienie, cukrzyca") {
                        draft = draft.copy(medicalConditions = it)
                    }
                    Spacer(Modifier.height(10.dp))
                    CfgTextField(
                        label = "Kontuzje i ograniczenia ruchowe",
                        value = draft.injuriesNotes,
                        placeholder = "np. ból barku przy wyciskaniu nad głowę, problem z kolanem",
                        singleLine = false
                    ) {
                        draft = draft.copy(injuriesNotes = it)
                    }
                }
            }

        }
    }
}

/**
 * v2.10.0 — wybór dźwięku końca przerwy: domyślne beepy / dźwięk systemowy
 * (picker dzwonków) / własny plik audio (SAF). Gra na wyjściu MEDIA.
 */
@Composable
private fun RestSoundSection(
    currentUri: String?,
    onPick: (String?) -> Unit
) {
    val context = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            onPick(uri?.toString())
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPick(uri.toString())
        }
    }

    val label = remember(currentUri) { soundLabel(context, currentUri) }

    TsSectionCard(
        title = "Dźwięk końca przerwy",
        subtitle = "Gra na wyjściu multimediów — w słuchawkach Bluetooth gdy podłączone, inaczej w głośniku."
    ) {
        Text(
            "Wybrany: $label",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = DarkOnSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundActionChip("Systemowy", Modifier.weight(1f)) {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Wybierz dźwięk")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    currentUri?.let {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                    }
                }
                runCatching { ringtoneLauncher.launch(intent) }
            }
            SoundActionChip("Własny plik", Modifier.weight(1f)) {
                runCatching { fileLauncher.launch(arrayOf("audio/*")) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundActionChip("Domyślny (beepy)", Modifier.weight(1f)) { onPick(null) }
            SoundActionChip(
                "Posłuchaj",
                Modifier.weight(1f),
                enabled = currentUri != null
            ) { previewSound(context, currentUri) }
        }
    }
}

@Composable
private fun SoundActionChip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) AccentOrange else DarkOnSurfaceVariant
        )
    }
}

private fun soundLabel(context: android.content.Context, uri: String?): String {
    if (uri.isNullOrBlank()) return "Domyślny (3 beepy)"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Własny dźwięk"
}

private fun previewSound(context: android.content.Context, uri: String?) {
    if (uri.isNullOrBlank()) return
    runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
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

/**
 * v1.20.1 — inline number field bez własnego Card (do użycia wewnątrz TsSectionCard,
 * unika podwójnej ramki).
 */
@Composable
private fun PeriodizationNumberField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = DarkOnSurface
        )
        Spacer(Modifier.height(4.dp))
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

private fun dietGoalLabel(g: DietGoalType): String = when (g) {
    DietGoalType.FAT_LOSS -> "Redukcja"
    DietGoalType.MUSCLE_GAIN -> "Masa"
    DietGoalType.RECOMP -> "Recomp"
    DietGoalType.MAINTAIN -> "Utrzymanie"
    DietGoalType.STRENGTH -> "Siła"
    DietGoalType.ENDURANCE -> "Wytrzymałość"
    DietGoalType.HEALTH -> "Zdrowie"
    DietGoalType.EVENT_PREP -> "Event prep"
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

// ===== v1.28.2 (Etap 3) — helpery sekcji konfiguracji diety =====

private fun genderLabel(g: Gender): String = when (g) {
    Gender.MALE -> "Mężczyzna"
    Gender.FEMALE -> "Kobieta"
}

private fun activityLabel(a: ActivityLevel): String = when (a) {
    ActivityLevel.SEDENTARY -> "Siedzący"
    ActivityLevel.LIGHT -> "Lekko aktywny"
    ActivityLevel.MODERATE -> "Umiarkowanie"
    ActivityLevel.VERY_ACTIVE -> "Bardzo aktywny"
    ActivityLevel.EXTREME -> "Ekstremalnie"
}

private fun dietPrefLabel(p: DietPreference): String = when (p) {
    DietPreference.STANDARD -> "Standardowa"
    DietPreference.VEGETARIAN -> "Wegetariańska"
    DietPreference.VEGAN -> "Wegańska"
    DietPreference.PESCATARIAN -> "Pescatariańska"
    DietPreference.KETO -> "Keto"
    DietPreference.MEDITERRANEAN -> "Śródziemnomorska"
}

/** Tekstowe pole konfiguracji (CSV / wolny tekst) — sterowane wartością draftu. */
@Composable
private fun CfgTextField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = DarkOnSurfaceVariant) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Przełącznik inline (bez własnej ramki — do użycia wewnątrz TsSectionCard). */
@Composable
private fun CfgInlineToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Pole liczbowe opcjonalne — puste = null. */
@Composable
private fun CfgNullableNumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = DarkOnSurface
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }
                onChange(text.toIntOrNull())
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Pole dziesiętne (np. tempo kg/tydz). */
@Composable
private fun CfgDecimalField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) {
        mutableStateOf(if (value == 0.0) "" else "%.2f".format(value).replace(',', '.'))
    }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = DarkOnSurface
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v
                v.replace(',', '.').toDoubleOrNull()?.let { onChange(it) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * v1.26.2 — wizualny kafel wyboru (mięsień/sprzęt). Wyraźny stan zaznaczenia:
 * accent border 2dp + tło accent + ✓ badge w rogu. Niezaznaczony — neutralne
 * tło, subtelny border. "Szybko pomaga ustawić wizualnie" (feedback Macieja).
 */
@Composable
private fun PickerTile(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    val accent = pl.filebit.gymtracker.ui.theme.AccentOrange
    Box(
        modifier = modifier
            .height(if (iconRes != null) 84.dp else 56.dp)
            .background(
                if (isSelected) accent.copy(alpha = 0.18f)
                else pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent
                else pl.filebit.gymtracker.ui.theme.DarkOutline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isSelected) accent
                    else pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                ),
                color = if (isSelected) accent
                else pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 13.sp
            )
        }
        if (isSelected) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * v1.26.2 — "Obszar zainteresowania": wizualny wybór preferowanych grup
 * mięśniowych. Pusty wybór = brak preferencji (wszystkie partie).
 */
@Composable
private fun MuscleGroupPickerCard(
    selectedCsv: String,
    onChange: (String) -> Unit
) {
    val selected = remember(selectedCsv) {
        selectedCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }
    val all = pl.filebit.gymtracker.data.entity.MuscleGroup.values()
        .filter {
            it != pl.filebit.gymtracker.data.entity.MuscleGroup.OTHER &&
            it != pl.filebit.gymtracker.data.entity.MuscleGroup.CARDIO
        }

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
                "🎯 Obszar zainteresowania",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Zaznacz partie na których chcesz się skupić. AI będzie priorytetyzował " +
                    "te grupy w generowanych planach. Pusty wybór = trening całego ciała.",
                style = MaterialTheme.typography.bodySmall,
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // v1.26.4 — proste kafle tekstowe (bez ikon), grid 3 kolumny
            val rows = all.toList().chunked(3)
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { mg ->
                        val isSelected = mg.name in selected
                        PickerTile(
                            label = mg.displayName(),
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val newSet = if (isSelected) selected - mg.name else selected + mg.name
                                onChange(newSet.joinToString(","))
                            }
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EquipmentPickerCard(
    selectedCsv: String,
    onChange: (String) -> Unit
) {
    val selected = remember(selectedCsv) {
        pl.filebit.gymtracker.data.entity.EquipmentCategory.parse(selectedCsv)
    }
    val all = pl.filebit.gymtracker.data.entity.EquipmentCategory.entries

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
                    "na tym sprzęcie. Pusty wybór = brak ograniczeń (siłownia kompletna).",
                style = MaterialTheme.typography.bodySmall,
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // v1.26.5 — wizualne kafle z ikonami SVG, grid 3 kolumny
            val rows = all.chunked(3)
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { cat ->
                        val isSelected = cat in selected
                        PickerTile(
                            iconRes = cat.iconRes,
                            label = cat.label,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val newSet = if (isSelected) selected - cat else selected + cat
                                onChange(newSet.joinToString(",") { it.name })
                            }
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Presety
            EquipmentPreset("🏟 Siłownia kompletna") { onChange("") }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    EquipmentPreset("🏠 Dom") {
                        onChange(pl.filebit.gymtracker.data.entity.EquipmentCategory.HOME
                            .joinToString(",") { it.name })
                    }
                }
                Box(Modifier.weight(1f)) {
                    EquipmentPreset("🤸 Tylko masa ciała") {
                        onChange(pl.filebit.gymtracker.data.entity.EquipmentCategory.BODYWEIGHT_ONLY
                            .joinToString(",") { it.name })
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentPreset(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                pl.filebit.gymtracker.ui.theme.SuccessGreen.copy(alpha = 0.10f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = pl.filebit.gymtracker.ui.theme.SuccessGreen
        )
    }
}

