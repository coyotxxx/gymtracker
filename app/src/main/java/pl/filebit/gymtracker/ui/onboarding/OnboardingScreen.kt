package pl.filebit.gymtracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import pl.filebit.gymtracker.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton

private const val TOTAL_PAGES = 9

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    onGenerateAiPlan: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header — to samo logo co na splash startowym
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.splash_logo),
                contentDescription = "GymTracker",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(800f / 538f)
            )
            // Kompensata pustego pola pyłu wokół G — tytuł "siada" pod literą
            Spacer(Modifier.height(0.dp))
            Text(
                "GymTracker",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = DarkOnSurface,
                modifier = Modifier.offset(y = (-20).dp)
            )
            Spacer(Modifier.height(4.dp))
            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(TOTAL_PAGES) { idx ->
                    Box(
                        modifier = Modifier
                            .size(width = if (idx == page) 24.dp else 8.dp, height = 8.dp)
                            .background(
                                if (idx == page) AccentOrange else DarkSurface,
                                RoundedCornerShape(50)
                            )
                            .border(1.dp, DarkOutlineSoft, RoundedCornerShape(50))
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            when (page) {
                0 -> WelcomePage(state, vm::setName)
                1 -> AgeHeightGenderPage(state, vm::setGender, vm::setAge, vm::setHeight)
                2 -> GoalExperiencePage(state, vm::setGoal, vm::setExperience, vm::setGender)
                3 -> DaysSessionWeightPage(state, vm::setDaysPerWeek, vm::setSessionMinutes, vm::setBodyweight)
                4 -> WeightGoalPage(state, vm::setWeightGoalType, vm::setTargetWeight)
                5 -> EquipmentPage(state, vm::setEquipment)
                6 -> ActivityLevelPage(state, vm::setActivityLevel)
                7 -> DietProfilePage(
                    state = state,
                    onSetWantsDietProfile = vm::setWantsDietProfile,
                    onSetDietPreference = vm::setDietPreference,
                    onSetAllergies = vm::setAllergies,
                    onSetIntolerances = vm::setIntolerances,
                    onSetWeeklyBudget = vm::setWeeklyBudget,
                    onSetMedicalConditions = vm::setMedicalConditions,
                    onSetCookingTime = vm::setCookingTimePerMeal,
                    onSetLovedFoods = vm::setLovedFoods,
                    onSetDislikedFoods = vm::setDislikedFoods
                )
                8 -> FinishPage(state)
            }

            Spacer(Modifier.height(32.dp))

            // Nawigacja
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page < TOTAL_PAGES - 1) {
                    GymPrimaryButton(
                        onClick = { page++ },
                        text = "Dalej"
                    )
                } else {
                    // Ostatnia strona — różne akcje zależnie od klucza AI
                    if (state.aiKeyConfigured) {
                        GymPrimaryButton(
                            onClick = {
                                vm.complete {
                                    onGenerateAiPlan()
                                }
                            },
                            text = if (state.isSaving) "Zapisuję…" else "🪄 Stwórz pierwszy plan AI",
                            leadingIcon = Icons.Default.AutoAwesome,
                            enabled = !state.isSaving
                        )
                        TextButton(
                            onClick = { vm.complete(onCompleted) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) {
                            Text("Pomiń — sam dodam plan", color = DarkOnSurfaceVariant)
                        }
                    } else {
                        GymPrimaryButton(
                            onClick = { vm.complete(onCompleted) },
                            text = if (state.isSaving) "Zapisuję…" else "Zaczynamy!",
                            leadingIcon = Icons.Default.AutoAwesome,
                            enabled = !state.isSaving
                        )
                        TextButton(
                            onClick = {
                                vm.complete { onOpenAiSettings() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) {
                            Text("Wpisz klucz AI by trener Cię wsparł", color = AccentOrange)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (page > 0) {
                        TextButton(onClick = { page-- }) {
                            Text("Wstecz", color = DarkOnSurfaceVariant)
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    TextButton(onClick = { vm.skip(onCompleted) }) {
                        Text("Pomiń wizard", color = DarkOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Cześć!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "GymTracker to aplikacja, która towarzyszy Ci na siłowni. Trenujesz, zapisujesz wyniki, " +
                "a AI Trener pomaga Ci się rozwijać. Zacznijmy od kilku pytań, żebyśmy Cię lepiej poznali.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onNameChange,
            label = { Text("Jak Ci na imię? (opcjonalnie)", color = DarkOnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun GoalExperiencePage(
    state: OnboardingUiState,
    onGoal: (TrainingGoal) -> Unit,
    onExperience: (ExperienceLevel) -> Unit,
    @Suppress("UNUSED_PARAMETER") onGender: (Gender) -> Unit
) {
    // v1.24.11: usunięto blok "Płeć (do standardów siłowych)" — duplikat pytania
    // z AgeHeightGenderPage. state.gender to pojedyncze pole singletonu, więc
    // pytanie 2× łamało zasadę "jeden user, jedno źródło prawdy" i myliło usera.
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Twój cel treningowy")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoicePill("Masa mięśniowa (hipertrofia)", state.goal == TrainingGoal.HYPERTROPHY) { onGoal(TrainingGoal.HYPERTROPHY) }
            ChoicePill("Siła", state.goal == TrainingGoal.STRENGTH) { onGoal(TrainingGoal.STRENGTH) }
            ChoicePill("Siła + masa", state.goal == TrainingGoal.MIX) { onGoal(TrainingGoal.MIX) }
            ChoicePill("Cardio + siłownia", state.goal == TrainingGoal.CARDIO_LIFTING) { onGoal(TrainingGoal.CARDIO_LIFTING) }
            ChoicePill("Sprawność ogólna", state.goal == TrainingGoal.GENERAL_FITNESS) { onGoal(TrainingGoal.GENERAL_FITNESS) }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("Twoje doświadczenie")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoicePill("Początkujący (< 1 rok)", state.experience == ExperienceLevel.BEGINNER) { onExperience(ExperienceLevel.BEGINNER) }
            ChoicePill("Średniozaawansowany (1-3 lata)", state.experience == ExperienceLevel.INTERMEDIATE) { onExperience(ExperienceLevel.INTERMEDIATE) }
            ChoicePill("Zaawansowany (3+ lat)", state.experience == ExperienceLevel.ADVANCED) { onExperience(ExperienceLevel.ADVANCED) }
        }
    }
}

@Composable
private fun DaysSessionWeightPage(
    state: OnboardingUiState,
    onDays: (Int) -> Unit,
    onSession: (Int) -> Unit,
    onWeight: (Double?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Ile dni w tygodniu chcesz trenować?")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (2..6).forEach { d ->
                NumberPill(
                    text = "$d",
                    selected = state.daysPerWeek == d,
                    modifier = Modifier.weight(1f),
                    onClick = { onDays(d) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Czas pojedynczej sesji (minuty)")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(30, 45, 60, 75, 90).forEach { m ->
                NumberPill(
                    text = "$m",
                    selected = state.sessionMinutes == m,
                    modifier = Modifier.weight(1f),
                    onClick = { onSession(m) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Waga ciała (kg, opcjonalnie)")
        Spacer(Modifier.height(4.dp))
        Text(
            "Pomaga w obliczaniu poziomów siły (np. ławka 1.25× wagi ciała)",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        var weightText by remember { mutableStateOf(state.bodyweightKg?.toString() ?: "") }
        OutlinedTextField(
            value = weightText,
            onValueChange = { v ->
                weightText = v.filter { it.isDigit() || it == '.' || it == ',' }
                onWeight(weightText.replace(',', '.').toDoubleOrNull())
            },
            label = { Text("np. 75", color = DarkOnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
private fun WeightGoalPage(
    state: OnboardingUiState,
    onWeightGoalType: (WeightGoalType) -> Unit,
    onTargetWeight: (Double?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Co chcesz osiągnąć z wagą ciała?")
        Spacer(Modifier.height(4.dp))
        Text(
            "To inny cel niż treningowy — tu chodzi o redukcję / przyrost masy ciała.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoicePill("Bez konkretnego celu", state.weightGoalType == WeightGoalType.NONE) { onWeightGoalType(WeightGoalType.NONE) }
            ChoicePill("Redukcja (chcę schudnąć)", state.weightGoalType == WeightGoalType.CUT) { onWeightGoalType(WeightGoalType.CUT) }
            ChoicePill("Masa (chcę przybrać)", state.weightGoalType == WeightGoalType.BULK) { onWeightGoalType(WeightGoalType.BULK) }
            ChoicePill("Utrzymanie", state.weightGoalType == WeightGoalType.MAINTAIN) { onWeightGoalType(WeightGoalType.MAINTAIN) }
        }

        if (state.weightGoalType == WeightGoalType.CUT || state.weightGoalType == WeightGoalType.BULK) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Docelowa waga (kg)")
            Spacer(Modifier.height(8.dp))
            var targetText by remember(state.weightGoalType) {
                mutableStateOf(state.targetWeightKg?.toString() ?: "")
            }
            OutlinedTextField(
                value = targetText,
                onValueChange = { v ->
                    targetText = v.filter { it.isDigit() || it == '.' || it == ',' }
                    onTargetWeight(targetText.replace(',', '.').toDoubleOrNull())
                },
                label = { Text("np. ${if (state.weightGoalType == WeightGoalType.CUT) "75" else "85"}", color = DarkOnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

@Composable
private fun FinishPage(state: OnboardingUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Wszystko gotowe!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentOrange.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentOrange.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryRow("Cel treningu", goalLabel(state.goal))
                SummaryRow("Doświadczenie", experienceLabel(state.experience))
                SummaryRow("Płeć", if (state.gender == Gender.MALE) "Mężczyzna" else "Kobieta")
                SummaryRow("Dni / tydzień", state.daysPerWeek.toString())
                SummaryRow("Czas sesji", "${state.sessionMinutes} min")
                state.bodyweightKg?.let { SummaryRow("Waga", "$it kg") }
                if (state.weightGoalType != WeightGoalType.NONE) {
                    val target = state.targetWeightKg?.let { " → $it kg" } ?: ""
                    SummaryRow("Cel wagi", weightGoalLabel(state.weightGoalType) + target)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.aiKeyConfigured) {
            Text(
                "Masz wpisany klucz AI. Klik 'Stwórz pierwszy plan AI' i trener przygotuje plan pasujący do " +
                    "Twojej konfiguracji + utworzy pierwszy pomiar wagi.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
        } else {
            Text(
                "Po starcie znajdziesz w Profilu Asystenta AI — wpisz klucz API (OpenAI / Anthropic) " +
                    "by trener AI ułożył pierwszy plan dla Ciebie.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = AccentOrange
    )
}

@Composable
private fun ChoicePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (selected) AccentOrange.copy(alpha = 0.5f) else DarkOutlineSoft,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) AccentOrange else DarkOnSurface
        )
    }
}

@Composable
private fun NumberPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                if (selected) AccentOrange.copy(alpha = 0.20f) else DarkSurface,
                RoundedCornerShape(12.dp)
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) AccentOrange else DarkOutlineSoft,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = if (selected) AccentOrange else DarkOnSurface
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = DarkOnSurface
        )
    }
}

private fun goalLabel(g: TrainingGoal): String = when (g) {
    TrainingGoal.HYPERTROPHY -> "Masa mięśniowa"
    TrainingGoal.STRENGTH -> "Siła"
    TrainingGoal.MIX -> "Siła + masa"
    TrainingGoal.CARDIO_LIFTING -> "Cardio + siłownia"
    TrainingGoal.GENERAL_FITNESS -> "Sprawność ogólna"
}

private fun experienceLabel(e: ExperienceLevel): String = when (e) {
    ExperienceLevel.BEGINNER -> "Początkujący"
    ExperienceLevel.INTERMEDIATE -> "Średniozaawansowany"
    ExperienceLevel.ADVANCED -> "Zaawansowany"
}

private fun weightGoalLabel(g: WeightGoalType): String = when (g) {
    WeightGoalType.NONE -> "Bez celu"
    WeightGoalType.CUT -> "Redukcja"
    WeightGoalType.BULK -> "Masa"
    WeightGoalType.MAINTAIN -> "Utrzymanie"
}

// =============================================================
// === NOWE STRONY WIZARDA v1.0.18 ===
// =============================================================

@Composable
private fun AgeHeightGenderPage(
    state: OnboardingUiState,
    onGenderChange: (pl.filebit.gymtracker.data.entity.Gender) -> Unit,
    onAgeChange: (Int?) -> Unit,
    onHeightChange: (Int?) -> Unit
) {
    StepCard(title = "Twoje dane", subtitle = "Potrzebne do dokładnego obliczenia zapotrzebowania kalorycznego (BMR)") {
        // Płeć
        Text("Płeć", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChipRow(
                selected = state.gender == pl.filebit.gymtracker.data.entity.Gender.MALE,
                text = "♂ Mężczyzna",
                onClick = { onGenderChange(pl.filebit.gymtracker.data.entity.Gender.MALE) },
                modifier = Modifier.weight(1f)
            )
            ChoiceChipRow(
                selected = state.gender == pl.filebit.gymtracker.data.entity.Gender.FEMALE,
                text = "♀ Kobieta",
                onClick = { onGenderChange(pl.filebit.gymtracker.data.entity.Gender.FEMALE) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        // Wiek — TextField pamięta lokalny tekst (żeby user mógł kasować/wpisywać bez doklejania się)
        var ageText by remember(state.ageYears) {
            androidx.compose.runtime.mutableStateOf(state.ageYears?.toString() ?: "")
        }
        OutlinedTextField(
            value = ageText,
            onValueChange = { v ->
                // Tylko cyfry, max 3 znaki
                val cleaned = v.filter { it.isDigit() }.take(3)
                ageText = cleaned
                onAgeChange(cleaned.toIntOrNull())
            },
            label = { Text("Wiek (lata)") },
            placeholder = { Text("np. 30", color = DarkOnSurfaceVariant) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = onboardingTextFieldColors(),
            isError = state.ageYears != null && (state.ageYears < 13 || state.ageYears > 90),
            supportingText = {
                val a = state.ageYears
                if (a != null && (a < 13 || a > 90)) {
                    Text("Wiek powinien być w zakresie 13-90 lat", color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // Wzrost — analogicznie
        var heightText by remember(state.heightCm) {
            androidx.compose.runtime.mutableStateOf(state.heightCm?.toString() ?: "")
        }
        OutlinedTextField(
            value = heightText,
            onValueChange = { v ->
                val cleaned = v.filter { it.isDigit() }.take(3)
                heightText = cleaned
                onHeightChange(cleaned.toIntOrNull())
            },
            label = { Text("Wzrost (cm)") },
            placeholder = { Text("np. 175", color = DarkOnSurfaceVariant) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = onboardingTextFieldColors(),
            isError = state.heightCm != null && (state.heightCm < 140 || state.heightCm > 220),
            supportingText = {
                val h = state.heightCm
                if (h != null && (h < 140 || h > 220)) {
                    Text("Wzrost powinien być w zakresie 140-220 cm", color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        )
    }
}

@Composable
private fun EquipmentPage(
    state: OnboardingUiState,
    onChange: (String) -> Unit
) {
    val selected = remember(state.availableEquipmentCsv) {
        state.availableEquipmentCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }
    val all = pl.filebit.gymtracker.data.entity.Equipment.values()
        .filter { it != pl.filebit.gymtracker.data.entity.Equipment.OTHER }

    StepCard(title = "Twój sprzęt", subtitle = "AI ułoży plan z ćwiczeń tylko na sprzęcie który masz. Pusty wybór = pełna siłownia.") {
        // Quick presety
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickPresetChip(
                label = "🏠 Dom (sztanga + sztangielki)",
                onClick = { onChange("BARBELL,DUMBBELLS,BODYWEIGHT,CABLE") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickPresetChip(
                label = "💪 Tylko masa ciała",
                onClick = { onChange("BODYWEIGHT") },
                modifier = Modifier.weight(1f)
            )
            QuickPresetChip(
                label = "🏋 Pełna siłownia",
                onClick = { onChange("") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text("Lub zaznacz pojedynczo:", style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val pairs = all.chunked(2)
        for (pair in pairs) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { eq ->
                    val isSel = eq.name in selected
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, if (isSel) AccentOrange else DarkOutlineSoft, RoundedCornerShape(8.dp))
                            .clickable {
                                val newSet = if (isSel) selected - eq.name else selected + eq.name
                                onChange(newSet.joinToString(","))
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "${if (isSel) "✓" else "○"} ${equipmentDisplayName(eq)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSel) AccentOrange else DarkOnSurface
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActivityLevelPage(
    state: OnboardingUiState,
    onChange: (pl.filebit.gymtracker.data.entity.ActivityLevel) -> Unit
) {
    StepCard(title = "Aktywność POZA treningiem", subtitle = "Praca, codzienne życie, chodzenie. Bez treningów na siłowni.") {
        val all = pl.filebit.gymtracker.data.entity.ActivityLevel.values()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { lvl ->
                val isSel = state.activityLevel == lvl
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, if (isSel) AccentOrange else DarkOutlineSoft, RoundedCornerShape(10.dp))
                        .clickable { onChange(lvl) }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            activityLevelTitle(lvl),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSel) AccentOrange else DarkOnSurface
                        )
                        Text(
                            activityLevelDesc(lvl),
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietProfilePage(
    state: OnboardingUiState,
    onSetWantsDietProfile: (Boolean) -> Unit,
    onSetDietPreference: (pl.filebit.gymtracker.data.entity.DietPreference) -> Unit,
    onSetAllergies: (String) -> Unit,
    onSetIntolerances: (String) -> Unit,
    onSetWeeklyBudget: (Int?) -> Unit,
    onSetMedicalConditions: (String) -> Unit,
    onSetCookingTime: (Int) -> Unit,
    onSetLovedFoods: (String) -> Unit,
    onSetDislikedFoods: (String) -> Unit
) {
    StepCard(title = "Profil dietetyczny", subtitle = "Pomijalne — uzupełnisz później w 'Dieta → Profil'.") {
        // Toggle "Chcę wypełnić teraz"
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Switch(
                checked = state.wantsDietProfile,
                onCheckedChange = onSetWantsDietProfile
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Wypełnij teraz", fontWeight = FontWeight.SemiBold, color = DarkOnSurface)
                Text(
                    "AI lepiej dopasuje dietę gdy zna alergie, preferencje, choroby",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }

        if (state.wantsDietProfile) {
            Spacer(Modifier.height(16.dp))

            // Preferencja dietetyczna
            Text("Preferencja dietetyczna:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
            Spacer(Modifier.height(6.dp))
            val prefs = pl.filebit.gymtracker.data.entity.DietPreference.values()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                prefs.forEach { p ->
                    val isSel = state.dietPreference == p
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, if (isSel) AccentOrange else DarkOutlineSoft, RoundedCornerShape(8.dp))
                            .clickable { onSetDietPreference(p) }
                            .padding(10.dp)
                    ) {
                        Text(
                            dietPreferenceLabelForOnboarding(p),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isSel) AccentOrange else DarkOnSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Alergie (CSV — dropdown z popularnymi)
            Text("Alergie / nietolerancje:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
            Spacer(Modifier.height(6.dp))
            val commonAllergies = listOf("laktoza", "gluten", "jaja", "orzechy", "soja", "ryby", "owoce_morza", "sezam", "gorczyca")
            val selectedAllergies = remember(state.allergiesCsv) {
                state.allergiesCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                commonAllergies.forEach { allergen ->
                    val isSel = allergen in selectedAllergies
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .background(
                                if (isSel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                                RoundedCornerShape(50)
                            )
                            .border(1.dp, if (isSel) AccentOrange else DarkOutlineSoft, RoundedCornerShape(50))
                            .clickable {
                                val newSet = if (isSel) selectedAllergies - allergen else selectedAllergies + allergen
                                onSetAllergies(newSet.joinToString(","))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${if (isSel) "✓ " else ""}$allergen",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSel) AccentOrange else DarkOnSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Lubione produkty (CSV)
            OutlinedTextField(
                value = state.lovedFoodsCsv,
                onValueChange = onSetLovedFoods,
                label = { Text("Lubię (CSV) np. kurczak, twaróg, ryż") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = onboardingTextFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            // Nielubione
            OutlinedTextField(
                value = state.dislikedFoodsCsv,
                onValueChange = onSetDislikedFoods,
                label = { Text("Nie lubię (CSV) np. brokuły, tofu") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = onboardingTextFieldColors()
            )

            Spacer(Modifier.height(14.dp))

            // Budżet tygodniowy — tekst lokalny żeby user mógł czyścić
            var budgetText by remember(state.weeklyBudgetPln) {
                androidx.compose.runtime.mutableStateOf(state.weeklyBudgetPln?.toString() ?: "")
            }
            OutlinedTextField(
                value = budgetText,
                onValueChange = { v ->
                    val cleaned = v.filter { it.isDigit() }.take(4)
                    budgetText = cleaned
                    onSetWeeklyBudget(cleaned.toIntOrNull())
                },
                label = { Text("Budżet tygodniowy (zł, opcjonalnie)") },
                placeholder = { Text("np. 250", color = DarkOnSurfaceVariant) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = onboardingTextFieldColors()
            )

            Spacer(Modifier.height(8.dp))

            // Czas gotowania
            var cookText by remember(state.cookingTimePerMealMin) {
                androidx.compose.runtime.mutableStateOf(
                    if (state.cookingTimePerMealMin > 0) state.cookingTimePerMealMin.toString() else ""
                )
            }
            OutlinedTextField(
                value = cookText,
                onValueChange = { v ->
                    val cleaned = v.filter { it.isDigit() }.take(2)
                    cookText = cleaned
                    cleaned.toIntOrNull()?.let(onSetCookingTime)
                },
                label = { Text("Max czas gotowania 1 posiłku (min)") },
                placeholder = { Text("np. 15", color = DarkOnSurfaceVariant) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = onboardingTextFieldColors()
            )

            Spacer(Modifier.height(14.dp))

            // Choroby (medical flags)
            Text("Stany zdrowia (opcjonalnie):", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = DarkOnSurface)
            Text(
                "AI dostosuje plan zachowując konserwatywne podejście.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val medical = listOf(
                "cukrzyca" to "Cukrzyca / IR",
                "nadciśnienie" to "Nadciśnienie",
                "choroby_nerek" to "Choroby nerek",
                "ciąża" to "Ciąża",
                "karmienie_piersią" to "Karmienie piersią",
                "zaburzenia_odżywiania" to "Zaburzenia odżywiania"
            )
            val selectedMedical = remember(state.medicalConditionsCsv) {
                state.medicalConditionsCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                medical.forEach { (key, label) ->
                    val isSel = key in selectedMedical
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .background(
                                if (isSel) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                                RoundedCornerShape(50)
                            )
                            .border(1.dp, if (isSel) AccentOrange else DarkOutlineSoft, RoundedCornerShape(50))
                            .clickable {
                                val newSet = if (isSel) selectedMedical - key else selectedMedical + key
                                onSetMedicalConditions(newSet.joinToString(","))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${if (isSel) "✓ " else ""}$label",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSel) AccentOrange else DarkOnSurface
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "✓ Pomijasz profil dietetyczny — uzupełnisz później w 'Dieta → Profil dietetyczny'.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

// === Helpers ===

@Composable
private fun ChoiceChipRow(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f) else DarkSurface,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, if (selected) AccentOrange else DarkOutlineSoft, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) AccentOrange else DarkOnSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(SuccessGreen.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = SuccessGreen,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun onboardingTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedBorderColor = AccentOrange,
    unfocusedBorderColor = DarkOutlineSoft,
    cursorColor = AccentOrange,
    focusedLabelColor = AccentOrange,
    unfocusedLabelColor = DarkOnSurfaceVariant
)

private fun equipmentDisplayName(e: pl.filebit.gymtracker.data.entity.Equipment): String = when (e) {
    pl.filebit.gymtracker.data.entity.Equipment.BARBELL -> "Sztanga olimpijska"
    pl.filebit.gymtracker.data.entity.Equipment.DUMBBELLS -> "Sztangielki / hantle"
    pl.filebit.gymtracker.data.entity.Equipment.MACHINE -> "Maszyny siłowe"
    pl.filebit.gymtracker.data.entity.Equipment.CABLE -> "Wyciąg / linki"
    pl.filebit.gymtracker.data.entity.Equipment.BODYWEIGHT -> "Masa ciała / drążek"
    pl.filebit.gymtracker.data.entity.Equipment.OTHER -> "Inne"
}

private fun activityLevelTitle(l: pl.filebit.gymtracker.data.entity.ActivityLevel): String = when (l) {
    pl.filebit.gymtracker.data.entity.ActivityLevel.SEDENTARY -> "Siedzący tryb (×1.2)"
    pl.filebit.gymtracker.data.entity.ActivityLevel.LIGHT -> "Lekko aktywny (×1.375)"
    pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE -> "Umiarkowanie aktywny (×1.55)"
    pl.filebit.gymtracker.data.entity.ActivityLevel.VERY_ACTIVE -> "Bardzo aktywny (×1.725)"
    pl.filebit.gymtracker.data.entity.ActivityLevel.EXTREME -> "Ekstremalnie aktywny (×1.9)"
}

private fun activityLevelDesc(l: pl.filebit.gymtracker.data.entity.ActivityLevel): String = when (l) {
    pl.filebit.gymtracker.data.entity.ActivityLevel.SEDENTARY -> "Praca biurowa, mało ruchu poza treningiem"
    pl.filebit.gymtracker.data.entity.ActivityLevel.LIGHT -> "Lekkie chodzenie, drobne prace, sporadyczna aktywność"
    pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE -> "3-5 treningów + sporo chodzenia / praca aktywna"
    pl.filebit.gymtracker.data.entity.ActivityLevel.VERY_ACTIVE -> "6-7 treningów + ciężka praca fizyczna"
    pl.filebit.gymtracker.data.entity.ActivityLevel.EXTREME -> "Sportowiec wyczynowy, wielogodzinne treningi"
}

private fun dietPreferenceLabelForOnboarding(p: pl.filebit.gymtracker.data.entity.DietPreference): String = when (p) {
    pl.filebit.gymtracker.data.entity.DietPreference.STANDARD -> "Standardowa (mięso + nabiał + warzywa)"
    pl.filebit.gymtracker.data.entity.DietPreference.VEGETARIAN -> "Wegetariańska (bez mięsa, nabiał+jaja OK)"
    pl.filebit.gymtracker.data.entity.DietPreference.VEGAN -> "Wegańska (bez produktów odzwierzęcych)"
    pl.filebit.gymtracker.data.entity.DietPreference.PESCATARIAN -> "Pescatariańska (ryby + nabiał, bez mięsa)"
    pl.filebit.gymtracker.data.entity.DietPreference.KETO -> "Keto (max 30g węgli/dzień, dużo tłuszczu)"
    pl.filebit.gymtracker.data.entity.DietPreference.MEDITERRANEAN -> "Śródziemnomorska (oliwa, ryby, warzywa)"
}

@Composable
private fun StepCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = DarkOnSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}
