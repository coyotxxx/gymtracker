package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.WorkoutSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class OverviewStats(
    val totalWorkouts: Int,
    val totalDurationMillis: Long,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val avgVolumePerWorkout: Double,
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int
)

data class ExercisePr(
    val maxWeightKg: Double,
    val repsAtMaxWeight: Int,
    val maxVolumeKg: Double,
    val estimated1RM: Double,
    val totalSetsLogged: Int
)

@Singleton
class StatsRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: pl.filebit.gymtracker.data.db.dao.ExerciseDao
) {

    /**
     * Epley 1RM formula: weight × (1 + reps/30). Działa dobrze do ~10 powt.
     */
    fun epley1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0) return 0.0
        return weightKg * (1 + reps / 30.0)
    }

    fun brzycki1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || reps >= 37 || weightKg <= 0) return 0.0
        return weightKg * 36.0 / (37.0 - reps)
    }

    fun lombardi1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 0 || weightKg <= 0) return 0.0
        return weightKg * Math.pow(reps.toDouble(), 0.10)
    }

    suspend fun overview(): OverviewStats {
        val all = workoutDao.observeAllOnce()
        val finished = all.filter { it.finishedAt != null }
        val totalDuration = finished.sumOf { it.durationMillis }
        var totalVolume = 0.0
        var totalSets = 0
        for (w in finished) {
            val sets = setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
            totalVolume += sets.sumOf { it.reps * it.weightKg }
            totalSets += sets.size
        }
        val avg = if (finished.isNotEmpty()) totalVolume / finished.size else 0.0
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
        val monthAgo = now - 30L * 24L * 60L * 60L * 1000L
        val thisWeek = finished.count { it.startedAt >= weekAgo }
        val thisMonth = finished.count { it.startedAt >= monthAgo }
        return OverviewStats(
            totalWorkouts = finished.size,
            totalDurationMillis = totalDuration,
            totalVolumeKg = totalVolume,
            totalSets = totalSets,
            avgVolumePerWorkout = avg,
            workoutsThisWeek = thisWeek,
            workoutsThisMonth = thisMonth
        )
    }

    suspend fun prForExercise(exerciseId: Long): ExercisePr? {
        val sets = setDao.getAllForExercise(exerciseId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (sets.isEmpty()) return null
        val maxWeightSet = sets.maxByOrNull { it.weightKg } ?: return null
        // pogrupuj per workout, wybierz max objętości
        val perWorkoutVol = sets.groupBy { it.workoutId }
            .mapValues { (_, list) -> list.sumOf { it.reps * it.weightKg } }
        val maxVolume = perWorkoutVol.values.maxOrNull() ?: 0.0
        val best1RM = sets.maxOf { epley1RM(it.weightKg, it.reps) }
        return ExercisePr(
            maxWeightKg = maxWeightSet.weightKg,
            repsAtMaxWeight = maxWeightSet.reps,
            maxVolumeKg = maxVolume,
            estimated1RM = (best1RM * 10).roundToInt() / 10.0,
            totalSetsLogged = sets.size
        )
    }

    /**
     * Punkty wykresu progresji per ćwiczenie — po jednym na trening.
     */
    suspend fun progressionForExercise(exerciseId: Long): List<ExerciseProgressionPoint> {
        val all = setDao.getAllForExercise(exerciseId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (all.isEmpty()) return emptyList()
        val workoutsById = workoutDao.observeAllOnce().associateBy { it.id }
        return all.groupBy { it.workoutId }
            .mapNotNull { (workoutId, sets) ->
                val w = workoutsById[workoutId] ?: return@mapNotNull null
                if (w.finishedAt == null) return@mapNotNull null
                val maxWeight = sets.maxOf { it.weightKg }
                val repsAtMax = sets.filter { it.weightKg == maxWeight }.maxOf { it.reps }
                val volume = sets.sumOf { it.reps * it.weightKg }
                val best1Rm = sets.maxOf { epley1RM(it.weightKg, it.reps) }
                ExerciseProgressionPoint(
                    workoutId = workoutId,
                    workoutDate = w.startedAt,
                    maxWeightKg = maxWeight,
                    repsAtMax = repsAtMax,
                    volumeKg = volume,
                    estimated1RM = (best1Rm * 10).roundToInt() / 10.0
                )
            }
            .sortedBy { it.workoutDate }
    }

    /**
     * Pełna historia setów ćwiczenia (po zakończonych treningach), posortowane od najnowszych.
     */
    suspend fun historyForExercise(exerciseId: Long): List<WorkoutSet> {
        val finishedIds = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .map { it.id }
            .toSet()
        return setDao.getAllForExercise(exerciseId)
            .filter { it.workoutId in finishedIds }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Sugestie progresji: jeśli wszystkie working sety wykonane (isCompleted, nie warmup)
     * z reps >= prog ostatniego treningu i tą samą lub większą wagą — sugeruj +2.5kg
     * w następnym treningu.
     */
    suspend fun progressionTipsForWorkout(currentWorkoutId: Long): List<ProgressionTip> {
        val curSets = setDao.getForWorkout(currentWorkoutId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (curSets.isEmpty()) return emptyList()
        val byExercise = curSets.groupBy { it.exerciseId }
        val tips = mutableListOf<ProgressionTip>()
        for ((exId, list) in byExercise) {
            val curMaxWeight = list.maxOf { it.weightKg }
            val curMinRepsAtMax = list.filter { it.weightKg == curMaxWeight }.minOf { it.reps }
            // wszystkie working sety dziś wykonane?
            val allDone = list.all { it.isCompleted }
            if (!allDone) continue
            // poprzedni trening dla tego ćwiczenia
            val previousAll = setDao.getAllForExercise(exId)
                .filter { it.workoutId != currentWorkoutId && it.isCompleted && it.setType != SetType.WARMUP }
            if (previousAll.isEmpty()) continue
            val previousByWorkout = previousAll.groupBy { it.workoutId }
            // weź ostatni trening (po max createdAt)
            val lastWorkoutId = previousByWorkout.keys.maxByOrNull { wid ->
                previousByWorkout[wid]!!.maxOf { it.createdAt }
            } ?: continue
            val prevSets = previousByWorkout[lastWorkoutId]!!
            val prevMaxWeight = prevSets.maxOf { it.weightKg }
            val prevMinRepsAtMax = prevSets.filter { it.weightKg == prevMaxWeight }.minOf { it.reps }
            // warunek: dziś waga == lub > poprzednio AND dziś min reps >= poprzednie min reps
            if (curMaxWeight >= prevMaxWeight && curMinRepsAtMax >= prevMinRepsAtMax && curMinRepsAtMax >= 8) {
                val suggested = curMaxWeight + 2.5
                val name = exerciseDao.getById(exId)?.name ?: "?"
                tips.add(
                    ProgressionTip(
                        exerciseId = exId,
                        exerciseName = name,
                        currentWeightKg = curMaxWeight,
                        suggestedWeightKg = suggested,
                        reason = "wszystkie serie ✓"
                    )
                )
            }
        }
        return tips
    }

    /**
     * Czy któryś set z bieżącego treningu pobił max wagę × powt dla swojego ćwiczenia
     * w porównaniu do innych zakończonych treningów (excl. obecnego).
     */
    suspend fun detectNewPRs(currentWorkoutId: Long): List<NewPr> {
        val curSets = setDao.getForWorkout(currentWorkoutId)
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
        if (curSets.isEmpty()) return emptyList()
        val byExercise = curSets.groupBy { it.exerciseId }
        val results = mutableListOf<NewPr>()
        for ((exId, list) in byExercise) {
            val previousMax1RM = setDao.getAllForExercise(exId)
                .filter {
                    it.workoutId != currentWorkoutId &&
                        it.isCompleted && it.setType != SetType.WARMUP
                }
                .maxOfOrNull { epley1RM(it.weightKg, it.reps) } ?: 0.0
            val curMax = list.maxOfOrNull { epley1RM(it.weightKg, it.reps) } ?: 0.0
            if (curMax > previousMax1RM && curMax > 0.0) {
                val bestSet = list.maxByOrNull { epley1RM(it.weightKg, it.reps) }!!
                results.add(
                    NewPr(
                        exerciseId = exId,
                        weightKg = bestSet.weightKg,
                        reps = bestSet.reps,
                        previousBest1RM = previousMax1RM,
                        new1RM = curMax
                    )
                )
            }
        }
        return results
    }
}

data class NewPr(
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val previousBest1RM: Double,
    val new1RM: Double
)

/**
 * Punkt na wykresie: jeden trening, max waga × max powt × suma volume tego ćwiczenia.
 */
data class ExerciseProgressionPoint(
    val workoutId: Long,
    val workoutDate: Long,
    val maxWeightKg: Double,
    val repsAtMax: Int,
    val volumeKg: Double,
    val estimated1RM: Double
)

/**
 * Sugestia: w danym ćwiczeniu spróbuj zwiększyć ciężar.
 */
data class ProgressionTip(
    val exerciseId: Long,
    val exerciseName: String,
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val reason: String
)

