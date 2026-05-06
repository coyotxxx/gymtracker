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
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.GoalUnit
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.GoalRepository
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

data class OnboardingUiState(
    // === KROK 0: Welcome / imię ===
    val displayName: String = "",
    // === KROK 1: Płeć + wiek + wzrost (do dokładnego BMR Mifflin-St Jeor) ===
    val gender: Gender = Gender.MALE,
    val ageYears: Int? = null,
    val heightCm: Int? = null,
    // === KROK 2: Cel treningowy + doświadczenie ===
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    // === KROK 3: Dni/tydz + czas sesji ===
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    // === KROK 4: Aktualna waga + cel wagowy + waga docelowa ===
    val bodyweightKg: Double? = null,
    val weightGoalType: WeightGoalType = WeightGoalType.NONE,
    val targetWeightKg: Double? = null,
    // === KROK 5: Sprzęt ===
    val availableEquipmentCsv: String = "",
    // === KROK 6: Aktywność poza treningiem ===
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    // === KROK 7: Dieta — opcjonalna ===
    val wantsDietProfile: Boolean = true,                       // user może pominąć
    val dietPreference: DietPreference = DietPreference.STANDARD,
    val allergiesCsv: String = "",                              // CSV: laktoza,gluten,...
    val intolerances: String = "",
    val dislikedFoodsCsv: String = "",
    val lovedFoodsCsv: String = "",
    val weeklyBudgetPln: Int? = null,
    val medicalConditionsCsv: String = "",
    val cookingTimePerMealMin: Int = 15,

    val isSaving: Boolean = false,
    val aiKeyConfigured: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val bodyRepo: BodyRepository,
    private val goalRepo: GoalRepository,
    private val aiPrefs: AiPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Prefill — gdy user uruchamia wizard ponownie, ładujemy obecne wartości
            val current = profileRepo.get()
            val dietProfile = runCatching { dietProfileRepo.get() }.getOrNull()
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
                    availableEquipmentCsv = current.availableEquipmentCsv,
                    // Prefill TYLKO jeśli user już wcześniej wypełnił — pusty stan
                    // pozwala wpisać wartość w czystym polu (nie dokleja się do "30")
                    ageYears = dietProfile?.ageYears?.takeIf { it > 0 },
                    heightCm = dietProfile?.heightCm?.takeIf { it > 0 },
                    activityLevel = dietProfile?.activityLevel ?: ActivityLevel.MODERATE,
                    dietPreference = dietProfile?.dietPreference ?: DietPreference.STANDARD,
                    allergiesCsv = dietProfile?.allergies ?: "",
                    intolerances = dietProfile?.intolerances ?: "",
                    dislikedFoodsCsv = dietProfile?.dislikedFoods ?: "",
                    lovedFoodsCsv = dietProfile?.lovedFoods ?: "",
                    weeklyBudgetPln = dietProfile?.weeklyBudgetPln,
                    medicalConditionsCsv = dietProfile?.medicalConditions ?: "",
                    cookingTimePerMealMin = dietProfile?.cookingTimePerMealMin ?: 15,
                    aiKeyConfigured = cfg.isConnected
                )
            }
        }
    }

    fun setName(s: String) = _state.update { it.copy(displayName = s) }
    fun setGoal(g: TrainingGoal) = _state.update { it.copy(goal = g) }
    fun setExperience(e: ExperienceLevel) = _state.update { it.copy(experience = e) }
    fun setGender(g: Gender) = _state.update { it.copy(gender = g) }
    fun setAge(years: Int?) = _state.update { it.copy(ageYears = years) }
    fun setHeight(cm: Int?) = _state.update { it.copy(heightCm = cm) }
    fun setDaysPerWeek(d: Int) = _state.update { it.copy(daysPerWeek = d.coerceIn(1, 7)) }
    fun setSessionMinutes(m: Int) = _state.update { it.copy(sessionMinutes = m.coerceIn(15, 240)) }
    fun setBodyweight(kg: Double?) = _state.update { it.copy(bodyweightKg = kg) }
    fun setWeightGoalType(t: WeightGoalType) = _state.update {
        it.copy(
            weightGoalType = t,
            targetWeightKg = if (t == WeightGoalType.NONE) null else it.targetWeightKg
        )
    }
    fun setTargetWeight(kg: Double?) = _state.update { it.copy(targetWeightKg = kg) }
    fun setEquipment(csv: String) = _state.update { it.copy(availableEquipmentCsv = csv) }
    fun setActivityLevel(level: ActivityLevel) = _state.update { it.copy(activityLevel = level) }
    fun setWantsDietProfile(v: Boolean) = _state.update { it.copy(wantsDietProfile = v) }
    fun setDietPreference(p: DietPreference) = _state.update { it.copy(dietPreference = p) }
    fun setAllergies(csv: String) = _state.update { it.copy(allergiesCsv = csv) }
    fun setIntolerances(s: String) = _state.update { it.copy(intolerances = s) }
    fun setDislikedFoods(csv: String) = _state.update { it.copy(dislikedFoodsCsv = csv) }
    fun setLovedFoods(csv: String) = _state.update { it.copy(lovedFoodsCsv = csv) }
    fun setWeeklyBudget(pln: Int?) = _state.update { it.copy(weeklyBudgetPln = pln) }
    fun setMedicalConditions(csv: String) = _state.update { it.copy(medicalConditionsCsv = csv) }
    fun setCookingTimePerMeal(min: Int) = _state.update { it.copy(cookingTimePerMealMin = min.coerceIn(5, 60)) }

    /**
     * Zapisuje profil + ustawia onboardingCompleted = true.
     * Jeśli waga ciała była podana — tworzy też pierwszy BodyMeasurement (dziś).
     * Jeśli wantsDietProfile=true — zapisuje UserDietProfile.
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
                    availableEquipmentCsv = s.availableEquipmentCsv,
                    onboardingCompleted = true
                )
            )

            // Body measurement
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

            // UserDietProfile — zapisuj tylko jeśli user wypełnił sekcję dietetyczną
            // Wiek/wzrost ZAWSZE zapisujemy (są niezbędne do TDEE)
            val existingDiet = runCatching { dietProfileRepo.get() }.getOrNull()
            val dietGoalType = when (s.weightGoalType) {
                WeightGoalType.CUT -> DietGoalType.FAT_LOSS
                WeightGoalType.BULK -> DietGoalType.MUSCLE_GAIN
                WeightGoalType.MAINTAIN -> DietGoalType.MAINTAIN
                WeightGoalType.NONE -> DietGoalType.MAINTAIN
            }
            val newDietProfile = (existingDiet ?: UserDietProfile()).copy(
                ageYears = s.ageYears?.coerceIn(13, 90) ?: 30,
                heightCm = s.heightCm?.coerceIn(140, 220) ?: 175,
                activityLevel = s.activityLevel,
                goalType = dietGoalType,
                dietPreference = if (s.wantsDietProfile) s.dietPreference else (existingDiet?.dietPreference ?: DietPreference.STANDARD),
                allergies = if (s.wantsDietProfile) s.allergiesCsv else (existingDiet?.allergies ?: ""),
                intolerances = if (s.wantsDietProfile) s.intolerances else (existingDiet?.intolerances ?: ""),
                dislikedFoods = if (s.wantsDietProfile) s.dislikedFoodsCsv else (existingDiet?.dislikedFoods ?: ""),
                lovedFoods = if (s.wantsDietProfile) s.lovedFoodsCsv else (existingDiet?.lovedFoods ?: ""),
                weeklyBudgetPln = if (s.wantsDietProfile) s.weeklyBudgetPln else existingDiet?.weeklyBudgetPln,
                medicalConditions = if (s.wantsDietProfile) s.medicalConditionsCsv else (existingDiet?.medicalConditions ?: ""),
                cookingTimePerMealMin = if (s.wantsDietProfile) s.cookingTimePerMealMin else (existingDiet?.cookingTimePerMealMin ?: 15),
                onboardingCompletedAt = if (s.wantsDietProfile) System.currentTimeMillis() else existingDiet?.onboardingCompletedAt,
                updatedAt = System.currentTimeMillis()
            )
            runCatching { dietProfileRepo.save(newDietProfile) }

            // Cele
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
