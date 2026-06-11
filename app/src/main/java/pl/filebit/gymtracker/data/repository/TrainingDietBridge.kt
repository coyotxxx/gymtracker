package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.TrainingDaySummaryDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.IntensityScore
import pl.filebit.gymtracker.data.entity.PerformanceTrend
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingDaySummary
import pl.filebit.gymtracker.data.entity.TrainingType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most TRENING ↔ DIETA. Agreguje dane treningowe per dzień do TrainingDaySummary.
 *
 * Wywoływane:
 * - po Workout.finish() (real-time)
 * - z DietViewModel przy wejściu na ekran (ensure today)
 * - codziennie 00:01 (TODO PeriodicWorker)
 */
@Singleton
class TrainingDietBridge @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val planRepo: PlanRepository,
    private val dao: TrainingDaySummaryDao
) {

    /** Wylicza summary dla dnia ze startMs (00:00 lokalny czas). */
    suspend fun recomputeForDate(dateMs: Long) {
        val (start, end) = dayBounds(dateMs)
        // Znajdź workout który zaczął się w tym dniu
        val finished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt in start until end }
        val workout = finished.maxByOrNull { it.startedAt }

        if (workout != null) {
            // === Wykonany trening ===
            val sets = setDao.getForWorkout(workout.id)
            val workingSets = sets.filter { it.isCompleted && it.setType != SetType.WARMUP }
            val volumeKg = workingSets.sumOf { it.reps * it.weightKg }
            val rpes = workingSets.mapNotNull { it.rpe?.takeIf { r -> r > 0 } }
            val rpeAvg = rpes.takeIf { it.isNotEmpty() }?.average()
            val rirs = workingSets.mapNotNull { it.rir?.takeIf { r -> r > 0 } }
            val rirAvg = rirs.takeIf { it.isNotEmpty() }?.average()

            // Partie mięśniowe — z exerciseDao po exerciseId
            val exercisesById = workingSets.map { it.exerciseId }.distinct()
                .mapNotNull { exerciseDao.getById(it) }
                .associateBy { it.id }
            val muscleGroups = exercisesById.values
                .map { it.primaryMuscle.name }
                .distinct().joinToString(",")

            // Cardio: sumuj durationSec z setów ćwiczeń CARDIO → minuty
            val cardioMinutes = workingSets
                .filter { exercisesById[it.exerciseId]?.primaryMuscle?.name == "CARDIO" }
                .sumOf { it.durationSec ?: 0 } / 60

            // Intensywność: RPE + objętość
            val intensity = computeIntensity(rpeAvg, workingSets.size, volumeKg)
            val trainingType = inferType(rpeAvg, workingSets, volumeKg)

            // Planowana objętość (kg) — z PlanExerciseSet dla dnia tygodnia
            val plannedVolumeKg = computePlannedVolume(start)

            // Wykonany %: porównanie z planem (jeśli z planu)
            val completedPct = computeCompletedPct(workout, workingSets.size, plannedVolumeKg, volumeKg)

            // Trend: PROGRESS jeśli wszystkie reps zrobione, REGRESS jeśli niedokończone
            val trend = computeTrend(workingSets, sets)

            // Fatigue: rośnie z RPE i częstością treningów ostatnie 7 dni
            val fatigue = computeFatigueScore(workout.startedAt, rpeAvg)

            dao.upsert(
                TrainingDaySummary(
                    dateMs = start,
                    isTrainingDay = true,
                    workoutId = workout.id,
                    trainingType = trainingType,
                    startTimeMs = workout.startedAt,
                    durationMinutes = (workout.durationMillis / 60_000L).toInt(),
                    trainedMuscleGroupsCsv = muscleGroups,
                    plannedVolumeKg = plannedVolumeKg,
                    completedVolumeKg = volumeKg,
                    intensityScore = intensity,
                    rpeAverage = rpeAvg,
                    rirAverage = rirAvg,
                    workingSetsCount = workingSets.size,
                    repsTotal = workingSets.sumOf { it.reps },
                    cardioMinutes = cardioMinutes,
                    workoutCompletedPct = completedPct,
                    performanceTrend = trend,
                    fatigueScore = fatigue
                )
            )
        } else {
            // === Bez treningu — sprawdź czy planowany ===
            val isoDay = isoDayOfWeek(start)
            val plansForDay = runCatching { planRepo.getPlansForDay(isoDay) }.getOrNull().orEmpty()
            val plannedButNotDone = plansForDay.isNotEmpty()
            dao.upsert(
                TrainingDaySummary(
                    dateMs = start,
                    isTrainingDay = false,
                    workoutId = null,
                    trainingType = if (plannedButNotDone) TrainingType.SKIPPED else TrainingType.REST,
                    workoutCompletedPct = 0,
                    fatigueScore = 3   // dni REST = niski fatigue
                )
            )
        }
    }

    /** Helper: po Workout.finish() w VM — wywołuje recompute dla dnia tego workoutu. */
    suspend fun recomputeFromWorkout(workoutId: Long) {
        val w = workoutDao.getById(workoutId) ?: return
        recomputeForDate(w.startedAt)
    }

    /** Zapewnia summary dla dziś (lazy — wywoływane przy wejściu na DietScreen). */
    suspend fun ensureForToday() {
        val now = System.currentTimeMillis()
        val (start, _) = dayBounds(now)
        if (dao.getForDate(start) == null) {
            recomputeForDate(start)
        }
    }

    suspend fun getForDate(dateMs: Long): TrainingDaySummary? {
        val (start, _) = dayBounds(dateMs)
        return dao.getForDate(start)
    }

    fun observeForDate(dateMs: Long): Flow<TrainingDaySummary?> {
        val (start, _) = dayBounds(dateMs)
        return dao.observeForDate(start)
    }

    suspend fun getRecent(days: Int = 14): List<TrainingDaySummary> = dao.getRecent(days)

    /**
     * v2.18.0 (P1-6): czy AKTYWNY plan przewiduje trening w ten dzień (ISO).
     * Jedno źródło prawdy dla carb cycling — używane i przez wyświetlany cel diety,
     * i przez AdherenceCalculator (cel pokazany = cel oceniany). Forward (planowane,
     * nie "czy już wykonane").
     */
    suspend fun isPlannedTrainingDay(dateMs: Long): Boolean {
        val active = runCatching { planRepo.getActivePlan() }.getOrNull() ?: return false
        return active.daysOfWeek.contains(isoDayOfWeek(dateMs))
    }

    // === HELPERS ===

    private fun computeIntensity(rpe: Double?, sets: Int, volumeKg: Double): IntensityScore {
        if (rpe != null) return when {
            rpe >= 9.0 -> IntensityScore.HEAVY
            rpe >= 7.0 -> IntensityScore.MEDIUM
            else -> IntensityScore.LIGHT
        }
        // Bez RPE — heurystyka po objętości
        return when {
            sets >= 20 || volumeKg > 8000 -> IntensityScore.HEAVY
            sets >= 10 || volumeKg > 3000 -> IntensityScore.MEDIUM
            else -> IntensityScore.LIGHT
        }
    }

    private fun inferType(
        rpe: Double?,
        workingSets: List<pl.filebit.gymtracker.data.entity.WorkoutSet>,
        volumeKg: Double
    ): TrainingType {
        if (workingSets.isEmpty()) return TrainingType.REST
        val avgReps = workingSets.map { it.reps }.average()
        return when {
            avgReps <= 6 && (rpe ?: 8.0) >= 8.0 -> TrainingType.STRENGTH
            avgReps in 7.0..12.0 -> TrainingType.HYPERTROPHY
            avgReps > 12 -> TrainingType.MIXED
            else -> TrainingType.HYPERTROPHY
        }
    }

    private fun computeCompletedPct(
        workout: pl.filebit.gymtracker.data.entity.Workout,
        workingSetsCount: Int,
        plannedVolumeKg: Double = 0.0,
        completedVolumeKg: Double = 0.0
    ): Int {
        // Najpierw porównanie objętości jeśli plan ma ciężary
        if (plannedVolumeKg > 0.0 && completedVolumeKg > 0.0) {
            return ((completedVolumeKg / plannedVolumeKg) * 100).toInt().coerceIn(0, 100)
        }
        // Fallback: liczba serii z planu
        val planId = workout.fromPlanId ?: return 100
        val day = workout.fromDayOfWeek ?: return 100
        return runCatching {
            val planExes = kotlinx.coroutines.runBlocking {
                planRepo.getPlanExercisesForDay(planId, day)
            }
            var planSetsCount = 0
            kotlinx.coroutines.runBlocking {
                for (pe in planExes) {
                    planSetsCount += planRepo.getSetsForPlanExercise(pe.id).size
                }
            }
            if (planSetsCount == 0) 100
            else (workingSetsCount * 100 / planSetsCount).coerceIn(0, 100)
        }.getOrDefault(100)
    }

    /**
     * Sumuje planowaną objętość (kg) z PlanExerciseSet dla dnia tygodnia.
     * Bierze pod uwagę plan przypisany do dnia (po isoDayOfWeek).
     */
    private suspend fun computePlannedVolume(dayStartMs: Long): Double {
        val isoDay = isoDayOfWeek(dayStartMs)
        val plansForDay = runCatching { planRepo.getPlansForDay(isoDay) }.getOrNull().orEmpty()
        if (plansForDay.isEmpty()) return 0.0
        var total = 0.0
        for (plan in plansForDay) {
            val planExes = planRepo.getPlanExercisesForDay(plan.id, isoDay)
            for (pe in planExes) {
                val sets = planRepo.getSetsForPlanExercise(pe.id)
                total += sets.sumOf { (it.weightKg ?: 0.0) * it.reps }
            }
        }
        return total
    }

    private fun computeTrend(
        workingSets: List<pl.filebit.gymtracker.data.entity.WorkoutSet>,
        allSets: List<pl.filebit.gymtracker.data.entity.WorkoutSet>
    ): PerformanceTrend {
        if (workingSets.isEmpty()) return PerformanceTrend.STAGNATION
        val notCompletedRatio = (allSets.size - workingSets.size).toDouble() /
            allSets.size.coerceAtLeast(1)
        return if (notCompletedRatio > 0.2) PerformanceTrend.REGRESS
        else PerformanceTrend.PROGRESS
    }

    private suspend fun computeFatigueScore(workoutStartMs: Long, rpe: Double?): Int {
        // Liczba treningów w ostatnich 7 dniach (większa frekwencja = większy fatigue)
        val sevenDaysAgo = workoutStartMs - 7L * 24 * 3600 * 1000
        val recentCount = workoutDao.observeAllOnce()
            .count { it.finishedAt != null && it.startedAt in sevenDaysAgo until workoutStartMs }
        val rpeBonus = ((rpe ?: 7.0) - 6.0).coerceAtLeast(0.0).toInt()  // 0 dla RPE6, 4 dla RPE10
        return (recentCount + rpeBonus).coerceIn(1, 10)
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

    private fun isoDayOfWeek(dateMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        // Calendar.MONDAY = 2, niedziela = 1 → konwersja na ISO 1=Pn..7=Nd
        val cd = cal.get(Calendar.DAY_OF_WEEK)
        return if (cd == Calendar.SUNDAY) 7 else cd - 1
    }
}
