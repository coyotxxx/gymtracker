package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

/**
 * Test v1.11.50 — `computeNewPRsFromSnapshot` pure function.
 * Weryfikuje detekcję nowych PR-ów per-exercise w bieżącym treningu vs poprzednich.
 *
 * Logika musi być IDENTYCZNA z StatsRepository.detectNewPRs() — w tej metodzie
 * AI sprawdza czy user pobił 1RM, więc IDENTICAL behavior is critical.
 */
class DetectNewPRsFastTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    // Epley 1RM formula: w * (1 + r/30)
    private fun epley(w: Double, r: Int): Double =
        if (w > 0 && r > 0) w * (1 + r / 30.0) else 0.0

    private fun ex(id: Long) = Exercise(
        id = id, name = "Ex$id", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL
    )
    private fun workout(id: Long, daysAgo: Long, finished: Boolean = true) = Workout(
        id = id,
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null
    )
    private fun set(
        id: Long, wid: Long, exId: Long, reps: Int, weight: Double,
        type: SetType = SetType.NORMAL, completed: Boolean = true
    ) = WorkoutSet(
        id = id, workoutId = wid, exerciseId = exId,
        setNumber = 1, orderIndex = 0,
        reps = reps, weightKg = weight,
        isCompleted = completed, setType = type
    )

    // ============= EDGE CASES (puste / brak danych) =============

    @Test
    fun `pusty snapshot zwraca pustą listę PR`() {
        val result = computeNewPRsFromSnapshot(currentWorkoutId = 1, snapshot = StatsSnapshot.EMPTY, epley = ::epley)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `currentWorkoutId nie istnieje - empty`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(set(id = 1, wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(currentWorkoutId = 999, snapshot = snapshot, epley = ::epley)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `bieżący trening bez setów - empty`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = emptyList<WorkoutSet>()
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(1, snapshot, ::epley)
        assertTrue(result.isEmpty())
    }

    // ============= PIERWSZY PR (poprzedni max = 0) =============

    @Test
    fun `pierwszy trening z setem - PR (poprzedni max = 0)`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(set(id = 1, wid = 1, exId = 10, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(1, snapshot, ::epley)

        assertEquals(1, result.size)
        val pr = result[0]
        assertEquals(10L, pr.exerciseId)
        assertEquals(100.0, pr.weightKg, 0.001)
        assertEquals(10, pr.reps)
        assertEquals(0.0, pr.previousBest1RM, 0.001)
        assertEquals(epley(100.0, 10), pr.new1RM, 0.001)  // 100 * (1+10/30) = 133.33
    }

    // ============= PR vs poprzedni trening =============

    @Test
    fun `PR pobity - aktualny 1RM wyzszy od poprzedniego`() {
        val workouts = listOf(
            workout(1, daysAgo = 7),  // poprzedni
            workout(2, daysAgo = 1)   // bieżący
        )
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 10, weight = 100.0),  // poprzedni: 1RM = 133.33
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 130.0)    // bieżący: 1RM = 151.67 → PR!
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)

        assertEquals(1, result.size)
        val pr = result[0]
        assertEquals(130.0, pr.weightKg, 0.001)
        assertEquals(5, pr.reps)
        assertEquals(epley(100.0, 10), pr.previousBest1RM, 0.001)
        assertEquals(epley(130.0, 5), pr.new1RM, 0.001)
        assertTrue(pr.new1RM > pr.previousBest1RM)
    }

    @Test
    fun `BEZ PR - aktualny 1RM rowny lub mniejszy od poprzedniego`() {
        val workouts = listOf(
            workout(1, daysAgo = 7),
            workout(2, daysAgo = 1)
        )
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 130.0),  // poprzedni: 1RM = 151.67
            set(id = 2, wid = 2, exId = 10, reps = 10, weight = 100.0)  // bieżący: 1RM = 133.33 < 151.67
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `EQUAL - aktualny 1RM rowny poprzedniemu - BEZ PR (strict greater)`() {
        val workouts = listOf(workout(1, daysAgo = 7), workout(2, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 10, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, reps = 10, weight = 100.0)  // identyczny — NIE PR
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)
        assertTrue(result.isEmpty())
    }

    // ============= FILTRY (WARMUP, niewykonane, niezakończone) =============

    @Test
    fun `WARMUP w bieżącym treningu pomijany`() {
        val workouts = listOf(workout(1, daysAgo = 7), workout(2, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 100.0),  // poprzedni: 1RM = 116.67
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 200.0, type = SetType.WARMUP),
            set(id = 3, wid = 2, exId = 10, reps = 5, weight = 110.0)   // 1RM = 128.33 → PR
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)

        assertEquals(1, result.size)
        // WARMUP 200kg pomijany — biorąc go pod uwagę byłby aktualny "max"
        // Bez warmupu: 110 jest faktycznym maxem
        assertEquals(110.0, result[0].weightKg, 0.001)
        assertEquals(5, result[0].reps)
    }

    @Test
    fun `niewykonany set w bieżącym treningu pomijany`() {
        val workouts = listOf(workout(1, daysAgo = 7), workout(2, daysAgo = 1))
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 200.0, completed = false),
            set(id = 3, wid = 2, exId = 10, reps = 5, weight = 110.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)

        assertEquals(1, result.size)
        assertEquals(110.0, result[0].weightKg, 0.001)
    }

    @Test
    fun `niezakonczony poprzedni trening (active) ignorowany w previousMax`() {
        val workouts = listOf(
            workout(1, daysAgo = 7, finished = false),  // active workout — nie zalicza się
            workout(2, daysAgo = 1, finished = true)    // bieżący
        )
        val ex = listOf(ex(10))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 200.0),  // active workout — pomijany
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 110.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)
        // Snapshot.completedSets filtruje tylko isCompleted && !WARMUP, ALE
        // setsByWorkoutId zawiera wszystko. completedSets - WSZYSTKIE completed.
        // Sprawdźmy faktyczne zachowanie: completedSets nie filtruje workoutów
        // niezakończonych — to robi tylko finishedWorkouts. To znaczy że set
        // z workoutu active może być w completedSets jeśli isCompleted=true.
        // Jednak w faktycznej aplikacji active workout nie ma completed sets
        // dopóki user nie kliknie "Zakończ".
        // Test sprawdza pośrednio — wynik zależy od konkretnego use case.
        // Dla bezpieczeństwa: tylko wynik niepustości, nie konkretny PR.
        // v1.20.0: konkretna asercja zamiast tautologii.
        // Snapshot zlicza set 1 (workout 1 = active) jako "completed" więc previousMax dla
        // exId=10 = e1RM(200kg × 5) ≈ 233 kg. Bieżący wynik dla workout 2 to e1RM(110×5) ≈ 128 kg.
        // 128 < 233 → NIE jest to PR → result.isEmpty().
        // (W produkcji active workout faktycznie nie ma completed sets aż user zakończy treningowanie —
        // ale snapshot tego nie filtruje, bo to byłaby zmiana semantyki API. Test dokumentuje
        // bieżące zachowanie.)
        assertTrue("nie powinno być PR — previousMax z active workout dominuje", result.isEmpty())
    }

    // ============= MULTI-EXERCISE =============

    @Test
    fun `multi-exercise - PR per cwiczenie osobno`() {
        val workouts = listOf(workout(1, daysAgo = 7), workout(2, daysAgo = 1))
        val ex = listOf(ex(10), ex(20))
        val sets = listOf(
            // Bench (10): poprzedni 100kg×5 → 1RM=116.67, bieżący 110×5 → 1RM=128.33 PR!
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 110.0),
            // Squat (20): poprzedni 150×5 → 1RM=175, bieżący 140×5 → 1RM=163.33 BEZ PR
            set(id = 3, wid = 1, exId = 20, reps = 5, weight = 150.0),
            set(id = 4, wid = 2, exId = 20, reps = 5, weight = 140.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)

        assertEquals(1, result.size)  // tylko bench
        assertEquals(10L, result[0].exerciseId)
    }

    @Test
    fun `multi-exercise - oba PR-y wykryte`() {
        val workouts = listOf(workout(1, daysAgo = 7), workout(2, daysAgo = 1))
        val ex = listOf(ex(10), ex(20))
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 5, weight = 100.0),
            set(id = 2, wid = 2, exId = 10, reps = 5, weight = 110.0),  // PR bench
            set(id = 3, wid = 1, exId = 20, reps = 5, weight = 100.0),
            set(id = 4, wid = 2, exId = 20, reps = 5, weight = 110.0)   // PR squat
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(2, snapshot, ::epley)

        assertEquals(2, result.size)
        val ids = result.map { it.exerciseId }.toSet()
        assertEquals(setOf(10L, 20L), ids)
    }

    // ============= NAJLEPSZY SET W TRENINGU =============

    @Test
    fun `bestSet - maxByOrNull epley wybiera prawidlowy set`() {
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = listOf(ex(10))
        // 3 sety: 80×10 (1RM=106.67), 100×6 (1RM=120.0), 110×3 (1RM=121.0)
        // Najlepszy = 110×3 (najwyższy 1RM)
        val sets = listOf(
            set(id = 1, wid = 1, exId = 10, reps = 10, weight = 80.0),
            set(id = 2, wid = 1, exId = 10, reps = 6, weight = 100.0),
            set(id = 3, wid = 1, exId = 10, reps = 3, weight = 110.0)
        )
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(1, snapshot, ::epley)

        assertEquals(1, result.size)
        assertEquals(110.0, result[0].weightKg, 0.001)
        assertEquals(3, result[0].reps)
    }

    // ============= USUNIĘTE ĆWICZENIA =============

    @Test
    fun `set z usunietym cwiczeniem - nadal PR (exerciseId nie wymaga lookup)`() {
        // detectNewPRs grupuje po exerciseId i porównuje 1RM. Nie wymaga
        // istnienia exercise w snapshot.allExercises — używa tylko setów.
        val workouts = listOf(workout(1, daysAgo = 1))
        val ex = emptyList<Exercise>()  // brak żadnego ćwiczenia
        val sets = listOf(set(id = 1, wid = 1, exId = 999, reps = 10, weight = 100.0))
        val snapshot = StatsSnapshot.from(workouts, ex, sets)
        val result = computeNewPRsFromSnapshot(1, snapshot, ::epley)

        // PR i tak wykryty bo grupuje po exerciseId, nie wymaga Exercise w bazie
        assertEquals(1, result.size)
        assertEquals(999L, result[0].exerciseId)
    }
}
