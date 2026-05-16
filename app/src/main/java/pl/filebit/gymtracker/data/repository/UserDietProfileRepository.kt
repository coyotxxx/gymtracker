package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.28 (refaktor "jedno źródło prawdy", Etap 1) — patrz docs/CONFIG-UNIFICATION-PLAN.md.
 *
 * Po scaleniu encji `UserProfile` + `UserDietProfile` w jedną tabelę `user_profile`
 * to repozytorium NIE używa już własnego DAO/tabeli. Jest cienką nakładką nad
 * [UserProfileRepository]: czyta/zapisuje pola diety scalonej encji, mapując je
 * w obie strony na DTO [UserDietProfile]. API repozytorium pozostaje identyczne,
 * więc ~11 konsumentów (DietViewModel, DietAiService, AutoAdjustmentService itd.)
 * działa bez żadnych zmian.
 */
@Singleton
class UserDietProfileRepository @Inject constructor(
    private val userProfileRepo: UserProfileRepository
) {
    fun observe(): Flow<UserDietProfile?> =
        userProfileRepo.observe().map { it.toDietProfile() }

    /**
     * Zwraca pola diety scalonego profilu. Po scaleniu profil ZAWSZE istnieje
     * (UserProfileRepository tworzy wiersz leniwie) — `null` w praktyce nie wystąpi.
     * Sygnał "onboarding diety ukończony" sprawdzaj przez [isOnboardingDone].
     */
    suspend fun get(): UserDietProfile? = userProfileRepo.get().toDietProfile()

    suspend fun upsert(profile: UserDietProfile) = save(profile)

    suspend fun save(profile: UserDietProfile) {
        val merged = userProfileRepo.get().applyDietProfile(profile)
        userProfileRepo.save(merged)
    }

    /** Sprawdza czy onboarding diety jest ukończony (do redirectu w DietScreen). */
    suspend fun isOnboardingDone(): Boolean = get()?.isOnboardingDone == true
}

/** Wyciąga pola diety ze scalonej encji do DTO [UserDietProfile]. */
fun UserProfile.toDietProfile(): UserDietProfile = UserDietProfile(
    id = id,
    ageYears = ageYears,
    heightCm = heightCm,
    activityLevel = activityLevel,
    avgStepsPerDay = avgStepsPerDay,
    goalType = goalType,
    paceKgPerWeek = paceKgPerWeek,
    customDeficitKcal = customDeficitKcal,
    dietPreference = dietPreference,
    allergies = allergies,
    intolerances = intolerances,
    dislikedFoods = dislikedFoods,
    lovedFoods = lovedFoods,
    cookingTimePerMealMin = cookingTimePerMealMin,
    eatsAtWork = eatsAtWork,
    hasMicrowaveAtWork = hasMicrowaveAtWork,
    mealPrepInterested = mealPrepInterested,
    weeklyBudgetPln = weeklyBudgetPln,
    medicalConditions = medicalConditions,
    medicalAwareness = medicalAwareness,
    usualTrainingHour = usualTrainingHour,
    onboardingCompletedAt = dietOnboardingCompletedAt,
    updatedAt = dietUpdatedAt
)

/** Wpisuje pola DTO [UserDietProfile] do scalonej encji, nie ruszając pól treningu. */
fun UserProfile.applyDietProfile(d: UserDietProfile): UserProfile = copy(
    ageYears = d.ageYears,
    heightCm = d.heightCm,
    activityLevel = d.activityLevel,
    avgStepsPerDay = d.avgStepsPerDay,
    goalType = d.goalType,
    paceKgPerWeek = d.paceKgPerWeek,
    customDeficitKcal = d.customDeficitKcal,
    dietPreference = d.dietPreference,
    allergies = d.allergies,
    intolerances = d.intolerances,
    dislikedFoods = d.dislikedFoods,
    lovedFoods = d.lovedFoods,
    cookingTimePerMealMin = d.cookingTimePerMealMin,
    eatsAtWork = d.eatsAtWork,
    hasMicrowaveAtWork = d.hasMicrowaveAtWork,
    mealPrepInterested = d.mealPrepInterested,
    weeklyBudgetPln = d.weeklyBudgetPln,
    medicalConditions = d.medicalConditions,
    medicalAwareness = d.medicalAwareness,
    usualTrainingHour = d.usualTrainingHour,
    dietOnboardingCompletedAt = d.onboardingCompletedAt,
    dietUpdatedAt = d.updatedAt
)
