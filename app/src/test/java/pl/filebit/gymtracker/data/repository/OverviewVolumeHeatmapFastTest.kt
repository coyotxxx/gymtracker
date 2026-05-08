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
 * Test porównawczy v1.11.42 — `computeOverviewFromSnapshot`,
 * `computeVolumePerWeekFromSnapshot`, `computeCalendarHeatmapFromSnapshot`.
 */
class OverviewVolumeHeatmapFastTest {

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
        type: SetType = SetType.NORMAL, completed: Boolean = true,
        createdAt: Long? = null
    ) = WorkoutSet(
        id = wid * 100 + exId,
        workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type,
        createdAt = createdAt ?: (now - 1)
    )

    // ============= overview =============

    @Test
    fun `overview pusty snapshot zwraca zera`() {
        val o = computeOverviewFromSnapshot(StatsSnapshot.EMPTY, now)
        assertEquals(0, o.totalWorkouts)
        assertEquals(0L, o.totalDurationMillis)
        assertEquals(0.0, o.totalVolumeKg, 0.001)
        assertEquals(0, o.totalSets)
        assertEquals(0.0, o.avgVolumePerWorkout, 0.001)
        assertEquals(0, o.workoutsThisWeek)
        assertEquals(0, o.workoutsThisMonth)
    }

    @Test
    fun `overview liczy total workouts duration volume sets`() {
        val workouts = listOf(workout(1, daysAgo = 2), workout(2, daysAgo = 5))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // 1000
            set(wid = 1, exId = 10, reps = 8, weight = 110.0),   // 880
            set(wid = 2, exId = 10, reps = 5, weight = 150.0)    // 750
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val o = computeOverviewFromSnapshot(snapshot, now)
        assertEquals(2, o.totalWorkouts)
        assertEquals(2630.0, o.totalVolumeKg, 0.001)
        assertEquals(3, o.totalSets)
        assertEquals(1315.0, o.avgVolumePerWorkout, 0.001)  // 2630/2
    }

    @Test
    fun `overview workoutsThisWeek - tylko z 7 dni`() {
        val workouts = listOf(
            workout(1, daysAgo = 2),  // 2 dni temu — w tygodniu
            workout(2, daysAgo = 6),  // 6 dni — w tygodniu
            workout(3, daysAgo = 8)   // 8 dni — poza tygodniem
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = workouts.map { set(wid = it.id, exId = 10, reps = 10, weight = 100.0) }
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val o = computeOverviewFromSnapshot(snapshot, now)
        assertEquals(2, o.workoutsThisWeek)
        assertEquals(3, o.workoutsThisMonth)
    }

    @Test
    fun `overview pomija WARMUP i niewykonane sety`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 50.0, type = SetType.WARMUP),
            set(wid = 1, exId = 10, reps = 10, weight = 100.0, completed = false),
            set(wid = 1, exId = 10, reps = 10, weight = 100.0)  // tylko ten = 1000
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val o = computeOverviewFromSnapshot(snapshot, now)
        assertEquals(1000.0, o.totalVolumeKg, 0.001)
        assertEquals(1, o.totalSets)
    }

    @Test
    fun `overview pomija niezakonczone treningi`() {
        val workouts = listOf(
            workout(1, daysAgo = 1, finished = true),
            workout(2, daysAgo = 1, finished = false)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)  // pomijane
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val o = computeOverviewFromSnapshot(snapshot, now)
        assertEquals(1, o.totalWorkouts)
        assertEquals(1000.0, o.totalVolumeKg, 0.001)
    }

    // ============= volumePerWeek =============

    @Test
    fun `volumePerWeek pusty snapshot zwraca liste zer`() {
        val result = computeVolumePerWeekFromSnapshot(4, StatsSnapshot.EMPTY, now)
        assertEquals(4, result.size)
        result.forEach { assertEquals(0.0, it, 0.001) }
    }

    @Test
    fun `volumePerWeek - bieżący tydzień ma volume`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeVolumePerWeekFromSnapshot(4, snapshot, now)
        assertEquals(4, result.size)
        // Ostatni element to bieżący tydzień
        assertEquals(1000.0, result.last(), 0.001)
    }

    // ============= calendarHeatmap =============

    @Test
    fun `calendarHeatmap pusty snapshot zwraca pustą mapę`() {
        val result = computeCalendarHeatmapFromSnapshot(84, StatsSnapshot.EMPTY, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `calendarHeatmap respektuje cutoff dni`() {
        val workouts = listOf(
            workout(1, daysAgo = 5),
            workout(2, daysAgo = 100)
        )
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)  // poza 84d
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeCalendarHeatmapFromSnapshot(84, snapshot, now)
        // Tylko 1 wpis z workout 1
        assertEquals(1, result.size)
        assertEquals(1000.0, result.values.first(), 0.001)
    }

    @Test
    fun `calendarHeatmap pomija WARMUP i niewykonane`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 50.0, type = SetType.WARMUP),
            set(wid = 1, exId = 10, reps = 10, weight = 100.0)  // 1000
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeCalendarHeatmapFromSnapshot(84, snapshot, now)
        assertEquals(1000.0, result.values.first(), 0.001)
    }
}
