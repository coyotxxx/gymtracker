package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * Test porównawczy v1.11.39 — `computeMuscleAnalysisFromSnapshot` musi dawać
 * IDENTYCZNE wyniki co stara metoda `StatsRepository.muscleAnalysis()`.
 *
 * Pure function bez DAO — testowalne bez Room/Hilt.
 */
class MuscleAnalysisFastTest {

    private val now = 1715000000000L  // fixed timestamp dla deterministycznego testu
    private val msPerDay = 86_400_000L

    private fun ex(id: Long, muscle: MuscleGroup) = Exercise(
        id = id,
        name = "Ex$id",
        primaryMuscle = muscle,
        equipment = Equipment.BARBELL
    )

    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )

    private fun set(
        wid: Long,
        exId: Long,
        reps: Int,
        weight: Double,
        type: SetType = SetType.NORMAL,
        completed: Boolean = true
    ) = WorkoutSet(
        id = wid * 100 + exId,
        workoutId = wid,
        exerciseId = exId,
        setNumber = 1,
        orderIndex = 0,
        reps = reps,
        weightKg = weight,
        isCompleted = completed,
        setType = type
    )

    @Test
    fun `pusty snapshot zwraca raport z pustymi statusami`() {
        val report = computeMuscleAnalysisFromSnapshot(
            periodDays = 28,
            snapshot = StatsSnapshot.EMPTY,
            now = now
        )
        // Wszystkie mięśnie powinny mieć NEGLECTED (brak treningu)
        assertEquals(MAIN_MUSCLES.size, report.analyses.size)
        report.analyses.forEach { a ->
            assertEquals(MuscleStatus.NEGLECTED, a.status)
            assertEquals(0, a.totalSets)
            assertEquals(0.0, a.volumeKg, 0.001)
        }
    }

    @Test
    fun `proste obliczenia volume i sets per muscle`() {
        val workouts = listOf(
            workout(1, daysAgo = 5),
            workout(2, daysAgo = 3)
        )
        val exercises = listOf(
            ex(10, MuscleGroup.CHEST),
            ex(20, MuscleGroup.BACK)
        )
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // chest 1000kg
            set(wid = 1, exId = 10, reps = 8, weight = 110.0),   // chest 880kg
            set(wid = 2, exId = 20, reps = 5, weight = 150.0),   // back 750kg
        )

        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)

        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        val back = report.analyses.first { it.muscle == MuscleGroup.BACK }

        assertEquals(1880.0, chest.volumeKg, 0.001)
        assertEquals(2, chest.totalSets)
        assertEquals(750.0, back.volumeKg, 0.001)
        assertEquals(1, back.totalSets)
    }

    @Test
    fun `WARMUP sety sa pomijane`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 50.0, type = SetType.WARMUP),  // pomijane
            set(wid = 1, exId = 10, reps = 10, weight = 100.0)  // 1000kg liczone
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1000.0, chest.volumeKg, 0.001)
        assertEquals(1, chest.totalSets)
    }

    @Test
    fun `niewykonane sety sa pomijane`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0, completed = false),  // pomijane
            set(wid = 1, exId = 10, reps = 10, weight = 100.0)  // 1000kg liczone
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1000.0, chest.volumeKg, 0.001)
        assertEquals(1, chest.totalSets)
    }

    @Test
    fun `niezakonczone treningi sa pomijane`() {
        val workouts = listOf(
            workout(1, daysAgo = 1, finished = true),
            workout(2, daysAgo = 1, finished = false)  // active workout
        )
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // 1000kg
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)   // pomijane (workout active)
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1000.0, chest.volumeKg, 0.001)
    }

    @Test
    fun `cutoff respektuje periodDays - stary trening pomijany`() {
        val workouts = listOf(
            workout(1, daysAgo = 5),    // w 28d window
            workout(2, daysAgo = 35)    // poza 28d window
        )
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // liczone
            set(wid = 2, exId = 10, reps = 10, weight = 200.0)   // pomijane (poza okno)
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1000.0, chest.volumeKg, 0.001)
    }

    @Test
    fun `daysSinceLast obliczane prawidlowo`() {
        val workouts = listOf(workout(1, daysAgo = 7))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(7, chest.daysSinceLast)
    }

    @Test
    fun `status NEGLECTED gdy mięsień nie był trenowany`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(set(wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        // BACK nie był trenowany — NEGLECTED
        val back = report.analyses.first { it.muscle == MuscleGroup.BACK }
        assertEquals(MuscleStatus.NEGLECTED, back.status)
        assertNullOrZero(back.daysSinceLast)
    }

    @Test
    fun `set z usunietym cwiczeniem (exId nie istnieje) jest pomijany`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val exercises = listOf(ex(10, MuscleGroup.CHEST))
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // ok, 1000kg
            set(wid = 1, exId = 999, reps = 10, weight = 200.0)  // exercise 999 nie istnieje → pomijane
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        assertEquals(1000.0, chest.volumeKg, 0.001)
        assertEquals(1, chest.totalSets)
    }

    @Test
    fun `total volume rowny sumie volumes per muscle (z minimum coerce)`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val exercises = listOf(
            ex(10, MuscleGroup.CHEST),
            ex(20, MuscleGroup.BACK)
        )
        val sets = listOf(
            set(wid = 1, exId = 10, reps = 10, weight = 100.0),  // 1000
            set(wid = 1, exId = 20, reps = 5, weight = 200.0)    // 1000
        )
        val snapshot = StatsSnapshot.from(workouts, exercises, sets)
        val report = computeMuscleAnalysisFromSnapshot(28, snapshot, now)
        // Total = 2000kg
        assertEquals(2000.0, report.totalVolumeKg, 0.001)
        // Per muscle %: 50/50
        val chest = report.analyses.first { it.muscle == MuscleGroup.CHEST }
        val back = report.analyses.first { it.muscle == MuscleGroup.BACK }
        assertEquals(50, chest.actualPercent)
        assertEquals(50, back.actualPercent)
    }

    private fun assertNullOrZero(value: Int?) {
        if (value != null && value != 0) {
            org.junit.Assert.fail("Expected null or 0 but was $value")
        }
    }
}
