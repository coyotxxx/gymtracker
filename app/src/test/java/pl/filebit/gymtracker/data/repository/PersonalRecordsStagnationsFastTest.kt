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
 * Test porównawczy v1.11.44 — `computeAllPersonalRecordsFromSnapshot`
 * i `computeAllStagnationsFromSnapshot` muszą dawać identyczne wyniki
 * co `StatsRepository.allPersonalRecords()` i `allStagnations()`.
 */
class PersonalRecordsStagnationsFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun ex(id: Long, name: String = "Ex$id", muscle: MuscleGroup = MuscleGroup.CHEST) =
        Exercise(id = id, name = name, primaryMuscle = muscle, equipment = Equipment.BARBELL)

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

    // ============= allPersonalRecords =============

    @Test
    fun `pusty snapshot zwraca pustą listę PR`() {
        val result = computeAllPersonalRecordsFromSnapshot(StatsSnapshot.EMPTY)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `PR per ćwiczenie - max weight i 1RM`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10, "Bench"))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 10, weight = 80.0),
            set(id = 2, wid = 1, exId = 10, reps = 5, weight = 100.0),  // max weight
            set(id = 3, wid = 1, exId = 10, reps = 8, weight = 90.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllPersonalRecordsFromSnapshot(snapshot)
        assertEquals(1, result.size)
        val pr = result[0].pr
        assertEquals(100.0, pr.maxWeightKg, 0.001)
        assertEquals(5, pr.repsAtMaxWeight)
        // Volume = 800 + 500 + 720 = 2020 (jeden workout)
        assertEquals(2020.0, pr.maxVolumeKg, 0.001)
        // Best 1RM = max(epley)
        // 80×10 = 80×(1+10/30)=106.67, 100×5=100×(1+5/30)=116.67, 90×8=90×(1+8/30)=114.0
        // Max = 116.67 → roundToInt(*10)/10 = 116.7
        assertEquals(116.7, pr.estimated1RM, 0.001)
        assertEquals(3, pr.totalSetsLogged)
    }

    @Test
    fun `WARMUP i niewykonane sety pomijane w PR`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, weight = 200.0, type = SetType.WARMUP),
            set(id = 2, wid = 1, exId = 10, weight = 150.0, completed = false),
            set(id = 3, wid = 1, exId = 10, weight = 100.0)  // tylko ten
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllPersonalRecordsFromSnapshot(snapshot)
        assertEquals(1, result.size)
        assertEquals(100.0, result[0].pr.maxWeightKg, 0.001)
    }

    @Test
    fun `cwiczenia bez setow pomijane`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(
            ex(10, "Bench"),
            ex(20, "Squat")  // brak setów
        )
        val sets = listOf(set(id = 1, wid = 1, exId = 10))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllPersonalRecordsFromSnapshot(snapshot)
        assertEquals(1, result.size)
        assertEquals("Bench", result[0].exerciseName)
    }

    @Test
    fun `sortowanie po estimated1RM desc`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(
            ex(10, "Bench"),
            ex(20, "Squat")
        )
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 100.0),  // 1RM ~116.7
            set(id = 2, wid = 1, exId = 20, reps = 5, weight = 200.0)   // 1RM ~233.3
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllPersonalRecordsFromSnapshot(snapshot)
        assertEquals(2, result.size)
        assertEquals("Squat", result[0].exerciseName)  // wyższy 1RM
        assertEquals("Bench", result[1].exerciseName)
    }

    // ============= allStagnations =============

    @Test
    fun `pusty snapshot zwraca pustą listę stagnacji`() {
        val result = computeAllStagnationsFromSnapshot(StatsSnapshot.EMPTY, threshold = 3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mniej niz threshold treningow - empty`() {
        val workouts = listOf(workout(1, daysAgo = 1), workout(2, daysAgo = 5))
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, weight = 100.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllStagnationsFromSnapshot(snapshot, threshold = 3)
        assertTrue(result.isEmpty())  // 2 < 3
    }

    @Test
    fun `3 treningi z ta sama max waga - stagnacja`() {
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 4),
            workout(3, daysAgo = 7)
        )
        val ex = listOf(ex(10, "Bench"))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, weight = 100.0),
            set(id = 3, wid = 3, exId = 10, weight = 100.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllStagnationsFromSnapshot(snapshot, threshold = 3)
        assertEquals(1, result.size)
        assertEquals("Bench", result[0].exerciseName)
        assertEquals(100.0, result[0].stuckAtKg, 0.001)
        assertEquals(3, result[0].workoutsAtSameWeight)
    }

    @Test
    fun `progresja po stagnacji - brak alertu`() {
        // Ostatnie 3 treningi: 110, 105, 100 → progresja, brak stagnacji
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 4),
            workout(3, daysAgo = 7)
        )
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, weight = 110.0),
            set(id = 2, wid = 2, exId = 10, weight = 105.0),
            set(id = 3, wid = 3, exId = 10, weight = 100.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllStagnationsFromSnapshot(snapshot, threshold = 3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `stagnacja tylko na 1 cwiczeniu z 2`() {
        val workouts = listOf(
            workout(1, daysAgo = 1),
            workout(2, daysAgo = 4),
            workout(3, daysAgo = 7)
        )
        val ex = listOf(
            ex(10, "Bench"),
            ex(20, "Squat")
        )
        val sets = listOf(
            // Bench - stagnacja
            set(id = 1, wid = 1, exId = 10, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, weight = 100.0),
            set(id = 3, wid = 3, exId = 10, weight = 100.0),
            // Squat - progresja (tylko w wid=1, najnowszym)
            set(id = 4, wid = 1, exId = 20, weight = 150.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeAllStagnationsFromSnapshot(snapshot, threshold = 3)
        // Bench stagnuje (3 treningi w 100kg), squat ma tylko 1 trening = pomijany (size < threshold)
        assertEquals(1, result.size)
        assertEquals("Bench", result[0].exerciseName)
    }
}
