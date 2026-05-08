package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * Test porównawczy v1.11.43 — `computeRecoveryByMuscleFromSnapshot` musi
 * dawać identyczne wyniki co stara `StatsRepository.recoveryByMuscle()`.
 */
class RecoveryByMuscleFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun ex(id: Long, muscle: MuscleGroup) = Exercise(
        id = id, name = "Ex$id", primaryMuscle = muscle, equipment = Equipment.BARBELL
    )
    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )
    private fun set(
        wid: Long, exId: Long, reps: Int = 10, weight: Double = 100.0,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = wid * 100 + exId,
        workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type
    )

    @Test
    fun `pusty snapshot zwraca pustą listę`() {
        val result = computeRecoveryByMuscleFromSnapshot(StatsSnapshot.EMPTY, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `najnowszy trening per mięsień`() {
        val workouts = listOf(
            workout(1, daysAgo = 5),    // chest 5 dni temu
            workout(2, daysAgo = 2)     // chest 2 dni temu (NEWER)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10),
            set(wid = 2, exId = 10)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertEquals(1, result.size)
        assertEquals(MuscleGroup.CHEST, result[0].muscle)
        assertEquals(2, result[0].daysAgo)  // najnowszy
    }

    @Test
    fun `wiele mięśni — każdy z własnym last`() {
        val workouts = listOf(
            workout(1, daysAgo = 3),    // chest
            workout(2, daysAgo = 7)     // back
        )
        val ex = listOf(
            ex(10, MuscleGroup.CHEST),
            ex(20, MuscleGroup.BACK)
        )
        val sets = listOf(
            set(wid = 1, exId = 10),
            set(wid = 2, exId = 20)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertEquals(2, result.size)
        // Sortowane desc po daysAgo — back (7) > chest (3)
        assertEquals(MuscleGroup.BACK, result[0].muscle)
        assertEquals(7, result[0].daysAgo)
        assertEquals(MuscleGroup.CHEST, result[1].muscle)
        assertEquals(3, result[1].daysAgo)
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane`() {
        val workouts = listOf(
            workout(1, daysAgo = 5),  // tylko WARMUP — nie liczone
            workout(2, daysAgo = 2)   // niewykonane — nie liczone
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, type = SetType.WARMUP),
            set(wid = 2, exId = 10, completed = false)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertTrue(result.isEmpty())  // brak completed working sets
    }

    @Test
    fun `niezakonczony trening pomijany`() {
        val workouts = listOf(
            workout(1, daysAgo = 5, finished = true),
            workout(2, daysAgo = 1, finished = false)  // active
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10),
            set(wid = 2, exId = 10)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertEquals(1, result.size)
        assertEquals(5, result[0].daysAgo)  // tylko workout 1, nie 2 (active)
    }

    @Test
    fun `set z usunietym cwiczeniem pomijany`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10),
            set(wid = 1, exId = 999)  // exercise nie istnieje
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertEquals(1, result.size)
        assertEquals(MuscleGroup.CHEST, result[0].muscle)
    }

    @Test
    fun `lastTrainingMillis zapisany prawidlowo`() {
        val workouts = listOf(workout(1, daysAgo = 3))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(wid = 1, exId = 10))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeRecoveryByMuscleFromSnapshot(snapshot, now)
        assertEquals(workouts[0].startedAt, result[0].lastTrainingMillis)
    }
}
