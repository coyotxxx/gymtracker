package pl.filebit.gymtracker.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

/** Stan wizardu — wszystkie pola zapełnione domyślnymi wartościami / pre-fill z UserProfile. */
data class DietOnboardingState(
    val isLoading: Boolean = true,
    val ageYears: Int = 30,
    val heightCm: Int = 175,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: DietGoalType = DietGoalType.MAINTAIN,
    val paceKgPerWeek: Double = 0.0,
    val dietPreference: DietPreference = DietPreference.STANDARD,
    val allergies: Set<String> = emptySet(),
    val dislikedFoods: String = "",
    val lovedFoods: String = "",
    val cookingTimePerMealMin: Int = 15,
    val eatsAtWork: Boolean = false,
    val hasMicrowaveAtWork: Boolean = true,
    val mealPrepInterested: Boolean = false,
    val weeklyBudgetPln: Int? = null,
    val medicalConditions: Set<String> = emptySet(),
    val medicalAwareness: Boolean = false,
    /** Pre-fill z UserProfile — pokazujemy tylko, nie edytujemy. */
    val knownWeightKg: Double? = null,
    val knownGoalLabel: String = "",
    val knownDaysPerWeek: Int = 4
)

@HiltViewModel
class DietOnboardingViewModel @Inject constructor(
    private val dietProfileRepo: UserDietProfileRepository,
    private val userProfileRepo: UserProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DietOnboardingState())
    val state: StateFlow<DietOnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userProfile = userProfileRepo.get()
            val existingDiet = dietProfileRepo.get()
            // Jeśli wizard już raz przeszedł, pre-fillujemy istniejącymi danymi
            _state.value = DietOnboardingState(
                isLoading = false,
                ageYears = existingDiet?.ageYears ?: 30,
                heightCm = existingDiet?.heightCm ?: 175,
                activityLevel = existingDiet?.activityLevel ?: ActivityLevel.MODERATE,
                goalType = existingDiet?.goalType ?: mapGoalFromUser(userProfile),
                paceKgPerWeek = existingDiet?.paceKgPerWeek ?: defaultPaceFor(userProfile),
                dietPreference = existingDiet?.dietPreference ?: DietPreference.STANDARD,
                allergies = existingDiet?.allergies?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                dislikedFoods = existingDiet?.dislikedFoods ?: "",
                lovedFoods = existingDiet?.lovedFoods ?: "",
                cookingTimePerMealMin = existingDiet?.cookingTimePerMealMin ?: 15,
                eatsAtWork = existingDiet?.eatsAtWork ?: false,
                hasMicrowaveAtWork = existingDiet?.hasMicrowaveAtWork ?: true,
                mealPrepInterested = existingDiet?.mealPrepInterested ?: false,
                weeklyBudgetPln = existingDiet?.weeklyBudgetPln,
                medicalConditions = existingDiet?.medicalConditions?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                medicalAwareness = existingDiet?.medicalAwareness ?: false,
                knownWeightKg = userProfile.bodyweightKg,
                knownGoalLabel = labelForUserGoal(userProfile.weightGoalType),
                knownDaysPerWeek = userProfile.daysPerWeek
            )
        }
    }

    fun setAge(v: Int) = _state.update { it.copy(ageYears = v.coerceIn(13, 100)) }
    fun setHeight(v: Int) = _state.update { it.copy(heightCm = v.coerceIn(120, 230)) }
    fun setActivity(a: ActivityLevel) = _state.update { it.copy(activityLevel = a) }
    fun setGoalType(g: DietGoalType) = _state.update { it.copy(goalType = g, paceKgPerWeek = defaultPaceForType(g)) }
    fun setPace(p: Double) = _state.update { it.copy(paceKgPerWeek = p) }
    fun setPreference(p: DietPreference) = _state.update { it.copy(dietPreference = p) }
    fun toggleAllergy(name: String) = _state.update {
        it.copy(allergies = if (name in it.allergies) it.allergies - name else it.allergies + name)
    }
    fun setDisliked(s: String) = _state.update { it.copy(dislikedFoods = s) }
    fun setLoved(s: String) = _state.update { it.copy(lovedFoods = s) }
    fun setCookingTime(min: Int) = _state.update { it.copy(cookingTimePerMealMin = min.coerceIn(5, 60)) }
    fun setEatsAtWork(v: Boolean) = _state.update { it.copy(eatsAtWork = v) }
    fun setMicrowave(v: Boolean) = _state.update { it.copy(hasMicrowaveAtWork = v) }
    fun setMealPrep(v: Boolean) = _state.update { it.copy(mealPrepInterested = v) }
    fun setBudget(v: Int?) = _state.update { it.copy(weeklyBudgetPln = v) }
    fun toggleMedical(name: String) = _state.update {
        it.copy(medicalConditions = if (name in it.medicalConditions) it.medicalConditions - name else it.medicalConditions + name)
    }
    fun setMedicalAwareness(v: Boolean) = _state.update { it.copy(medicalAwareness = v) }

    fun complete(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            dietProfileRepo.upsert(
                UserDietProfile(
                    ageYears = s.ageYears,
                    heightCm = s.heightCm,
                    activityLevel = s.activityLevel,
                    goalType = s.goalType,
                    paceKgPerWeek = s.paceKgPerWeek,
                    dietPreference = s.dietPreference,
                    allergies = s.allergies.joinToString(","),
                    dislikedFoods = s.dislikedFoods,
                    lovedFoods = s.lovedFoods,
                    cookingTimePerMealMin = s.cookingTimePerMealMin,
                    eatsAtWork = s.eatsAtWork,
                    hasMicrowaveAtWork = s.hasMicrowaveAtWork,
                    mealPrepInterested = s.mealPrepInterested,
                    weeklyBudgetPln = s.weeklyBudgetPln,
                    medicalConditions = s.medicalConditions.joinToString(","),
                    medicalAwareness = s.medicalAwareness,
                    onboardingCompletedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            onDone()
        }
    }

    private fun mapGoalFromUser(user: UserProfile): DietGoalType = when (user.weightGoalType) {
        WeightGoalType.CUT -> DietGoalType.FAT_LOSS
        WeightGoalType.BULK -> DietGoalType.MUSCLE_GAIN
        WeightGoalType.MAINTAIN -> DietGoalType.MAINTAIN
        WeightGoalType.NONE -> DietGoalType.MAINTAIN
    }

    // v1.24.24 CRITICAL fix: pace ZAWSZE positive (jak prędkość).
    // Kierunek (deficyt/surplus) determinowany przez goalType w computeDailyGoal.
    // Wcześniej FAT_LOSS dawał -0.5 → computeDailyGoal robił -(-0.5) = surplus +550
    // zamiast -550. User wybierał Redukcja, dostawał surplus. Krytyczny bug.
    private fun defaultPaceFor(user: UserProfile): Double = when (user.weightGoalType) {
        WeightGoalType.CUT -> 0.5
        WeightGoalType.BULK -> 0.3
        WeightGoalType.MAINTAIN -> 0.0
        WeightGoalType.NONE -> 0.0
    }

    private fun defaultPaceForType(g: DietGoalType): Double = when (g) {
        DietGoalType.FAT_LOSS -> 0.5
        DietGoalType.MUSCLE_GAIN -> 0.3
        DietGoalType.RECOMP -> 0.0
        DietGoalType.MAINTAIN -> 0.0
        DietGoalType.STRENGTH -> 0.0   // v1.24.23: strength = maintenance (zmiana z 0.1)
        DietGoalType.ENDURANCE -> 0.0
        DietGoalType.HEALTH -> 0.0
        DietGoalType.EVENT_PREP -> 0.3  // positive (był -0.3) — sign z FAT_LOSS-like logic
    }

    private fun labelForUserGoal(g: WeightGoalType): String = when (g) {
        WeightGoalType.CUT -> "Redukcja"
        WeightGoalType.BULK -> "Masa"
        WeightGoalType.MAINTAIN -> "Utrzymanie"
        WeightGoalType.NONE -> "Brak"
    }
}

private fun MutableStateFlow<DietOnboardingState>.update(transform: (DietOnboardingState) -> DietOnboardingState) {
    value = transform(value)
}
