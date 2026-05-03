package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {
    fun observeActive(): Flow<Workout?> = workoutDao.observeActive()
    fun observeAll(): Flow<List<Workout>> = workoutDao.observeAll()
    fun observeRecent(limit: Int = 10): Flow<List<Workout>> = workoutDao.observeRecent(limit)
    fun observeSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>> =
        setDao.observeForWorkout(workoutId)

    suspend fun getWorkout(id: Long): Workout? = workoutDao.getById(id)
    suspend fun getSetsForWorkout(id: Long): List<WorkoutSet> = setDao.getForWorkout(id)
    suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)

    /**
     * Tworzy nowy trening jeśli nie ma aktywnego, w przeciwnym razie zwraca aktywny.
     * Jeśli aktywny już istnieje, fromPlanId/fromDayOfWeek są IGNOROWANE.
     */
    suspend fun startOrResume(fromPlanId: Long? = null, fromDayOfWeek: Int? = null): Workout {
        workoutDao.getActive()?.let { return it }
        val newWorkout = Workout(
            startedAt = System.currentTimeMillis(),
            fromPlanId = fromPlanId,
            fromDayOfWeek = fromDayOfWeek
        )
        val id = workoutDao.insert(newWorkout)
        return newWorkout.copy(id = id)
    }

    suspend fun finish(workoutId: Long) {
        val w = workoutDao.getById(workoutId) ?: return
        if (w.finishedAt != null) return
        // Jeśli trening jest pusty (brak setów) -> usuń zamiast zapisywać
        val sets = setDao.getForWorkout(workoutId)
        if (sets.isEmpty()) {
            workoutDao.deleteById(workoutId)
            return
        }
        workoutDao.update(w.copy(finishedAt = System.currentTimeMillis()))
    }

    suspend fun discardActive() {
        val active = workoutDao.getActive() ?: return
        workoutDao.deleteById(active.id)
    }

    suspend fun deleteWorkout(id: Long) = workoutDao.deleteById(id)

    suspend fun setAiSummary(workoutId: Long, summary: String) {
        val w = workoutDao.getById(workoutId) ?: return
        workoutDao.update(
            w.copy(
                aiSummary = summary,
                aiSummaryGeneratedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Zapisuje post-workout feedback usera: jak się czuł (1-5) + ewentualny ból
     * (partia mięśniowa + opis). Wywoływane po kliknięciu Save w PostWorkoutFeedbackSheet.
     */
    suspend fun setPostWorkoutFeedback(
        workoutId: Long,
        wellbeingRating: Int?,
        painArea: String?,
        painNotes: String?
    ) {
        val w = workoutDao.getById(workoutId) ?: return
        workoutDao.update(
            w.copy(
                wellbeingRating = wellbeingRating,
                painArea = painArea,
                painNotes = painNotes
            )
        )
    }

    /**
     * Dodaje nową serię. Auto-numeracja setNumber w obrębie ćwiczenia,
     * orderIndex liczony tak, że ćwiczenie zachowuje swoją grupę.
     */
    suspend fun addSet(
        workoutId: Long,
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        setType: pl.filebit.gymtracker.data.entity.SetType = pl.filebit.gymtracker.data.entity.SetType.NORMAL
    ): WorkoutSet {
        val sets = setDao.getForWorkout(workoutId)
        val existingForExercise = sets.filter { it.exerciseId == exerciseId }
        val orderIndex = existingForExercise.firstOrNull()?.orderIndex
            ?: ((setDao.getMaxOrderIndex(workoutId) ?: -1) + 1)
        val setNumber = (existingForExercise.maxOfOrNull { it.setNumber } ?: 0) + 1

        val set = WorkoutSet(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            orderIndex = orderIndex,
            reps = reps,
            weightKg = weightKg,
            setType = setType,
            isCompleted = true
        )
        val id = setDao.insert(set)
        return set.copy(id = id)
    }

    /**
     * Dodaje serię z planu jako placeholder (isCompleted=false).
     * Logika setNumber/orderIndex identyczna jak w addSet.
     */
    suspend fun addPlannedSet(
        workoutId: Long,
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        setType: pl.filebit.gymtracker.data.entity.SetType = pl.filebit.gymtracker.data.entity.SetType.NORMAL
    ): WorkoutSet {
        val sets = setDao.getForWorkout(workoutId)
        val existingForExercise = sets.filter { it.exerciseId == exerciseId }
        val orderIndex = existingForExercise.firstOrNull()?.orderIndex
            ?: ((setDao.getMaxOrderIndex(workoutId) ?: -1) + 1)
        val setNumber = (existingForExercise.maxOfOrNull { it.setNumber } ?: 0) + 1

        val set = WorkoutSet(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            orderIndex = orderIndex,
            reps = reps,
            weightKg = weightKg,
            setType = setType,
            isCompleted = false
        )
        val id = setDao.insert(set)
        return set.copy(id = id)
    }

    suspend fun updateSet(set: WorkoutSet) = setDao.update(set)
    suspend fun updateWorkout(workout: Workout) = workoutDao.update(workout)
    suspend fun deleteSet(set: WorkoutSet) = setDao.delete(set)

    suspend fun getSet(id: Long): WorkoutSet? = setDao.getById(id)

    /**
     * Coach mode: zatwierdź planowaną serię, podając rzeczywistą liczbę powtórzeń.
     * actualReps może być inne niż planowane (user mógł zrobić mniej lub więcej).
     */
    suspend fun confirmSet(setId: Long, actualReps: Int) {
        val set = setDao.getById(setId) ?: return
        setDao.update(set.copy(reps = actualReps, isCompleted = true))
    }

    /** Usuwa wszystkie serie danego ćwiczenia z aktualnego treningu. */
    suspend fun removeExerciseFromWorkout(workoutId: Long, exerciseId: Long) {
        setDao.deleteAllForExerciseInWorkout(workoutId, exerciseId)
    }

    /** Auto-fill: ostatnia (zakończona) seria danego ćwiczenia. */
    suspend fun getLastSetForExercise(exerciseId: Long): WorkoutSet? =
        setDao.getLastSetForExercise(exerciseId)
}
