package pl.filebit.gymtracker.ai

import android.util.Log
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.11.60 — Backfill historycznych eventów z istniejących danych użytkownika.
 *
 * Wywoływany jednorazowo przy starcie aplikacji (gdy table training_events jest
 * pusty ale są zakończone treningi). Skanuje cały trening po kolei i symuluje
 * detekcję eventów tak jakby były wykrywane na bieżąco — z narastającym
 * historicalSets per ćwiczenie.
 *
 * Wytwarza eventy: PR_SET, INJURY, GAP_RESUMED.
 */
@Singleton
class EventBackfillService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val eventDao: TrainingEventDao
) {

    /**
     * Wykonaj backfill jeśli potrzebny. Idempotentny — jeśli już są eventy,
     * nie wykonuje. Bezpieczny do wywołania przy każdym starcie.
     */
    suspend fun backfillIfNeeded(): Int {
        val existingEventsCount = eventDao.count()
        if (existingEventsCount > 0) {
            Log.d("EventBackfill", "Skip - juz $existingEventsCount eventow w bazie")
            return 0
        }

        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedBy { it.startedAt }
        if (finishedWorkouts.isEmpty()) {
            Log.d("EventBackfill", "Skip - brak zakonczonych treningow")
            return 0
        }

        Log.d("EventBackfill", "Start backfill ${finishedWorkouts.size} treningow")

        // Cache: exerciseId -> name (jednorazowe pobranie)
        val allExercises = exerciseDao.getAll()
        val exerciseNamesById = allExercises.associate { it.id to it.name }

        // Wszystkie sety jednorazowo (zamiast getForWorkout per workout)
        val setsByWorkoutId = mutableMapOf<Long, List<pl.filebit.gymtracker.data.entity.WorkoutSet>>()
        for (w in finishedWorkouts) {
            setsByWorkoutId[w.id] = setDao.getForWorkout(w.id)
        }

        // Narastający historicalSets per exerciseId (rośnie iteracyjnie)
        val historicalSets = mutableMapOf<Long, MutableList<pl.filebit.gymtracker.data.entity.WorkoutSet>>()
        val allEvents = mutableListOf<TrainingEvent>()
        val previousFinishedSoFar = mutableListOf<pl.filebit.gymtracker.data.entity.Workout>()

        for (w in finishedWorkouts) {
            val sets = setsByWorkoutId[w.id].orEmpty()

            // PR detection - na podstawie historycznych setów ZE WSZYSTKICH wcześniejszych workoutów
            val prs = detectPrsFromWorkout(
                workout = w,
                workoutSets = sets,
                historicalSetsByExerciseId = historicalSets,
                exerciseNamesById = exerciseNamesById
            )
            allEvents.addAll(prs)

            // INJURY detection
            allEvents.addAll(detectInjuryFromWorkout(w))

            // GAP_RESUMED detection (>14 dni od ostatniego)
            allEvents.addAll(detectGapResumed(w, previousFinishedSoFar))

            // Aktualizuj cache historycznych setów
            sets.filter { it.isCompleted && it.setType != SetType.WARMUP && it.weightKg > 0.0 && it.reps > 0 }
                .forEach { s ->
                    historicalSets.getOrPut(s.exerciseId) { mutableListOf() }.add(s)
                }
            previousFinishedSoFar.add(w)
        }

        if (allEvents.isNotEmpty()) {
            eventDao.insertAll(allEvents)
            Log.d("EventBackfill", "Wstawiono ${allEvents.size} eventow z historii")
        }
        return allEvents.size
    }

    /**
     * v1.11.77: backfill DELOAD_DETECTED z historii tygodni.
     *
     * Iteruje po wszystkich tygodniach (poniedziałek 00:00 jako start), liczy
     * volume per tydzień, dla każdego tygodnia od 5-tego sprawdza czy volume
     * < 60% mediany 4 wcześniejszych tygodni (tylko z volume > 0).
     *
     * Idempotentny — jeśli już są DELOAD eventy, nic nie robi.
     */
    suspend fun backfillDeloadsIfNeeded(): Int {
        val existingDeloads = eventDao.getByType(TrainingEventType.DELOAD_DETECTED, limit = 1)
        if (existingDeloads.isNotEmpty()) {
            Log.d("EventBackfill", "Skip DELOAD - juz sa eventy DELOAD_DETECTED")
            return 0
        }
        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedBy { it.startedAt }
        if (finishedWorkouts.size < 6) return 0  // potrzeba min 6 tyg historii

        // Wszystkie sety jednorazowo
        val setsByWorkoutId = mutableMapOf<Long, List<WorkoutSet>>()
        for (w in finishedWorkouts) {
            setsByWorkoutId[w.id] = setDao.getForWorkout(w.id)
        }

        // Volume per workout (tylko sety NORMAL/DROP/FAILURE/AMRAP, bez WARMUP)
        fun volumeOf(w: Workout): Double {
            return setsByWorkoutId[w.id].orEmpty()
                .filter { it.isCompleted && it.setType != SetType.WARMUP && it.weightKg > 0.0 && it.reps > 0 }
                .sumOf { it.weightKg * it.reps }
        }

        // Grupuj per tydzień (poniedziałek 00:00). Mapa: weekStart → suma volume
        val volumeByWeek = sortedMapOf<Long, Double>()
        for (w in finishedWorkouts) {
            val weekStart = startOfWeekFor(w.finishedAt ?: w.startedAt)
            volumeByWeek[weekStart] = (volumeByWeek[weekStart] ?: 0.0) + volumeOf(w)
        }
        val weekStarts = volumeByWeek.keys.toList()
        if (weekStarts.size < 5) return 0

        val results = mutableListOf<TrainingEvent>()
        // Dla każdego tygodnia od indeksu 4 (5 tydzień) sprawdzamy deload
        for (i in 4 until weekStarts.size) {
            val current = volumeByWeek[weekStarts[i]] ?: continue
            val prior4 = (i - 4 until i).mapNotNull { volumeByWeek[weekStarts[it]] }
                .filter { it > 0.0 }
            if (prior4.size < 3) continue

            val median = prior4.sorted().let { sorted ->
                if (sorted.size % 2 == 1) sorted[sorted.size / 2]
                else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            }
            if (median <= 0.0) continue
            val ratio = current / median
            if (ratio < 0.60) {
                results.add(
                    TrainingEvent(
                        date = weekStarts[i],
                        type = TrainingEventType.DELOAD_DETECTED,
                        weeksContext = prior4.size,
                        notes = "Volume spadł do ${"%.0f".format(ratio * 100)}% mediany ostatnich ${prior4.size} tyg"
                    )
                )
            }
        }

        if (results.isNotEmpty()) {
            eventDao.insertAll(results)
            Log.d("EventBackfill", "DELOAD backfill: wstawiono ${results.size} eventow")
        } else {
            Log.d("EventBackfill", "DELOAD backfill: brak deloadow w historii")
        }
        return results.size
    }

    /**
     * v1.11.77: backfill CYCLE_MILESTONE — co 25/50/100/200/500/1000 treningów.
     *
     * Idempotentny — jeśli już są MILESTONE eventy, nic nie robi.
     */
    suspend fun backfillMilestonesIfNeeded(): Int {
        val existingMilestones = eventDao.getByType(TrainingEventType.CYCLE_MILESTONE, limit = 1)
        if (existingMilestones.isNotEmpty()) {
            Log.d("EventBackfill", "Skip MILESTONE - juz sa eventy CYCLE_MILESTONE")
            return 0
        }
        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedBy { it.startedAt }

        val milestones = listOf(25, 50, 100, 200, 500, 1000)
        val results = mutableListOf<TrainingEvent>()
        for (n in milestones) {
            if (finishedWorkouts.size >= n) {
                val w = finishedWorkouts[n - 1]
                results.add(
                    TrainingEvent(
                        date = w.finishedAt ?: w.startedAt,
                        type = TrainingEventType.CYCLE_MILESTONE,
                        workoutId = w.id,
                        notes = "$n ukończonych treningów"
                    )
                )
            }
        }

        if (results.isNotEmpty()) {
            eventDao.insertAll(results)
            Log.d("EventBackfill", "MILESTONE backfill: wstawiono ${results.size} eventow")
        }
        return results.size
    }

    /** Poniedziałek 00:00 dla danego epoch ms. */
    private fun startOfWeekFor(epochMs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMs
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
