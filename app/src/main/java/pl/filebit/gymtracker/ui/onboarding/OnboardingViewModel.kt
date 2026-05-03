package pl.filebit.gymtracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.GoalUnit
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.GoalRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class OnboardingUiState(
    val displayName: String = "",
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    val gender: Gender = Gender.MALE,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val bodyweightKg: Double? = null,
    val weightGoalType: WeightGoalType = WeightGoalType.NONE,
    val targetWeightKg: Double? = null,
    val isSaving: Boolean = false,
    val aiKeyConfigured: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepo: UserProfileRepository,
    private val bodyRepo: BodyRepository,
    private val goalRepo: GoalRepository,
    private val aiPrefs: AiPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Prefill — gdy user uruchamia wizard ponownie, ładujemy obecne
            // wartości z profilu zamiast resetować do defaults
            val current = profileRepo.get()
            val cfg = aiPrefs.load()
            _state.update {
                it.copy(
                    displayName = current.displayName,
                    goal = current.goal,
                    experience = current.experience,
                    gender = current.gender,
                    daysPerWeek = current.daysPerWeek,
                    sessionMinutes = current.sessionMinutes,
                    bodyweightKg = current.bodyweightKg,
                    weightGoalType = current.weightGoalType,
                    targetWeightKg = current.targetWeightKg,
                    aiKeyConfigured = cfg.isConnected
                )
            }
        }
    }

    fun setName(s: String) = _state.update { it.copy(displayName = s) }
    fun setGoal(g: TrainingGoal) = _state.update { it.copy(goal = g) }
    fun setExperience(e: ExperienceLevel) = _state.update { it.copy(experience = e) }
    fun setGender(g: Gender) = _state.update { it.copy(gender = g) }
    fun setDaysPerWeek(d: Int) = _state.update { it.copy(daysPerWeek = d.coerceIn(1, 7)) }
    fun setSessionMinutes(m: Int) = _state.update { it.copy(sessionMinutes = m.coerceIn(15, 240)) }
    fun setBodyweight(kg: Double?) = _state.update { it.copy(bodyweightKg = kg) }
    fun setWeightGoalType(t: WeightGoalType) = _state.update {
        it.copy(
            weightGoalType = t,
            // Reset target waga gdy NONE
            targetWeightKg = if (t == WeightGoalType.NONE) null else it.targetWeightKg
        )
    }
    fun setTargetWeight(kg: Double?) = _state.update { it.copy(targetWeightKg = kg) }

    /**
     * Zapisuje profil + ustawia onboardingCompleted = true.
     * Jeśli waga ciała była podana — tworzy też pierwszy BodyMeasurement (dziś).
     * @param onDone wywołuje się po zapisie
     */
    fun complete(onDone: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val current = profileRepo.get()
            val s = _state.value
            profileRepo.save(
                current.copy(
                    displayName = s.displayName.trim(),
                    goal = s.goal,
                    experience = s.experience,
                    gender = s.gender,
                    daysPerWeek = s.daysPerWeek,
                    sessionMinutes = s.sessionMinutes,
                    bodyweightKg = s.bodyweightKg,
                    weightGoalType = s.weightGoalType,
                    targetWeightKg = s.targetWeightKg,
                    onboardingCompleted = true
                )
            )
            // Body measurement — utwórz pomiar dziś jeśli:
            //  (a) brak jakiegokolwiek pomiaru → pierwszy
            //  (b) ostatnia waga różni się o ≥0.5kg od wpisanej w wizardzie → user
            //      najwyraźniej zmienił/skoryguje wagę
            if (s.bodyweightKg != null && s.bodyweightKg > 0) {
                val latest = bodyRepo.getLatest()
                val needsNew = latest == null ||
                    (latest.weightKg != null && Math.abs(latest.weightKg - s.bodyweightKg) >= 0.5)
                if (needsNew) {
                    bodyRepo.upsert(
                        BodyMeasurement(
                            date = System.currentTimeMillis(),
                            weightKg = s.bodyweightKg
                        )
                    )
                }
            }
            // Cele — gdy user wybrał CUT/BULK + docelową wagę, utwórz pierwszy Goal
            // (jeśli jeszcze nie istnieje aktywny Goal tego typu)
            if (s.bodyweightKg != null && s.targetWeightKg != null && s.bodyweightKg > 0) {
                val goalType = when (s.weightGoalType) {
                    WeightGoalType.CUT -> GoalType.LOSE_WEIGHT
                    WeightGoalType.BULK -> GoalType.GAIN_MASS
                    else -> null
                }
                if (goalType != null) {
                    val existing = goalRepo.getActive().firstOrNull { it.type == goalType }
                    if (existing == null) {
                        val now = System.currentTimeMillis()
                        val threeMonths = 90L * 24 * 60 * 60 * 1000
                        val title = if (goalType == GoalType.LOSE_WEIGHT)
                            "Schudnąć do ${s.targetWeightKg} kg"
                        else
                            "Przybrać do ${s.targetWeightKg} kg"
                        goalRepo.upsert(
                            Goal(
                                type = goalType,
                                title = title,
                                description = "Cel utworzony automatycznie z kreatora",
                                unit = GoalUnit.KG,
                                startValue = s.bodyweightKg,
                                targetValue = s.targetWeightKg,
                                startDate = now,
                                deadline = now + threeMonths
                            )
                        )
                    }
                }
            }
            _state.update { it.copy(isSaving = false) }
            onDone()
        }
    }

    /** Skip — zapisuje tylko flagę onboardingCompleted, reszta defaults. */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = profileRepo.get()
            profileRepo.save(current.copy(onboardingCompleted = true))
            onDone()
        }
    }
}
