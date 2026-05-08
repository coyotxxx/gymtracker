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
 * Test v1.11.47 — `computeMuscleRecoveryFromSnapshot` (pure function).
 * Weryfikuje exponential decay regeneracji per partia mięśniowa.
 */
class MuscleRecoveryAnalyzerFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    // Defaultowe parametry — kopia z MuscleRecoveryAnalyzer (private val)
    private val halfLifeHours = mapOf(
        MuscleGroup.QUADS to 36.0,
        MuscleGroup.HAMSTRINGS to 36.0,
        MuscleGroup.GLUTES to 30.0,
        MuscleGroup.BACK to 30.0,
        MuscleGroup.CHEST to 28.0,
        MuscleGroup.SHOULDERS to 24.0,
        MuscleGroup.BICEPS to 20.0,
        MuscleGroup.TRICEPS to 20.0,
        MuscleGroup.CALVES to 18.0,
        MuscleGroup.CORE to 16.0
    )
    private val tracked = halfLifeHours.keys.toList()
    private val maxFatigueSets = 12.0

    private fun ex(id: Long, muscle: MuscleGroup) = Exercise(
        id = id, name = "Ex$id", primaryMuscle = muscle, equipment = Equipment.BARBELL
    )
    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )
    private fun set(
        id: Long, wid: Long, exId: Long, reps: Int = 10, weight: Double = 100.0,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = id,
        workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type
    )

    @Test
    fun `pusty snapshot - wszystkie miesnie 100 percent recovery`() {
        val result = computeMuscleRecoveryFromSnapshot(
            StatsSnapshot.EMPTY, now, tracked, halfLifeHours, maxFatigueSets
        )
        assertEquals(tracked.size, result.statuses.size)
        result.statuses.forEach {
            assertEquals(100, it.recoveryPct)
            assertEquals(TrainingRecommendation.TRAIN_HEAVY, it.recommendation)
        }
        assertEquals(100, result.avgRecoveryPct)
    }

    @Test
    fun `swiezy trening - niskie recovery dla trenowanej partii`() {
        // Trening 2h temu, 12 setów chest
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 7200000, finishedAt = now - 3600000)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = (1..12).map { set(id = it.toLong(), wid = 1, exId = 10) }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleRecoveryFromSnapshot(snapshot, now, tracked, halfLifeHours, maxFatigueSets)
        val chest = result.statuses.first { it.muscle == MuscleGroup.CHEST }
        // 12/12 = 100% fatigue, decay przez 2h przy halfLife=28h
        // fatigue(2) = 1.0 × exp(-2 × 0.693/28) = 1.0 × 0.951 ≈ 0.951
        // recovery = (1 - 0.951) × 100 = 4.9% → 4 lub 5
        assertTrue(chest.recoveryPct < 20)
        assertEquals(TrainingRecommendation.AVOID, chest.recommendation)
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, type = SetType.WARMUP),
            set(id = 2, wid = 1, exId = 10, completed = false)
            // brak completed working sets
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleRecoveryFromSnapshot(snapshot, now, tracked, halfLifeHours, maxFatigueSets)
        // Chest nie ma working setów → traktowane jakby nie był trenowany
        val chest = result.statuses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(100, chest.recoveryPct)
    }

    @Test
    fun `treningi starsze niz 7 dni pomijane`() {
        val workouts = listOf(workout(1, daysAgo = 10))  // poza 7d window
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(id = 1, wid = 1, exId = 10))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleRecoveryFromSnapshot(snapshot, now, tracked, halfLifeHours, maxFatigueSets)
        val chest = result.statuses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(100, chest.recoveryPct)  // jakby nie trenowany
    }

    @Test
    fun `niezakonczony trening pomijany`() {
        val workouts = listOf(workout(1, daysAgo = 1, finished = false))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(id = 1, wid = 1, exId = 10))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleRecoveryFromSnapshot(snapshot, now, tracked, halfLifeHours, maxFatigueSets)
        val chest = result.statuses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(100, chest.recoveryPct)
    }
}
