package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.StatsSnapshot

/**
 * Test v1.11.47 — `computeTrainingPhaseFromSnapshot` (pure function).
 * Weryfikuje detekcję fazy cyklu treningowego (akumulacja/intensyfikacja/deload).
 */
class TrainingPhaseAnalyzerFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun ex(id: Long) = Exercise(
        id = id, name = "Ex$id", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL
    )
    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )
    private fun set(id: Long, wid: Long, exId: Long, weight: Double) = WorkoutSet(
        id = id,
        workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = 10, weightKg = weight,
        isCompleted = true, setType = SetType.NORMAL
    )

    @Test
    fun `pusty snapshot zwraca NO_DATA`() {
        val result = computeTrainingPhaseFromSnapshot(StatsSnapshot.EMPTY, null, now)
        assertEquals(TrainingPhase.NO_DATA, result.phase)
    }

    @Test
    fun `mniej niz 4 treningi w 8w window zwraca NO_DATA`() {
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 5),
            workout(3, daysAgo = 10)
        )
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(id = (i + 1).toLong(), wid = w.id, exId = 10, weight = 100.0)
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingPhaseFromSnapshot(snapshot, null, now)
        assertEquals(TrainingPhase.NO_DATA, result.phase)
    }

    @Test
    fun `4+ treningi w 1 tygodniu - tylko 1 weekIdx - NO_DATA`() {
        // Wszystkie w jednym tygodniu (daysAgo 1..4)
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 2),
            workout(3, daysAgo = 3),
            workout(4, daysAgo = 4)
        )
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(id = (i + 1).toLong(), wid = w.id, exId = 10, weight = 100.0)
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingPhaseFromSnapshot(snapshot, null, now)
        // Tylko 1 unikalny weekIdx → NO_DATA (potrzeba >= 2 tygodni)
        assertEquals(TrainingPhase.NO_DATA, result.phase)
    }

    @Test
    fun `stagnationReport z deloadRecommended zwraca NEEDS_DELOAD`() {
        val workouts = (1..5).map { workout(it.toLong(), daysAgo = it.toLong() * 7) }
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(id = (i + 1).toLong(), wid = w.id, exId = 10, weight = 100.0)
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val stagnation = PlanStagnationReport(
            trends = emptyList(),
            totalAnalyzed = 5,
            stagnatingCount = 3,
            regressingCount = 1,
            deloadRecommended = true,
            recommendation = "Stagnacja"
        )
        val result = computeTrainingPhaseFromSnapshot(snapshot, stagnation, now)
        assertEquals(TrainingPhase.NEEDS_DELOAD, result.phase)
    }
}
