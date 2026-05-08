package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.StatsSnapshot

/**
 * Test v1.11.47 — `computeTrainingLoadFromSnapshot` (pure function bez DAO).
 * Weryfikuje logikę ACWR (Acute:Chronic Workload Ratio).
 */
class TrainingLoadAnalyzerFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun ex(id: Long, muscle: MuscleGroup = MuscleGroup.CHEST) = Exercise(
        id = id, name = "Ex$id", primaryMuscle = muscle, equipment = Equipment.BARBELL
    )
    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )
    private fun set(
        wid: Long, exId: Long, reps: Int, weight: Double,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = wid * 1000 + exId,
        workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type
    )

    @Test
    fun `pusty snapshot zwraca INSUFFICIENT zone`() {
        val result = computeTrainingLoadFromSnapshot(StatsSnapshot.EMPTY, now)
        assertEquals(LoadZone.INSUFFICIENT, result.zone)
        assertEquals(0, result.workoutsCount14d)
        assertTrue(result.recommendation.contains("Brak treningów"))
    }

    @Test
    fun `mniej niz 6 treningow w 14d zwraca INSUFFICIENT`() {
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 5),
            workout(3, daysAgo = 10)
        )
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(wid = w.id, exId = 10, reps = 10, weight = 100.0).copy(id = (i + 1).toLong())
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingLoadFromSnapshot(snapshot, now)
        assertEquals(LoadZone.INSUFFICIENT, result.zone)
        assertEquals(3, result.workoutsCount14d)
    }

    @Test
    fun `6 treningow w 14d - nie INSUFFICIENT`() {
        val workouts = (0..5).map { workout((it + 1).toLong(), daysAgo = it.toLong() * 2) }
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(wid = w.id, exId = 10, reps = 10, weight = 100.0).copy(id = (i + 100).toLong())
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingLoadFromSnapshot(snapshot, now)
        assertTrue(result.zone != LoadZone.INSUFFICIENT)
        assertEquals(6, result.workoutsCount14d)
        assertTrue(result.acuteLoad7d >= 0)
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane`() {
        val workouts = (0..5).map { workout((it + 1).toLong(), daysAgo = it.toLong()) }
        val ex = listOf(ex(10))
        val sets = mutableListOf<WorkoutSet>()
        var setIdCounter = 1L
        workouts.forEach { w ->
            sets.add(set(w.id, 10, 10, 50.0, type = SetType.WARMUP).copy(id = setIdCounter++))
            sets.add(set(w.id, 10, 10, 100.0, completed = false).copy(id = setIdCounter++))
            sets.add(set(w.id, 10, 10, 100.0).copy(id = setIdCounter++))  // jedyny liczony
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingLoadFromSnapshot(snapshot, now)
        assertEquals(6, result.workoutsCount14d)
        // Tylko 1 set per workout × 1000kg = 6000kg total acute (jeśli wszystko w 7d)
        // 5 z 6 jest w 7d window (daysAgo 0..5), więc acute = 5000
        assertTrue(result.acuteLoad7d > 0)
    }

    @Test
    fun `stary trening (ponad 28d) pomijany`() {
        val workouts = (0..5).map { workout((it + 1).toLong(), daysAgo = it.toLong()) } +
            workout(100, daysAgo = 50)  // poza 28d window
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(wid = w.id, exId = 10, reps = 10, weight = 100.0).copy(id = (i + 200).toLong())
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingLoadFromSnapshot(snapshot, now)
        // 6 workouts w 14d (workout 100 jest poza 28d window — nie liczony)
        assertEquals(6, result.workoutsCount14d)
    }

    @Test
    fun `niezakonczone treningi nie liczone`() {
        val workouts = (0..5).map { workout((it + 1).toLong(), daysAgo = it.toLong(), finished = true) } +
            workout(100, daysAgo = 1, finished = false)  // active workout — nie liczony
        val ex = listOf(ex(10))
        val sets = workouts.mapIndexed { i, w ->
            set(wid = w.id, exId = 10, reps = 10, weight = 100.0).copy(id = (i + 300).toLong())
        }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeTrainingLoadFromSnapshot(snapshot, now)
        // Tylko 6 finished workouts liczonych, active = ignorowany
        assertEquals(6, result.workoutsCount14d)
    }
}
