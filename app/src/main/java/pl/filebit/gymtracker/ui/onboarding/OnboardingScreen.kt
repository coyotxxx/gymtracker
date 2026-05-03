package pl.filebit.gymtracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
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
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.GymPrimaryButton

private const val TOTAL_PAGES = 5

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
            // Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .border(1.dp, AccentOrange.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "GymTracker",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = DarkOnSurface
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
                1 -> GoalExperiencePage(state, vm::setGoal, vm::setExperience, vm::setGender)
                2 -> DaysSessionWeightPage(state, vm::setDaysPerWeek, vm::setSessionMinutes, vm::setBodyweight)
                3 -> WeightGoalPage(state, vm::setWeightGoalType, vm::setTargetWeight)
                4 -> FinishPage(state)
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
    onGender: (Gender) -> Unit
) {
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
        Spacer(Modifier.height(20.dp))
        SectionLabel("Płeć (do standardów siłowych)")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Mężczyzna", state.gender == Gender.MALE, modifier = Modifier.weight(1f)) { onGender(Gender.MALE) }
            ChoicePill("Kobieta", state.gender == Gender.FEMALE, modifier = Modifier.weight(1f)) { onGender(Gender.FEMALE) }
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
