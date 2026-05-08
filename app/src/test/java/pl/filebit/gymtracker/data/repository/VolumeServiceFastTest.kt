package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * Test v1.11.48 — `computeMuscleVolumeReportFromSnapshot` pure function.
 * Weryfikuje liczenie volume per partia z normalizacją na tygodnie.
 */
class VolumeServiceFastTest {

    // 2024-05-06 14:13 UTC = poniedziałek (week start in ISO)
    // Use this so cutoff = monday 00:00 UTC (deterministic)
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
        id: Long, wid: Long, exId: Long,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = id, workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = 10, weightKg = 100.0,
        isCompleted = completed, setType = type
    )

    @Test
    fun `pusty snapshot zwraca raport ze wszystkimi miesniami 0 setow`() {
        val result = computeMuscleVolumeReportFromSnapshot(
            weeks = 1, snapshot = StatsSnapshot.EMPTY,
            now = now, goal = TrainingGoal.HYPERTROPHY
        )
        // reportWeeklyVolume zwraca raporty dla wszystkich tracked muscle groups
        assertTrue(result.isNotEmpty())
        result.forEach { assertEquals(0, it.sets) }
    }

    @Test
    fun `sety w bieżącym tygodniu zliczone`() {
        // Use workout 0h ago so we're definitely in current week
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 3600000, finishedAt = now - 1800000)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = (1..3).map { set(id = it.toLong(), wid = 1, exId = 10) }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleVolumeReportFromSnapshot(1, snapshot, now, TrainingGoal.HYPERTROPHY)
        val chest = result.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(3, chest.sets)
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane`() {
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 3600000, finishedAt = now - 1800000)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, type = SetType.WARMUP),
            set(id = 2, wid = 1, exId = 10, completed = false),
            set(id = 3, wid = 1, exId = 10)  // tylko ten liczony
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleVolumeReportFromSnapshot(1, snapshot, now, TrainingGoal.HYPERTROPHY)
        val chest = result.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1, chest.sets)
    }

    @Test
    fun `niezakonczone treningi pomijane`() {
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 3600000, finishedAt = now - 1800000),
            Workout(id = 2, startedAt = now - 1800000, finishedAt = null)  // active
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10),       // liczone
            set(id = 2, wid = 2, exId = 10),       // pomijane (active)
            set(id = 3, wid = 2, exId = 10)        // pomijane (active)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleVolumeReportFromSnapshot(1, snapshot, now, TrainingGoal.HYPERTROPHY)
        val chest = result.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1, chest.sets)
    }

    @Test
    fun `set z usunietym cwiczeniem pomijany`() {
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 3600000, finishedAt = now - 1800000)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10),       // ok
            set(id = 2, wid = 1, exId = 999)       // exercise nie istnieje
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleVolumeReportFromSnapshot(1, snapshot, now, TrainingGoal.HYPERTROPHY)
        val chest = result.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1, chest.sets)
    }

    @Test
    fun `wiele tygodni - srednia tygodniowa`() {
        // 4 sety w bieżącym tygodniu, weeks=2 → 4/2 = 2 setów średnio
        val workouts = listOf(
            Workout(id = 1, startedAt = now - 3600000, finishedAt = now - 1800000)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = (1..4).map { set(id = it.toLong(), wid = 1, exId = 10) }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeMuscleVolumeReportFromSnapshot(2, snapshot, now, TrainingGoal.HYPERTROPHY)
        val chest = result.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(2, chest.sets)  // 4/2
    }
}
