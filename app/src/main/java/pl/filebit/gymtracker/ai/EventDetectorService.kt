package pl.filebit.gymtracker.ai

import android.util.Log
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEventType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.11.59 — Service łączący pure functions [EventDetector] z bazą danych.
 *
 * Wywoływany przez hooks:
 * - WorkoutRepository.finish() → PR detection + GAP detection
 * - WorkoutRepository.setPostWorkoutFeedback() → INJURY detection
 *
 * Anti-duplicate: dla PR-ów sprawdza czy ten sam (exerciseId, weight, reps) już
 * istnieje w bazie eventów (DAO.countExistingPr), żeby ponowne wywołanie nie
 * tworzyło duplikatów.
 */
@Singleton
class EventDetectorService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val eventDao: TrainingEventDao,
    private val statsRepository: pl.filebit.gymtracker.data.repository.StatsRepository,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService
) {

    /**
     * Wywołane po finalizacji treningu (finish). Wykrywa PR-y + powrót po przerwie.
     */
    suspend fun onWorkoutFinished(workoutId: Long) {
        val w = workoutDao.getById(workoutId) ?: return
        if (w.finishedAt == null) return

        // Sety z tego workoutu
        val sets = setDao.getForWorkout(workoutId)

        // Historyczne sety z innych zakończonych workoutów (do PR comparison)
        val allFinished = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.id != workoutId }
        val historicalSets = allFinished.flatMap { setDao.getForWorkout(it.id) }
            .filter { it.isCompleted && it.setType != SetType.WARMUP }
            .groupBy { it.exerciseId }

        // Mapa exerciseId -> nazwa
        val exerciseIds = sets.map { it.exerciseId }.distinct()
        val exerciseNames = exerciseIds.associateWith { id ->
            exerciseDao.getById(id)?.name.orEmpty()
        }.filterValues { it.isNotBlank() }

        val prs = detectPrsFromWorkout(
            workout = w,
            workoutSets = sets,
            historicalSetsByExerciseId = historicalSets,
            exerciseNamesById = exerciseNames
        )

        // Anti-duplicate per PR
        val newPrs = prs.filter { pr ->
            val exId = pr.exerciseId ?: return@filter false
            val w_ = pr.weightKg ?: return@filter false
            val r = pr.reps ?: return@filter false
            eventDao.countExistingPr(exId, w_, r) == 0
        }
        if (newPrs.isNotEmpty()) {
            eventDao.insertAll(newPrs)
            Log.d("EventDetector", "Wykryto ${newPrs.size} nowych PR-ów dla workoutu $workoutId")
        }

        // Gap_resumed
        val gapEvents = detectGapResumed(w, allFinished)
        if (gapEvents.isNotEmpty()) {
            eventDao.insertAll(gapEvents)
            Log.d("EventDetector", "Wykryto GAP_RESUMED dla workoutu $workoutId")
        }

        // v1.11.65: DELOAD detection - ocenia OSTATNI ZAKOŃCZONY tydzień
        // (weeksAgo=1) vs 4 jeszcze starsze. Bieżący niezakończony tydzień
        // jest pomijany (za mało danych zeby ocenic). Potrzebujemy 6 tygodni
        // historii: bieżący + ostatni zakończony + 4 do mediany.
        runCatching {
            val snapshot = statsCacheService.snapshot()
            val volumePerWeek6 = statsRepository.volumePerWeekFast(weeks = 6, snapshot = snapshot)
            val currentWeekStart = startOfCurrentWeek()
            val deloadEvents = detectDeloadFromWeeklyVolumes(
                weeklyVolumes = volumePerWeek6,
                currentWeekStartMs = currentWeekStart
            )
            // Anti-duplicate: event jest datowany na poniedziałek POPRZEDNIEGO
            // (zakończonego) tygodnia - sprawdzamy po tej dacie
            val msPerWeek = 7L * 24 * 60 * 60 * 1000
            val lastWeekStart = currentWeekStart - msPerWeek
            val recentDeloads = eventDao.getByType(TrainingEventType.DELOAD_DETECTED, limit = 5)
            val alreadyHasLastWeek = recentDeloads.any { it.date == lastWeekStart }
            if (deloadEvents.isNotEmpty() && !alreadyHasLastWeek) {
                eventDao.insertAll(deloadEvents)
                Log.d("EventDetector", "Wykryto DELOAD_DETECTED dla ostatniego zakończonego tygodnia")
            }
        }
    }

    /** Poniedzialek bieżącego tygodnia o 00:00 (epoch ms). */
    private fun startOfCurrentWeek(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * v1.11.63: Wywoływane po stworzeniu nowego planu (PlanRepository.upsertPlan
     * gdy id=0 przed insert).
     */
    suspend fun onPlanCreated(planId: Long, planName: String) {
        eventDao.insert(
            pl.filebit.gymtracker.data.entity.TrainingEvent(
                date = System.currentTimeMillis(),
                type = pl.filebit.gymtracker.data.entity.TrainingEventType.PLAN_START,
                planId = planId,
                planName = planName,
                notes = "Aktywowano plan: $planName"
            )
        )
        Log.d("EventDetector", "PLAN_START event dla planId=$planId ($planName)")
    }

    /**
     * v1.11.63: Wywoływane po usunięciu planu.
     */
    suspend fun onPlanDeleted(planId: Long, planName: String) {
        eventDao.insert(
            pl.filebit.gymtracker.data.entity.TrainingEvent(
                date = System.currentTimeMillis(),
                type = pl.filebit.gymtracker.data.entity.TrainingEventType.PLAN_END,
                planId = planId,
                planName = planName,
                notes = "Zakończono plan: $planName"
            )
        )
        Log.d("EventDetector", "PLAN_END event dla planId=$planId ($planName)")
    }

    /**
     * Wywołane po zapisaniu post-workout feedback (wellbeing + painArea).
     * Wykrywa INJURY (jeśli painArea wypełniony).
     */
    suspend fun onPostWorkoutFeedback(workoutId: Long) {
        val w = workoutDao.getById(workoutId) ?: return
        // Anti-duplicate: sprawdź czy event INJURY już istnieje dla tego workoutu
        val existing = eventDao.getForWorkout(workoutId)
            .filter { it.type == TrainingEventType.INJURY }
        if (existing.isNotEmpty()) return

        val events = detectInjuryFromWorkout(w)
        if (events.isNotEmpty()) {
            eventDao.insertAll(events)
            Log.d("EventDetector", "Wykryto INJURY dla workoutu $workoutId (${w.painArea})")
        }
    }
}
