package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * v1.11.59 — Pure functions detekcji eventów.
 */
class EventDetectorTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun workout(id: Long = 1L, daysAgo: Long = 0, finished: Boolean = true, pain: String? = null) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null,
        painArea = pain
    )

    private fun set(
        id: Long, wid: Long, exId: Long, weight: Double, reps: Int,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = id, workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type
    )

    // ============== detectPrsFromWorkout ==============

    @Test
    fun `PR detection - pierwszy raz to ćwiczenie zawsze PR`() {
        val w = workout(id = 1L)
        val sets = listOf(set(1, 1, 100L, 80.0, 5))
        val result = detectPrsFromWorkout(
            workout = w,
            workoutSets = sets,
            historicalSetsByExerciseId = emptyMap(),
            exerciseNamesById = mapOf(100L to "Bench Press")
        )
        assertEquals(1, result.size)
        assertEquals(TrainingEventType.PR_SET, result[0].type)
        assertEquals(80.0, result[0].weightKg!!, 0.001)
        assertEquals(5, result[0].reps!!)
        assertEquals("Bench Press", result[0].exerciseName)
    }

    @Test
    fun `PR detection - nie ma PR jeśli nowy set jest gorszy niż historyczny`() {
        val w = workout(id = 2L)
        val newSets = listOf(set(1, 2, 100L, 70.0, 5))  // e1RM = 70 * (1 + 5/30) = 81.67
        val historical = mapOf(100L to listOf(set(99, 0, 100L, 80.0, 5)))  // e1RM = 93.33
        val result = detectPrsFromWorkout(w, newSets, historical, mapOf(100L to "Bench"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `PR detection - PR po e1RM (cięższy ale mniej powtórzeń)`() {
        // Historyczny: 80 × 5 → e1RM = 93.33
        // Nowy: 90 × 4 → e1RM = 102 → PR
        val w = workout(id = 3L)
        val newSets = listOf(set(1, 3, 100L, 90.0, 4))
        val historical = mapOf(100L to listOf(set(99, 0, 100L, 80.0, 5)))
        val result = detectPrsFromWorkout(w, newSets, historical, mapOf(100L to "Bench"))
        assertEquals(1, result.size)
        assertEquals(90.0, result[0].weightKg!!, 0.001)
        assertEquals(4, result[0].reps!!)
    }

    @Test
    fun `PR detection - ignoruje WARMUP i niedokończone sety`() {
        val w = workout(id = 4L)
        val sets = listOf(
            set(1, 4, 100L, 100.0, 10, type = SetType.WARMUP),  // mocny ale warmup
            set(2, 4, 100L, 90.0, 8, completed = false),         // mocny ale niedokończony
            set(3, 4, 100L, 70.0, 8, type = SetType.NORMAL)      // właściwy set
        )
        val result = detectPrsFromWorkout(w, sets, emptyMap(), mapOf(100L to "Bench"))
        assertEquals(1, result.size)
        assertEquals(70.0, result[0].weightKg!!, 0.001)
        assertEquals(8, result[0].reps!!)
    }

    @Test
    fun `PR detection - bierze NAJLEPSZY set z workoutu (po e1RM)`() {
        val w = workout(id = 5L)
        val sets = listOf(
            set(1, 5, 100L, 60.0, 12),  // e1RM = 84
            set(2, 5, 100L, 80.0, 5),   // e1RM = 93.33 ← best
            set(3, 5, 100L, 70.0, 8)    // e1RM = 88.67
        )
        val result = detectPrsFromWorkout(w, sets, emptyMap(), mapOf(100L to "Bench"))
        assertEquals(1, result.size)
        assertEquals(80.0, result[0].weightKg!!, 0.001)
        assertEquals(5, result[0].reps!!)
    }

    @Test
    fun `PR detection - kilka ćwiczeń w jednym workoutcie - kilka eventów`() {
        val w = workout(id = 6L)
        val sets = listOf(
            set(1, 6, 100L, 80.0, 5),  // bench - PR
            set(2, 6, 200L, 100.0, 5)  // squat - PR
        )
        val result = detectPrsFromWorkout(
            w, sets, emptyMap(),
            mapOf(100L to "Bench", 200L to "Squat")
        )
        assertEquals(2, result.size)
        val benchPr = result.first { it.exerciseName == "Bench" }
        val squatPr = result.first { it.exerciseName == "Squat" }
        assertEquals(80.0, benchPr.weightKg!!, 0.001)
        assertEquals(100.0, squatPr.weightKg!!, 0.001)
    }

    // ============== detectInjuryFromWorkout ==============

    @Test
    fun `INJURY - pusty painArea = brak eventu`() {
        val w = workout(pain = null)
        assertTrue(detectInjuryFromWorkout(w).isEmpty())
    }

    @Test
    fun `INJURY - blank painArea = brak eventu`() {
        val w = workout(pain = "")
        assertTrue(detectInjuryFromWorkout(w).isEmpty())
    }

    @Test
    fun `INJURY - painArea wypełniony tworzy event`() {
        val w = workout(id = 10L, pain = "kolano")
        val result = detectInjuryFromWorkout(w)
        assertEquals(1, result.size)
        assertEquals(TrainingEventType.INJURY, result[0].type)
        assertEquals("kolano", result[0].area)
        assertEquals(10L, result[0].workoutId)
    }

    // ============== detectGapResumed ==============

    @Test
    fun `GAP - brak poprzednich treningów = brak eventu (pierwszy trening)`() {
        val w = workout(daysAgo = 0)
        assertTrue(detectGapResumed(w, emptyList()).isEmpty())
    }

    @Test
    fun `GAP - przerwa 5 dni = brak eventu`() {
        val newW = workout(id = 20L, daysAgo = 0)
        val prev = listOf(workout(id = 1L, daysAgo = 5))
        assertTrue(detectGapResumed(newW, prev).isEmpty())
    }

    @Test
    fun `GAP - przerwa 14 dni = brak eventu (granica)`() {
        val newW = workout(id = 20L, daysAgo = 0)
        val prev = listOf(workout(id = 1L, daysAgo = 14))
        assertTrue(detectGapResumed(newW, prev).isEmpty())
    }

    @Test
    fun `GAP - przerwa 15 dni = event GAP_RESUMED`() {
        val newW = workout(id = 20L, daysAgo = 0)
        val prev = listOf(workout(id = 1L, daysAgo = 15))
        val result = detectGapResumed(newW, prev)
        assertEquals(1, result.size)
        assertEquals(TrainingEventType.GAP_RESUMED, result[0].type)
        assertEquals(2, result[0].weeksContext!!)  // 15/7 = 2
    }

    @Test
    fun `GAP - bierze NAJNOWSZY poprzedni workout, nie najstarszy`() {
        val newW = workout(id = 20L, daysAgo = 0)
        val prev = listOf(
            workout(id = 1L, daysAgo = 100),  // bardzo dawno
            workout(id = 2L, daysAgo = 5)     // niedawno → brak gap
        )
        assertTrue(detectGapResumed(newW, prev).isEmpty())
    }

    // ============== detectDeloadFromWeeklyVolumes ==============

    @Test
    fun `DELOAD - za mało danych nie wykrywa`() {
        val volumes = listOf(10000.0, 11000.0)  // tylko 2 tygodnie
        assertTrue(detectDeloadFromWeeklyVolumes(volumes, now).isEmpty())
    }

    @Test
    fun `DELOAD - normalny tydzień = brak eventu`() {
        // [4 starsze 10000] [last completed 10500] [bieżący niezakonczony 3000]
        // last completed 10500 vs mediana 10000 = 105% → brak deload
        val volumes = listOf(10000.0, 10000.0, 10000.0, 10000.0, 10500.0, 3000.0)
        assertTrue(detectDeloadFromWeeklyVolumes(volumes, now).isEmpty())
    }

    @Test
    fun `DELOAD - last completed drop 50 procent = event DELOAD_DETECTED`() {
        // [4 tyg 10000] [last completed 5000] [bieżący 7000 - irrelevant]
        // 5000 / 10000 = 50% → deload
        val volumes = listOf(10000.0, 10000.0, 10000.0, 10000.0, 5000.0, 7000.0)
        val result = detectDeloadFromWeeklyVolumes(volumes, now)
        assertEquals(1, result.size)
        assertEquals(TrainingEventType.DELOAD_DETECTED, result[0].type)
        // event datowany na poniedzialek poprzedniego tygodnia
        assertEquals(now - 7 * msPerDay, result[0].date)
    }

    @Test
    fun `DELOAD - 60 procent = granica nie wykrywa`() {
        val volumes = listOf(10000.0, 10000.0, 10000.0, 10000.0, 6000.0, 11000.0)
        assertTrue(detectDeloadFromWeeklyVolumes(volumes, now).isEmpty())
    }

    @Test
    fun `DELOAD - 59 procent = event`() {
        val volumes = listOf(10000.0, 10000.0, 10000.0, 10000.0, 5900.0, 11000.0)
        val result = detectDeloadFromWeeklyVolumes(volumes, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `DELOAD - bieżący niezakończony tydzień nie generuje false positive (regresja v1_11_65)`() {
        // To byl dokladny case usera: 4 tyg ~33000, last completed 39120 (rosnacy!),
        // bieżący 9512 (zaczęty - tylko 1-2 sesje). Powinno NIE wykryc deloadu.
        val volumes = listOf(28811.0, 32711.0, 33387.0, 32845.0, 39120.0, 9512.0)
        val result = detectDeloadFromWeeklyVolumes(volumes, now)
        assertTrue("Bieżący tydzien nie powinien wywolywac deload", result.isEmpty())
    }
}
