package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.first
import pl.filebit.gymtracker.data.db.dao.AdherenceLogDao
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.DiagnosticLevel
import pl.filebit.gymtracker.util.computeDailyGoal
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Liczy zgodność dnia: cel kcal/B/W/T vs faktyczne spożycie + wykonanie treningu.
 *
 * Wywołania:
 * - real-time po każdej zmianie MealEntry (w DietViewModel po addMeal/deleteMeal)
 * - po Workout.finish() (już mamy w bridge — tu doliczamy adherence)
 * - codziennie 23:55 (TODO: PeriodicWorker w v0.89.45)
 */
@Singleton
class AdherenceCalculator @Inject constructor(
    private val dietRepo: DietRepository,
    private val dietPrefs: DietPreferences,
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val trainingDietBridge: TrainingDietBridge,
    private val bodyDao: pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao,
    private val dao: AdherenceLogDao,
    private val consumptionRepo: MealConsumptionRepository,
    // v2.24.0: nullable-default — Hilt wstrzykuje realny logger, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {

    /** Policz adherence dla podanego dnia + zapisz do AdherenceLog. */
    suspend fun computeForDate(dateMs: Long) {
        val (start, _) = dayBounds(dateMs)
        val profile = runCatching { profileRepo.get() }.getOrNull() ?: return
        val dietProfile = runCatching { dietProfileRepo.get() }.getOrNull()
        val config = dietPrefs.load()
        val latestWeight = runCatching { bodyDao.getLatest()?.weightKg }.getOrNull()
        // v2.18.0 (P1-6): cel carbs/fat zależy od dnia (carb cycling) — spójny z wyświetlanym.
        val isTrainingDay = runCatching { trainingDietBridge.isPlannedTrainingDay(start) }.getOrDefault(false)
        val goal = computeDailyGoal(
            profile = profile,
            manualKcalOverride = config.manualKcal,
            customDeficit = config.customDeficit,
            dietProfile = dietProfile,
            latestMeasuredWeightKg = latestWeight,
            isTrainingDay = isTrainingDay
        )

        // Faktyczne spożycie z MealEntries
        // v1.24.14: respektuj MealConsumptionStatus.SKIPPED — posiłek pominięty
        // świadomie przez usera NIE liczy się jako spożyty.
        // v2.11.0: adherence = REALNE spożycie, nie plan. Posiłek liczy się jako zjedzony gdy:
        //   - slot oznaczony CONSUMED, LUB
        //   - wpis RĘCZNY (isPlanned=false) i slot NIE jest SKIPPED.
        // Plan AI (isPlanned=true) bez jawnego CONSUMED = NIEzjedzony (0 kcal). Dzięki temu
        // wygenerowany plan, którego user nie zjadł, nie zawyża adherence do fałszywego 100%.
        val meals = dietRepo.getMealsForDate(start)
        val consumptions = runCatching { consumptionRepo.getForDate(start) }.getOrDefault(emptyList())
        val statusByType = consumptions.associate { it.mealType to it.status }
        val products = dietRepo.observeAllProducts().first().associateBy { it.id }
        var actualKcal = 0.0
        var actualProtein = 0.0
        var actualCarbs = 0.0
        var actualFat = 0.0
        // v1.28.9: liczymy POSIŁKI (sloty mealType), nie wpisy produktów. MealEntry to
        // jeden produkt — dzień z 17 produktami w 3 posiłkach dawał wcześniej "17/3 posiłków".
        val loggedMealTypes = mutableSetOf<pl.filebit.gymtracker.data.entity.MealType>()
        for (m in meals) {
            val eaten = when (statusByType[m.mealType]) {
                pl.filebit.gymtracker.data.entity.MealConsumptionStatus.CONSUMED -> true
                pl.filebit.gymtracker.data.entity.MealConsumptionStatus.SKIPPED -> false
                // null (brak statusu) lub PLANNED: ręczny wpis liczymy, plan AI dopiero po potwierdzeniu
                else -> !m.isPlanned
            }
            if (!eaten) continue
            val p = products[m.productId] ?: continue
            val factor = m.grams / 100.0
            actualKcal += p.kcalPer100g * factor
            actualProtein += p.proteinPer100g * factor
            actualCarbs += p.carbsPer100g * factor
            actualFat += p.fatPer100g * factor
            loggedMealTypes.add(m.mealType)
        }

        // v2.24.0: posiłki świadomie pominięte przez usera — to one „znikały" z raportu.
        val skippedTypes = consumptions
            .filter { it.status == pl.filebit.gymtracker.data.entity.MealConsumptionStatus.SKIPPED }
            .map { it.mealType.name }

        // Trening — z TrainingDaySummary
        val training = trainingDietBridge.getForDate(start)
        val wasTrainingPlanned = (training?.workoutId != null) ||
            (training?.trainingType == pl.filebit.gymtracker.data.entity.TrainingType.SKIPPED)
        val wasTrainingDone = training?.isTrainingDay == true

        dao.upsert(
            AdherenceLog(
                dateMs = start,
                targetKcal = goal.kcal,
                actualKcal = actualKcal.roundToInt(),
                kcalAdherencePct = pct(actualKcal, goal.kcal.toDouble()),
                targetProteinG = goal.proteinG,
                actualProteinG = actualProtein.roundToInt(),
                proteinAdherencePct = pct(actualProtein, goal.proteinG.toDouble()),
                targetCarbsG = goal.carbsG,
                actualCarbsG = actualCarbs.roundToInt(),
                carbsAdherencePct = pct(actualCarbs, goal.carbsG.toDouble()),
                targetFatG = goal.fatG,
                actualFatG = actualFat.roundToInt(),
                fatAdherencePct = pct(actualFat, goal.fatG.toDouble()),
                // v1.24.14: liczymy posiłki które user faktycznie zjadł (nie pominął)
                // v1.28.9: liczba slotów posiłkowych z jedzeniem, nie wpisów produktów
                mealsLoggedCount = loggedMealTypes.size,
                mealsPlannedCount = config.mealsPerDay,
                wasTrainingPlanned = wasTrainingPlanned,
                wasTrainingDone = wasTrainingDone
            )
        )

        // v2.24.0: log wyliczenia — kcal%, posiłki zjedzone/zaplanowane + KTÓRE pominięte.
        // To jest brakujący ślad dla problemu „w piątek nie zjadłem kolacji a raport milczy".
        val kcalPct = pct(actualKcal, goal.kcal.toDouble())
        val skippedNote = if (skippedTypes.isEmpty()) "" else " · pominięte: ${skippedTypes.joinToString(",")}"
        diag?.event(
            category = DiagnosticCategory.ADHERENCE,
            level = if (skippedTypes.isNotEmpty() || kcalPct < 70) DiagnosticLevel.WARN else DiagnosticLevel.INFO,
            source = "AdherenceCalculator",
            event = "adherence_computed",
            message = "Zgodność dnia: kcal $kcalPct% (${actualKcal.roundToInt()}/${goal.kcal}), " +
                "posiłki ${loggedMealTypes.size}/${config.mealsPerDay}$skippedNote",
            dataJson = buildString {
                append("{")
                append("\"dayStartMs\":$start,")
                append("\"kcalPct\":$kcalPct,")
                append("\"actualKcal\":${actualKcal.roundToInt()},\"targetKcal\":${goal.kcal},")
                append("\"proteinPct\":${pct(actualProtein, goal.proteinG.toDouble())},")
                append("\"mealsLogged\":${loggedMealTypes.size},\"mealsPlanned\":${config.mealsPerDay},")
                append("\"skipped\":[${skippedTypes.joinToString(",") { "\"$it\"" }}],")
                append("\"isTrainingDay\":$isTrainingDay,\"trainingDone\":$wasTrainingDone")
                append("}")
            },
            success = true
        )
    }

    suspend fun computeForToday() {
        computeForDate(System.currentTimeMillis())
    }

    suspend fun getRecent(days: Int = 14): List<AdherenceLog> = dao.getRecent(days)

    suspend fun getForDate(dateMs: Long): AdherenceLog? {
        val (start, _) = dayBounds(dateMs)
        return dao.getForDate(start)
    }

    /**
     * Średnia zgodność z N dni — wskaźnik dla CalorieAdjustmentEngine.
     * Bierzemy dni gdzie cokolwiek zostało zalogowane (mealsLoggedCount lub actualKcal).
     * v1.24.5: dodano `actualKcal > 0` żeby backup z aplikacji innej (np. zaimportowane
     * z innego źródła bez liczników meal entries) nadal liczył się jako "z zalogowaną dietą".
     */
    suspend fun avgAdherenceLastDays(days: Int): AdherenceSummary {
        val logs = dao.getRecent(days).filter { it.mealsLoggedCount > 0 || it.actualKcal > 0 }
        if (logs.isEmpty()) return AdherenceSummary()
        return AdherenceSummary(
            sampleDays = logs.size,
            avgKcalPct = logs.map { it.kcalAdherencePct }.average().roundToInt(),
            avgProteinPct = logs.map { it.proteinAdherencePct }.average().roundToInt(),
            avgCarbsPct = logs.map { it.carbsAdherencePct }.average().roundToInt(),
            avgFatPct = logs.map { it.fatAdherencePct }.average().roundToInt(),
            highAdherenceDays = logs.count { it.isHighAdherence },
            avgScore = logs.map { it.overallScore }.average().roundToInt(),
            workoutsPlanned = logs.count { it.wasTrainingPlanned },
            workoutsDone = logs.count { it.wasTrainingDone }
        )
    }

    private fun pct(actual: Double, target: Double): Int {
        if (target <= 0) return 0
        return ((actual / target) * 100).roundToInt().coerceIn(0, 300)
    }

    private fun dayBounds(dateMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}

data class AdherenceSummary(
    val sampleDays: Int = 0,
    val avgKcalPct: Int = 0,
    val avgProteinPct: Int = 0,
    val avgCarbsPct: Int = 0,
    val avgFatPct: Int = 0,
    val highAdherenceDays: Int = 0,
    val avgScore: Int = 0,
    val workoutsPlanned: Int = 0,
    val workoutsDone: Int = 0
)
