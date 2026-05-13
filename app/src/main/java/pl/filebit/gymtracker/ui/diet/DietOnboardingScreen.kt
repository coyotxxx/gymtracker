package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutline
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SelectableChip

private const val TOTAL_STEPS = 6

@Composable
fun DietOnboardingScreen(
    onCompleted: () -> Unit,
    onBack: () -> Unit,
    vm: DietOnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        ScreenHeader(title = "Konfiguracja diety", onBack = onBack)

        // Progress dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(TOTAL_STEPS) { idx ->
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .weight(1f)
                        .background(
                            if (idx <= step) AccentOrange else DarkSurfaceVariant,
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                when (step) {
                    0 -> StepIntro(state)
                    1 -> StepBasics(state, vm)
                    2 -> StepActivity(state, vm)
                    3 -> StepGoal(state, vm)
                    4 -> StepPreferences(state, vm)
                    5 -> StepPracticalAndMedical(state, vm)
                }
            }
        }

        // Bottom navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (step > 0) {
                TextButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("← Wstecz", color = DarkOnSurfaceVariant)
                }
            }
            Button(
                onClick = {
                    if (step < TOTAL_STEPS - 1) step++
                    else vm.complete(onCompleted)
                },
                modifier = Modifier.weight(2f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (step < TOTAL_STEPS - 1) "Dalej" else "✓ Zakończ",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StepIntro(state: DietOnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader("Krok 1 z 6", "Witaj w module DIETA")
        Text(
            "Skonfigurujmy Twoją dietę. Skorzystam z danych które już znamy z konfiguracji treningu, " +
                "i dopytam tylko o to czego brakuje.",
            color = DarkOnSurface
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "WIEMY O TOBIE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = AccentOrange
                )
                state.knownWeightKg?.let {
                    Text("• Aktualna waga: $it kg", color = DarkOnSurface)
                }
                Text("• Cel treningu: ${state.knownGoalLabel}", color = DarkOnSurface)
                Text("• Treningów/tydzień: ${state.knownDaysPerWeek}", color = DarkOnSurface)
            }
        }

        Text(
            "Następne kroki: wiek/wzrost → aktywność → cel dietetyczny → preferencje → praktyczne → zdrowie. ~3 minuty.",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun StepBasics(state: DietOnboardingState, vm: DietOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader("Krok 2 z 6", "Podstawowe dane")
        Text(
            "Te wartości pozwolą obliczyć dokładny BMR (podstawowa przemiana materii) wzorem Mifflin-St Jeor — " +
                "fundament wyliczenia dziennych kcal.",
            color = DarkOnSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        FieldLabel("Wiek (lata)", "${state.ageYears}")
        Slider(
            value = state.ageYears.toFloat(),
            onValueChange = { vm.setAge(it.toInt()) },
            valueRange = 13f..100f,
            colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
        )

        FieldLabel("Wzrost (cm)", "${state.heightCm}")
        Slider(
            value = state.heightCm.toFloat(),
            onValueChange = { vm.setHeight(it.toInt()) },
            valueRange = 140f..220f,
            colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
        )
    }
}

@Composable
private fun StepActivity(state: DietOnboardingState, vm: DietOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader("Krok 3 z 6", "Aktywność POZA treningiem")
        Text(
            "Wpływa na TDEE (całkowite zapotrzebowanie). Treningi już są w bazie — tu chodzi o resztę dnia: " +
                "praca, chodzenie, codzienna aktywność.",
            color = DarkOnSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        ActivityLevel.entries.forEach { lvl ->
            ActivityRow(
                level = lvl,
                selected = state.activityLevel == lvl,
                onClick = { vm.setActivity(lvl) }
            )
        }
    }
}

@Composable
private fun StepGoal(state: DietOnboardingState, vm: DietOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader("Krok 4 z 6", "Cel dietetyczny")
        Text(
            "Możesz mieć inny cel niż treningowy — np. budowa siły bez zmiany masy.",
            color = DarkOnSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        DietGoalType.entries.forEach { goal ->
            GoalRow(
                goal = goal,
                selected = state.goalType == goal,
                onClick = { vm.setGoalType(goal) }
            )
        }

        Spacer(Modifier.height(8.dp))
        // v1.24.24: pace zawsze positive (magnitude). Kierunek (redukcja/surplus)
        // wynika z goalType. Slider 0..1.5 kg/tydz. Label dostosowany do typu.
        val absPace = kotlin.math.abs(state.paceKgPerWeek)
        val isLossGoal = state.goalType == DietGoalType.FAT_LOSS ||
            state.goalType == DietGoalType.EVENT_PREP
        val isGainGoal = state.goalType == DietGoalType.MUSCLE_GAIN
        FieldLabel(
            "Tempo zmiany wagi",
            when {
                isLossGoal -> "−%.2f kg/tydzień (chudniesz)".format(absPace)
                isGainGoal -> "+%.2f kg/tydzień (przybierasz)".format(absPace)
                else -> "%.2f kg/tydzień".format(absPace)
            }
        )
        Slider(
            value = absPace.toFloat(),
            onValueChange = { vm.setPace((it * 100).toInt() / 100.0) },
            valueRange = 0.0f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
        )
        Text(
            when {
                absPace >= 1.0 && isLossGoal -> "⚠ Agresywny — ryzyko utraty masy mięśniowej"
                absPace >= 0.5 && isLossGoal -> "Klasyczna redukcja"
                absPace > 0 && isLossGoal -> "Łagodna redukcja — chroni mięśnie"
                absPace == 0.0 -> "Utrzymanie / rekompozycja"
                absPace <= 0.3 && isGainGoal -> "Lean bulk — minimalny tłuszcz"
                isGainGoal -> "Większy zysk masy + tłuszcz"
                else -> "Tempo nie dotyczy tego celu"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AccentOrange
        )
    }
}

@Composable
private fun StepPreferences(state: DietOnboardingState, vm: DietOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader("Krok 5 z 6", "Preferencje i alergie")

        FieldLabel("Typ diety", "")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DietPreference.entries) { pref ->
                SelectableChip(
                    text = labelForPref(pref),
                    selected = state.dietPreference == pref,
                    onClick = { vm.setPreference(pref) }
                )
            }
        }

        FieldLabel("Alergie / nietolerancje", "")
        val allergens = listOf("laktoza", "gluten", "jaja", "orzechy", "soja", "ryby", "owoce_morza", "sezam", "gorczyca")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allergens) { a ->
                SelectableChip(
                    text = a,
                    selected = a in state.allergies,
                    onClick = { vm.toggleAllergy(a) }
                )
            }
        }

        OutlinedTextField(
            value = state.lovedFoods,
            onValueChange = vm::setLoved,
            label = { Text("Ulubione produkty (przecinkami)") },
            placeholder = { Text("np. kurczak, łosoś, awokado", color = DarkOnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            colors = wizardFieldColors()
        )

        OutlinedTextField(
            value = state.dislikedFoods,
            onValueChange = vm::setDisliked,
            label = { Text("Nielubione produkty (przecinkami)") },
            placeholder = { Text("np. brokuły, cieciorka", color = DarkOnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            colors = wizardFieldColors()
        )
    }
}

@Composable
private fun StepPracticalAndMedical(state: DietOnboardingState, vm: DietOnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader("Krok 6 z 6", "Praktyczne + zdrowie")

        FieldLabel("Czas na 1 posiłek", "${state.cookingTimePerMealMin} min")
        Slider(
            value = state.cookingTimePerMealMin.toFloat(),
            onValueChange = { vm.setCookingTime(it.toInt()) },
            valueRange = 5f..60f,
            colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
        )

        ToggleRow("Jadam w pracy/szkole", state.eatsAtWork, vm::setEatsAtWork)
        if (state.eatsAtWork) {
            ToggleRow("Mam dostęp do mikrofalówki", state.hasMicrowaveAtWork, vm::setMicrowave)
        }
        ToggleRow("Chcę meal prep (gotowanie raz / 2-3 dni)", state.mealPrepInterested, vm::setMealPrep)

        Spacer(Modifier.height(8.dp))
        FieldLabel(
            "Budżet tygodniowy (PLN)",
            state.weeklyBudgetPln?.let { "$it zł" } ?: "bez limitu"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf<Int?>(null, 100, 150, 200, 300, 500)) { budget ->
                SelectableChip(
                    text = budget?.let { "$it zł" } ?: "bez limitu",
                    selected = state.weeklyBudgetPln == budget,
                    onClick = { vm.setBudget(budget) }
                )
            }
        }
        Text(
            "AI dobierze produkty pasujące do budżetu (np. kurczak/jaja zamiast łososia/wołowiny w tańszych poziomach)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = DarkOnSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))
        FieldLabel("Stany zdrowia (wymaga konsultacji ze specjalistą)", "")
        val medFlags = listOf("cukrzyca", "nadciśnienie", "choroby_nerek", "choroby_wątroby", "choroby_serca", "ciąża", "karmienie_piersią", "zaburzenia_odżywiania")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(medFlags) { m ->
                SelectableChip(
                    text = m.replace('_', ' '),
                    selected = m in state.medicalConditions,
                    onClick = { vm.toggleMedical(m) }
                )
            }
        }
        if (state.medicalConditions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pl.filebit.gymtracker.ui.theme.ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        "⚠ WAŻNE",
                        fontWeight = FontWeight.Bold,
                        color = pl.filebit.gymtracker.ui.theme.ErrorRed
                    )
                    Text(
                        "Aplikacja nie jest narzędziem medycznym. Skonsultuj plan ze specjalistą " +
                            "(dietetyk kliniczny, lekarz). Apka będzie konserwatywna w sugestiach.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Rozumiem i akceptuję", state.medicalAwareness, vm::setMedicalAwareness)
                }
            }
        }
    }
}

// ============================================================
// Komponenty pomocnicze
// ============================================================

@Composable
private fun StepHeader(stepLabel: String, title: String) {
    Column {
        Text(
            stepLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = AccentOrange
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = DarkOnSurface
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FieldLabel(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotBlank()) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentOrange,
                checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = DarkOnSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActivityRow(level: ActivityLevel, selected: Boolean, onClick: () -> Unit) {
    val (label, desc) = when (level) {
        ActivityLevel.SEDENTARY -> "Siedzący tryb" to "Biuro, mało ruchu, <5000 kroków"
        ActivityLevel.LIGHT -> "Lekko aktywny" to "Trochę chodzenia, 5-7k kroków"
        ActivityLevel.MODERATE -> "Umiarkowany" to "Sporo chodzenia, 7-10k kroków"
        ActivityLevel.VERY_ACTIVE -> "Bardzo aktywny" to "Praca fizyczna, >10k kroków"
        ActivityLevel.EXTREME -> "Ekstremalny" to "Zawodowy sportowiec / kurier"
    }
    val bg = if (selected) AccentOrange.copy(alpha = 0.15f) else DarkSurface
    val border = if (selected) AccentOrange else DarkOutlineSoft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = if (selected) AccentOrange else DarkOnSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        }
    }
}

@Composable
private fun GoalRow(goal: DietGoalType, selected: Boolean, onClick: () -> Unit) {
    val (label, desc) = when (goal) {
        DietGoalType.FAT_LOSS -> "Redukcja tłuszczu" to "Deficyt kaloryczny, ochrona mięśni"
        DietGoalType.MUSCLE_GAIN -> "Budowa masy" to "Nadwyżka, więcej węgli post-WO"
        DietGoalType.RECOMP -> "Rekompozycja" to "Stała waga, mniej tłuszczu/więcej mięśni"
        DietGoalType.MAINTAIN -> "Utrzymanie wagi" to "Zero deficytu, balans makro"
        DietGoalType.STRENGTH -> "Poprawa siły" to "Lekka nadwyżka, więcej węgli"
        DietGoalType.ENDURANCE -> "Wydolność" to "Wysokie węgle, balans"
        DietGoalType.HEALTH -> "Zdrowie / cholesterol" to "Niskie tłuszcze nasycone, błonnik"
        DietGoalType.EVENT_PREP -> "Przygotowanie do zawodów" to "Konkretny termin / waga"
    }
    val bg = if (selected) AccentOrange.copy(alpha = 0.15f) else DarkSurface
    val border = if (selected) AccentOrange else DarkOutlineSoft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = if (selected) AccentOrange else DarkOnSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        }
    }
}

private fun labelForPref(p: DietPreference): String = when (p) {
    DietPreference.STANDARD -> "Standard"
    DietPreference.VEGETARIAN -> "Wegetariańska"
    DietPreference.VEGAN -> "Wegańska"
    DietPreference.PESCATARIAN -> "Pescatariańska"
    DietPreference.KETO -> "Keto"
    DietPreference.MEDITERRANEAN -> "Śródziemnomorska"
}

@Composable
private fun wizardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedBorderColor = AccentOrange,
    unfocusedBorderColor = DarkOutline,
    cursorColor = AccentOrange,
    focusedLabelColor = AccentOrange,
    unfocusedLabelColor = DarkOnSurfaceVariant
)
