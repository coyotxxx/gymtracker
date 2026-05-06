package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.coroutines.flow.first
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.DietPhaseRepository
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.HydrationCalculator
import pl.filebit.gymtracker.data.repository.HydrationRepository
import pl.filebit.gymtracker.data.repository.MealFeedbackRepository
import pl.filebit.gymtracker.data.repository.RecoveryAnalyzer
import pl.filebit.gymtracker.data.repository.RecoveryRepository
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.util.TrendAnalyzer
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.ActivityRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builder zbiera CAŁY kontekst usera z 12 tabel w jednym przejściu.
 *
 * Używaj wszędzie gdzie potrzebujesz info o userze do prompt:
 * - DietAiService → toFullContext()
 * - WorkoutPlanAiService → toBaseProfile + toAdherence + toRecovery
 * - WeeklyReportService → toFullContext()
 * - PlanAuditService → toBaseProfile + toAdherence + toPRs
 *
 * Wzorzec: build() raz na wywołanie AI → wynik trzymany lokalnie w prompt.
 * Nie cache'ujemy bo dane szybko się zmieniają (codzienne logowanie).
 */
@Singleton
class MasterAiContextBuilder @Inject constructor(
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val bodyDao: BodyMeasurementDao,
    private val dietRepo: DietRepository,
    private val mealFeedbackRepo: MealFeedbackRepository,
    private val exerciseRepo: ExerciseRepository,
    private val adherenceCalc: AdherenceCalculator,
    private val recoveryRepo: RecoveryRepository,
    private val recoveryAnalyzer: RecoveryAnalyzer,
    private val activityRepo: ActivityRepository,
    private val hydrationRepo: HydrationRepository,
    private val hydrationCalc: HydrationCalculator,
    private val dietPhaseRepo: DietPhaseRepository,
    private val trainingBridge: TrainingDietBridge,
    private val statsRepo: StatsRepository
) {

    suspend fun build(): MasterAiContext {
        val profile = profileRepo.get()
        val dietProfile = runCatching { dietProfileRepo.get() }.getOrNull()
        val latestMeasurement = runCatching { bodyDao.getLatest() }.getOrNull()
        val weight = latestMeasurement?.weightKg ?: profile.bodyweightKg

        // === Trend wagi ===
        val measurements = runCatching { bodyDao.getAllAsc() }.getOrNull().orEmpty()
        val trend = TrendAnalyzer.analyze(measurements)

        // === Adherence 14 dni ===
        val adherence14 = runCatching { adherenceCalc.avgAdherenceLastDays(14) }
            .getOrNull() ?: pl.filebit.gymtracker.data.repository.AdherenceSummary()

        // === Recovery 7 dni ===
        val recoveryLogs = runCatching { recoveryRepo.getLast7Days() }.getOrNull().orEmpty()
        val recovery = recoveryAnalyzer.analyze(recoveryLogs)

        // === NEAT (kroki) ===
        val activityLogs = runCatching { activityRepo.getRecent(days = 30) }.getOrNull().orEmpty()
        val sortedSteps = activityLogs.sortedByDescending { it.dateMs }
        val avgSteps7d = sortedSteps.take(7).map { it.steps }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        val avgSteps30d = sortedSteps.map { it.steps }.average().takeIf { !it.isNaN() }?.toInt() ?: 0

        // === Hydration ===
        val hydrAdherence = runCatching {
            hydrationRepo.avgAdherenceLastNDays(weight ?: 75.0, 14, hydrationCalc)
        }.getOrDefault(0)

        // === Faza diety ===
        val activePhase = runCatching { dietPhaseRepo.getActive() }.getOrNull()
        val phaseLabel = activePhase?.phaseType?.name ?: when (profile.weightGoalType.name) {
            "CUT" -> "CUT"
            "BULK" -> "BULK"
            "MAINTAIN" -> "MAINTAIN"
            else -> "—"
        }
        val daysInPhase = activePhase?.let {
            ((System.currentTimeMillis() - it.startDateMs) / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)
        } ?: 0

        // === Dziś ===
        runCatching { trainingBridge.ensureForToday() }
        val todaySummary = runCatching { trainingBridge.getForDate(System.currentTimeMillis()) }.getOrNull()
        val isTrainingDay = todaySummary?.isTrainingDay == true
        val muscleGroups = todaySummary?.parsedMuscleGroups().orEmpty()

        // === MealFeedback ulubione/nielubiane ===
        val feedbacks = runCatching {
            mealFeedbackRepo.getTopFavorites(limit = 10)
        }.getOrNull().orEmpty()
        val disliked = runCatching {
            mealFeedbackRepo.getTopDisliked(limit = 5)
        }.getOrNull().orEmpty()

        // === Ulubione produkty (z FoodProduct.isFavorite) ===
        val favoriteProducts = runCatching { dietRepo.getFavoriteProducts() }.getOrNull().orEmpty()
        val favoriteFoodNames = favoriteProducts.map { it.name }

        // === Ulubione ćwiczenia (z Exercise.isFavorite) ===
        val favoriteExercises = runCatching { exerciseRepo.getFavorites() }.getOrNull().orEmpty()

        // === Top PRy (top 5) ===
        val allExes = runCatching { exerciseRepo.observeAll().first() }.getOrNull().orEmpty()
        val prList = mutableListOf<PRSummary>()
        for (ex in allExes) {
            runCatching { statsRepo.prForExercise(ex.id) }.getOrNull()?.let { pr ->
                if (pr.maxWeightKg > 0) {
                    val e1rm = pr.maxWeightKg * (1 + pr.repsAtMaxWeight / 30.0)  // Epley
                    prList += PRSummary(
                        exerciseName = ex.name,
                        maxWeightKg = pr.maxWeightKg,
                        repsAtMax = pr.repsAtMaxWeight,
                        estimatedOneRepMaxKg = e1rm
                    )
                }
            }
        }
        val topPRs = prList.sortedByDescending { it.estimatedOneRepMaxKg }.take(5)

        Log.d("MasterAiContext", "Built: weight=$weight phase=$phaseLabel adherence=${adherence14.avgKcalPct}% recovery=${recoveryLogs.size}d steps7=$avgSteps7d PRs=${topPRs.size}")

        return MasterAiContext(
            // Profil bazowy
            displayName = profile.displayName,
            gender = profile.gender,
            ageYears = dietProfile?.ageYears?.takeIf { it > 0 },
            heightCm = dietProfile?.heightCm?.takeIf { it > 0 },
            weightKg = weight,
            targetWeightKg = profile.targetWeightKg,
            experience = profile.experience,
            trainingGoal = profile.goal,
            weightGoalType = profile.weightGoalType,
            daysPerWeek = profile.daysPerWeek,
            sessionMinutes = profile.sessionMinutes,
            defaultRestSeconds = profile.defaultRestSeconds,
            injuriesNotes = profile.injuriesNotes,

            // Profil dietetyczny
            activityLevel = dietProfile?.activityLevel ?: pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE,
            avgStepsPerDay = dietProfile?.avgStepsPerDay ?: avgSteps30d,
            dietPreference = dietProfile?.dietPreference ?: pl.filebit.gymtracker.data.entity.DietPreference.STANDARD,
            allergiesCsv = dietProfile?.allergies ?: "",
            intolerances = dietProfile?.intolerances ?: "",
            dislikedFoodsCsv = dietProfile?.dislikedFoods ?: "",
            lovedFoodsCsv = dietProfile?.lovedFoods ?: "",
            weeklyBudgetPln = dietProfile?.weeklyBudgetPln,
            medicalConditionsCsv = dietProfile?.medicalConditions ?: "",
            cookingTimePerMealMin = dietProfile?.cookingTimePerMealMin ?: 15,
            usualTrainingHour = dietProfile?.usualTrainingHour,
            dietProfileFilled = dietProfile?.isOnboardingDone == true,

            // Sprzęt
            availableEquipmentCsv = profile.availableEquipmentCsv,

            // Ulubione
            favoriteExercises = favoriteExercises,
            favoriteFoodNames = favoriteFoodNames,
            likedMealNames = feedbacks.map { it.displayName },
            dislikedMealNames = disliked.map { it.displayName },

            // Trend wagi
            weightTrendSlopeKgPerWeek = trend.slopeKgPerWeek,
            weightAvg7d = trend.avg7Days,
            weightAvg14d = trend.avg14Days,
            weightSampleCount = trend.sampleCount,

            // Adherence
            avgKcalAdherencePct = adherence14.avgKcalPct,
            avgProteinAdherencePct = adherence14.avgProteinPct,
            avgCarbsAdherencePct = adherence14.avgCarbsPct,
            avgFatAdherencePct = adherence14.avgFatPct,
            mealsLoggedDays = adherence14.sampleDays,
            workoutsPlanned14d = adherence14.workoutsPlanned,
            workoutsDone14d = adherence14.workoutsDone,

            // Recovery
            avgSleepHours = recovery.avgSleepHours,
            avgSleepQuality = recovery.avgSleepQuality,
            avgStressLevel = recovery.avgStress,
            avgHungerLevel = recovery.avgHunger,
            avgEnergyLevel = recovery.avgEnergy,
            avgSorenessLevel = recovery.avgSoreness,
            avgDifficultyLevel = recovery.avgDifficulty,
            recoverySampleDays = recoveryLogs.size,

            // NEAT
            avgSteps7d = avgSteps7d,
            avgSteps30d = avgSteps30d,
            baselineSteps = dietProfile?.avgStepsPerDay ?: avgSteps30d,

            // Hydration
            hydrationAdherencePct = hydrAdherence,

            // Faza
            currentDietPhase = phaseLabel,
            daysInCurrentPhase = daysInPhase,

            // Dziś
            isTodayTrainingDay = isTrainingDay,
            todayTrainingTypeLabel = todaySummary?.trainingType?.name,
            todayMuscleGroups = muscleGroups,

            // PRy
            topPRs = topPRs
        )
    }
}
