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
 * Test porównawczy v1.11.41 — `computeMuscleEngagementFromSnapshot` musi dawać
 * identyczne wyniki co stara `StatsRepository.muscleEngagement()`.
 */
class MuscleEngagementFastTest {

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
        wid: Long, exId: Long, reps: Int, weight: Double,
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
        val result = computeMuscleEngagementFromSnapshot(28, StatsSnapshot.EMPTY, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane`() {
        val workouts = listOf(workout(1, 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 50.0, type = SetType.WARMUP),
            set(wid = 1, exId = 10, reps = 10, weight = 100.0, completed = false),
            set(wid = 1, exId = 10, reps = 10, weight = 100.0)  // tylko ten liczony = 1000
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(1, result.size)
        assertEquals(MuscleGroup.CHEST, result[0].muscle)
        assertEquals(1000.0, result[0].volumeKg, 0.001)
        assertEquals(1, result[0].totalSets)
        assertEquals(100, result[0].percentOfTotal)
    }

    @Test
    fun `niezakonczone treningi pomijane`() {
        val workouts = listOf(
            workout(1, 1, finished = true),
            workout(2, 1, finished = false)
        )
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)  // pomijane
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(1000.0, result[0].volumeKg, 0.001)
    }

    @Test
    fun `cutoff respektuje periodDays`() {
        val workouts = listOf(workout(1, 5), workout(2, 35))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // w oknie
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)   // poza
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(1000.0, result[0].volumeKg, 0.001)
    }

    @Test
    fun `wiele miesni — sortowanie po volume desc`() {
        val workouts = listOf(workout(1, 1))
        val exercises = listOf(
            ex(10, MuscleGroup.CHEST),
            ex(20, MuscleGroup.BACK),
            ex(30, MuscleGroup.QUADS)
        )
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // 1000 chest
            set(wid = 1, exId = 20, reps = 5, weight = 200.0),   // 1000 back
            set(wid = 1, exId = 30, reps = 8, weight = 100.0)    // 800 quads
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(3, result.size)
        // Pierwsze 2 mają taką samą volume — order by hashmap, ale 3rd musi być QUADS
        assertEquals(MuscleGroup.QUADS, result[2].muscle)
        assertEquals(800.0, result[2].volumeKg, 0.001)
        // Procenty: 1000+1000+800=2800. Chest/back ~35%, quads ~28%
        val total = result.sumOf { it.volumeKg }
        assertEquals(2800.0, total, 0.001)
    }

    @Test
    fun `set z usunietym cwiczeniem pomijany`() {
        val workouts = listOf(workout(1, 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),
            set(wid = 1, exId = 999, reps = 10, weight = 200.0)  // exercise nie istnieje
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(1, result.size)
        assertEquals(1000.0, result[0].volumeKg, 0.001)
    }

    @Test
    fun `procenty sa coerceIn 0-100`() {
        // Skrajny case — bardzo duże volume nie powinno przekroczyć 100%
        val workouts = listOf(workout(1, 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val result = computeMuscleEngagementFromSnapshot(28, snapshot, now)
        assertEquals(100, result[0].percentOfTotal)  // jedyny mięsień = 100%
    }
}
