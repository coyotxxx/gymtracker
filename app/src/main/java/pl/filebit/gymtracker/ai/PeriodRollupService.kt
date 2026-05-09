package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.MonthlyRollupDao
import pl.filebit.gymtracker.data.db.dao.QuarterlyRollupDao
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.WeeklyRollupDao
import pl.filebit.gymtracker.data.entity.MonthlyRollup
import pl.filebit.gymtracker.data.entity.QuarterlyRollup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.entity.WeeklyRollup
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.StatsCacheService
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.11.66 — Service zamykający okresy (week/month/quarter) i computing rollupów.
 *
 * Wywoływany:
 *  - WorkoutRepository.finish() po finalizacji treningu (closeWeekIfNeeded etc.)
 *  - GymTrackerApp.onCreate() przy starcie aplikacji (close zaległych okresów)
 *
 * Idempotentny: używa countForX przed insert. Rollupy raz utworzone nie są
 * re-computowane (drift propaguje przy re-summary).
 */
@Singleton
class PeriodRollupService @Inject constructor(
    private val statsCacheService: StatsCacheService,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val eventDao: TrainingEventDao,
    private val weeklyDao: WeeklyRollupDao,
    private val monthlyDao: MonthlyRollupDao,
    private val quarterlyDao: QuarterlyRollupDao
) {
    private val json = Json { encodeDefaults = false; prettyPrint = false }

    /**
     * Zamyka wszystkie zaległe tygodnie (od najstarszego nie-rollupowanego do
     * ostatniego zakończonego). Bieżący tydzień NIE jest zamykany.
     */
    suspend fun closeWeekIfNeeded() {
        val snapshot = statsCacheService.snapshot()
        val finished = snapshot.finishedWorkouts
        if (finished.isEmpty()) return

        val firstWorkoutWeek = startOfWeek(finished.minOf { it.startedAt })
        val lastCompleteWeek = startOfWeek(System.currentTimeMillis()) - MS_PER_WEEK

        var weekStart = firstWorkoutWeek
        while (weekStart <= lastCompleteWeek) {
            val current = weekStart  // capture for non-mutability
            if (weeklyDao.countForWeek(current) == 0) {
                val rollup = computeWeeklyRollup(current, snapshot.finishedWorkouts, snapshot.completedSetsByWorkoutId, snapshot.exercisesById)
                if (rollup != null) {
                    weeklyDao.insert(rollup)
                    Log.d("PeriodRollup", "Weekly rollup created for ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(current))}")
                }
            }
            weekStart += MS_PER_WEEK
        }
    }

    /** Zamyka zaległe miesiące (do ostatniego zakończonego). */
    suspend fun closeMonthIfNeeded() {
        val snapshot = statsCacheService.snapshot()
        val finished = snapshot.finishedWorkouts
        if (finished.isEmpty()) return

        val firstMonth = startOfMonth(finished.minOf { it.startedAt })
        val lastCompleteMonth = startOfPreviousMonth(System.currentTimeMillis())

        var month = firstMonth
        while (month <= lastCompleteMonth) {
            val current = month
            if (monthlyDao.countForMonth(current) == 0) {
                val rollup = computeMonthlyRollup(current, snapshot.finishedWorkouts, snapshot.completedSetsByWorkoutId, snapshot.exercisesById)
                if (rollup != null) {
                    monthlyDao.insert(rollup)
                    Log.d("PeriodRollup", "Monthly rollup created for ${java.text.SimpleDateFormat("yyyy-MM").format(java.util.Date(current))}")
                }
            }
            month = startOfNextMonth(month)
        }
    }

    /** Zamyka zaległe kwartały. */
    suspend fun closeQuarterIfNeeded() {
        val snapshot = statsCacheService.snapshot()
        val finished = snapshot.finishedWorkouts
        if (finished.isEmpty()) return

        val firstQuarter = startOfQuarter(finished.minOf { it.startedAt })
        val lastCompleteQuarter = startOfPreviousQuarter(System.currentTimeMillis())

        var quarter = firstQuarter
        while (quarter <= lastCompleteQuarter) {
            val current = quarter
            if (quarterlyDao.countForQuarter(current) == 0) {
                val rollup = computeQuarterlyRollup(current, snapshot.finishedWorkouts, snapshot.completedSetsByWorkoutId, snapshot.exercisesById)
                if (rollup != null) {
                    quarterlyDao.insert(rollup)
                    Log.d("PeriodRollup", "Quarterly rollup created for ${java.text.SimpleDateFormat("yyyy-MM").format(java.util.Date(current))}")
                }
            }
            quarter = startOfNextQuarter(quarter)
        }
    }

    /** Zamyka wszystkie 3 typy w jednym wywołaniu. */
    suspend fun closeAllPeriodsIfNeeded() {
        runCatching { closeWeekIfNeeded() }
        runCatching { closeMonthIfNeeded() }
        runCatching { closeQuarterIfNeeded() }
    }

    // ============================================================================
    // Computing functions (pure, testowalne)
    // ============================================================================

    private suspend fun computeWeeklyRollup(
        weekStartMs: Long,
        allWorkouts: List<Workout>,
        completedSetsByWorkoutId: Map<Long, List<WorkoutSet>>,
        exercisesById: Map<Long, pl.filebit.gymtracker.data.entity.Exercise>
    ): WeeklyRollup? {
        val weekEndMs = weekStartMs + MS_PER_WEEK
        val workoutsInWeek = allWorkouts.filter {
            it.finishedAt != null && it.startedAt >= weekStartMs && it.startedAt < weekEndMs
        }
        if (workoutsInWeek.isEmpty()) return null

        val setsInWeek = workoutsInWeek.flatMap { completedSetsByWorkoutId[it.id].orEmpty() }
            .filter { it.setType != SetType.WARMUP && it.weightKg > 0.0 }
        val totalVolume = setsInWeek.sumOf { it.weightKg * it.reps }
        val totalSets = setsInWeek.size
        val avgRpe = setsInWeek.mapNotNull { it.rpe?.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val wellbeings = workoutsInWeek.mapNotNull { it.wellbeingRating?.toDouble() }
        val avgWellbeing = if (wellbeings.isNotEmpty()) wellbeings.average() else null

        // Best set per main lift (po e1RM)
        val mainLiftKeywords = listOf("Wyciskanie sztangi leżąc", "Przysiad ze sztangą", "Martwy ciąg klasyczny", "Wyciskanie żołnierskie", "Wiosłowanie sztangą")
        val mainLiftBests = mutableMapOf<String, Pair<Double, Int>>()  // name -> (weight, reps)
        for (set in setsInWeek) {
            val ex = exercisesById[set.exerciseId] ?: continue
            val matchedKey = mainLiftKeywords.firstOrNull { ex.name.contains(it, ignoreCase = true) } ?: continue
            val newE1rm = set.weightKg * (1 + set.reps / 30.0)
            val current = mainLiftBests[matchedKey]
            val currentE1rm = current?.let { it.first * (1 + it.second / 30.0) } ?: 0.0
            if (newE1rm > currentE1rm) {
                mainLiftBests[matchedKey] = set.weightKg to set.reps
            }
        }
        val mainLiftsJson = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                mainLiftBests.forEach { (name, pair) ->
                    val e1rm = pair.first * (1 + pair.second / 30.0)
                    put(name, "${pair.first} kg × ${pair.second} (e1RM ${"%.1f".format(java.util.Locale.US, e1rm)})")
                }
            }
        )

        // Muscle volume %
        val volByMuscle = mutableMapOf<String, Double>()
        for (set in setsInWeek) {
            val muscle = exercisesById[set.exerciseId]?.primaryMuscle?.name ?: continue
            volByMuscle[muscle] = (volByMuscle[muscle] ?: 0.0) + set.weightKg * set.reps
        }
        val sumVol = volByMuscle.values.sum()
        val musclePctsJson = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                volByMuscle.forEach { (m, v) ->
                    put(m, if (sumVol > 0) (v * 100 / sumVol).toInt() else 0)
                }
            }
        )

        return WeeklyRollup(
            weekStartMs = weekStartMs,
            totalVolumeKg = totalVolume,
            sessionsCount = workoutsInWeek.size,
            totalSets = totalSets,
            avgRpe = avgRpe,
            avgWellbeing = avgWellbeing,
            mainLiftsBestJson = mainLiftsJson,
            muscleVolumePctsJson = musclePctsJson,
            autoNotes = ""
        )
    }

    private suspend fun computeMonthlyRollup(
        monthStartMs: Long,
        allWorkouts: List<Workout>,
        completedSetsByWorkoutId: Map<Long, List<WorkoutSet>>,
        exercisesById: Map<Long, pl.filebit.gymtracker.data.entity.Exercise>
    ): MonthlyRollup? {
        val monthEndMs = startOfNextMonth(monthStartMs)
        val workoutsInMonth = allWorkouts.filter {
            it.finishedAt != null && it.startedAt >= monthStartMs && it.startedAt < monthEndMs
        }
        if (workoutsInMonth.isEmpty()) return null

        val setsInMonth = workoutsInMonth.flatMap { completedSetsByWorkoutId[it.id].orEmpty() }
            .filter { it.setType != SetType.WARMUP && it.weightKg > 0.0 }

        val totalVolume = setsInMonth.sumOf { it.weightKg * it.reps }
        val totalSets = setsInMonth.size
        val avgRpe = setsInMonth.mapNotNull { it.rpe?.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        // Najlepsze e1RM per main lift na koniec miesiąca
        val mainLiftKeywords = listOf("Wyciskanie sztangi leżąc", "Przysiad ze sztangą", "Martwy ciąg klasyczny")
        val e1rmByLift = mutableMapOf<String, Double>()
        for (set in setsInMonth) {
            val ex = exercisesById[set.exerciseId] ?: continue
            val matchedKey = mainLiftKeywords.firstOrNull { ex.name.contains(it, ignoreCase = true) } ?: continue
            val newE1rm = set.weightKg * (1 + set.reps / 30.0)
            if (newE1rm > (e1rmByLift[matchedKey] ?: 0.0)) {
                e1rmByLift[matchedKey] = newE1rm
            }
        }
        val mainLiftsE1rmJson = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                e1rmByLift.forEach { (name, e1rm) ->
                    put(name, "%.1f".format(java.util.Locale.US, e1rm).toDouble())
                }
            }
        )

        // Body weight delta
        val measurements = bodyMeasurementDao.getAllAsc()
            .filter { it.weightKg != null && it.date in monthStartMs until monthEndMs }
        val bodyWeightStart = measurements.minByOrNull { it.date }?.weightKg
        val bodyWeightEnd = measurements.maxByOrNull { it.date }?.weightKg
        val bodyWeightDelta = if (bodyWeightStart != null && bodyWeightEnd != null) bodyWeightEnd - bodyWeightStart else null

        // Eventy w tym miesiącu
        val eventsInMonth = eventDao.getInRange(monthStartMs, monthEndMs - 1)
        val prCount = eventsInMonth.count { it.type == TrainingEventType.PR_SET }
        val planChanges = eventsInMonth.count { it.type == TrainingEventType.PLAN_START || it.type == TrainingEventType.PLAN_END }
        val deloadCount = eventsInMonth.count { it.type == TrainingEventType.DELOAD_DETECTED }

        return MonthlyRollup(
            monthStartMs = monthStartMs,
            totalVolumeKg = totalVolume,
            sessionsCount = workoutsInMonth.size,
            totalSets = totalSets,
            avgRpe = avgRpe,
            mainLiftsE1rmEndJson = mainLiftsE1rmJson,
            bodyWeightEndKg = bodyWeightEnd,
            bodyWeightDeltaKg = bodyWeightDelta,
            prCount = prCount,
            planChanges = planChanges,
            deloadCount = deloadCount
        )
    }

    private suspend fun computeQuarterlyRollup(
        quarterStartMs: Long,
        allWorkouts: List<Workout>,
        completedSetsByWorkoutId: Map<Long, List<WorkoutSet>>,
        exercisesById: Map<Long, pl.filebit.gymtracker.data.entity.Exercise>
    ): QuarterlyRollup? {
        val quarterEndMs = startOfNextQuarter(quarterStartMs)
        val workoutsInQuarter = allWorkouts.filter {
            it.finishedAt != null && it.startedAt >= quarterStartMs && it.startedAt < quarterEndMs
        }
        if (workoutsInQuarter.isEmpty()) return null

        val setsInQuarter = workoutsInQuarter.flatMap { completedSetsByWorkoutId[it.id].orEmpty() }
            .filter { it.setType != SetType.WARMUP && it.weightKg > 0.0 }
        val totalVolume = setsInQuarter.sumOf { it.weightKg * it.reps }

        // Pierwszy i ostatni e1RM w kwartale per main lift
        val mainLiftKeywords = listOf("Wyciskanie sztangi leżąc", "Przysiad ze sztangą", "Martwy ciąg klasyczny")
        val firstE1rm = mutableMapOf<String, Double>()
        val lastE1rm = mutableMapOf<String, Double>()
        for (workout in workoutsInQuarter.sortedBy { it.startedAt }) {
            val sets = completedSetsByWorkoutId[workout.id].orEmpty()
                .filter { it.setType != SetType.WARMUP && it.weightKg > 0.0 }
            for (set in sets) {
                val ex = exercisesById[set.exerciseId] ?: continue
                val matchedKey = mainLiftKeywords.firstOrNull { ex.name.contains(it, ignoreCase = true) } ?: continue
                val e1rm = set.weightKg * (1 + set.reps / 30.0)
                if (matchedKey !in firstE1rm) firstE1rm[matchedKey] = e1rm
                if (e1rm > (lastE1rm[matchedKey] ?: 0.0)) lastE1rm[matchedKey] = e1rm
            }
        }
        val firstJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject { firstE1rm.forEach { (k, v) -> put(k, "%.1f".format(java.util.Locale.US, v).toDouble()) } })
        val lastJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject { lastE1rm.forEach { (k, v) -> put(k, "%.1f".format(java.util.Locale.US, v).toDouble()) } })

        // Body weight delta
        val measurements = bodyMeasurementDao.getAllAsc()
            .filter { it.weightKg != null && it.date in quarterStartMs until quarterEndMs }
        val bodyWeightDelta = if (measurements.size >= 2) {
            val sorted = measurements.sortedBy { it.date }
            sorted.last().weightKg!! - sorted.first().weightKg!!
        } else null

        // Eventy
        val events = eventDao.getInRange(quarterStartMs, quarterEndMs - 1)
        val prCount = events.count { it.type == TrainingEventType.PR_SET }
        val planChanges = events.count { it.type == TrainingEventType.PLAN_START || it.type == TrainingEventType.PLAN_END }
        val deloadCount = events.count { it.type == TrainingEventType.DELOAD_DETECTED }

        // Highlights: top 5 PR-ów (po e1RM zmianie)
        val highlights = events
            .filter { it.type == TrainingEventType.PR_SET }
            .sortedByDescending { it.e1rmKg ?: 0.0 }
            .take(5)
            .map { "${it.exerciseName ?: "?"}: ${it.weightKg ?: 0} kg × ${it.reps ?: 0}" }
        val highlightsJson = json.encodeToString(
            kotlinx.serialization.json.JsonArray.serializer(),
            buildJsonArray { highlights.forEach { add(it) } }
        )

        return QuarterlyRollup(
            quarterStartMs = quarterStartMs,
            totalVolumeKg = totalVolume,
            sessionsCount = workoutsInQuarter.size,
            mainLiftsE1rmStartJson = firstJson,
            mainLiftsE1rmEndJson = lastJson,
            bodyWeightDeltaKg = bodyWeightDelta,
            prCount = prCount,
            planChanges = planChanges,
            deloadCount = deloadCount,
            highlightsJson = highlightsJson
        )
    }

    companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
        const val MS_PER_WEEK = 7L * MS_PER_DAY

        /** Poniedziałek 00:00 dla danej daty. */
        fun startOfWeek(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = epochMs
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        /** 1 dzień miesiąca 00:00. */
        fun startOfMonth(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = epochMs
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun startOfNextMonth(monthStartMs: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs }
            cal.add(Calendar.MONTH, 1)
            return cal.timeInMillis
        }

        fun startOfPreviousMonth(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = epochMs
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -1)
            }
            return cal.timeInMillis
        }

        /** 1 stycznia / 1 kwietnia / 1 lipca / 1 października 00:00. */
        fun startOfQuarter(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val month = cal.get(Calendar.MONTH)
            val quarterStartMonth = (month / 3) * 3
            cal.set(Calendar.MONTH, quarterStartMonth)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun startOfNextQuarter(quarterStartMs: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = quarterStartMs }
            cal.add(Calendar.MONTH, 3)
            return cal.timeInMillis
        }

        fun startOfPreviousQuarter(epochMs: Long): Long {
            val q = startOfQuarter(epochMs)
            val cal = Calendar.getInstance().apply { timeInMillis = q }
            cal.add(Calendar.MONTH, -3)
            return cal.timeInMillis
        }
    }
}
