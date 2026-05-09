package pl.filebit.gymtracker.ai

import android.util.Log
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEvent
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
}
