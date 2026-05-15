package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27 — FAZA 5.1 — flow treningu (interakcja).
 *
 * Pełna ścieżka: rozpocznij trening → zaloguj serie → zakończ → trening
 * w historii. Test wykonuje te same operacje co user klikając w UI
 * (przez WorkoutRepository) i weryfikuje stan po każdym kroku.
 */
class TrainingFlowTest : TestHarness() {

    @Test
    fun `flow - rozpocznij trening, zaloguj serie, zakoncz`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.workoutRepo
        val exercises = db.exerciseDao().getAll().take(2)

        val tr = TraceReport("flow-trening")

        // KROK 1 — rozpocznij trening
        val workout = repo.startOrResume()
        tr.section("KROK 1 — rozpocznij")
            .verdict("aktywny trening", "id=${workout.id}", "isActive=${workout.isActive}")
        assertTrue("nowy trening jest aktywny", workout.isActive)
        assertTrue("observeActive widzi nowy trening",
            repo.observeActive().first()?.id == workout.id)

        // KROK 2 — zaloguj serie (2 ćwiczenia × 3 serie)
        repeat(3) { repo.addSet(workout.id, exercises[0].id, reps = 8, weightKg = 80.0) }
        repeat(3) { repo.addSet(workout.id, exercises[1].id, reps = 10, weightKg = 40.0) }
        val setsLogged = repo.getSetsForWorkout(workout.id)
        tr.section("KROK 2 — loguj serie")
            .kv("zalogowane serie", setsLogged.size.toString())
            .kv("wykonane (isCompleted)", setsLogged.count { it.isCompleted }.toString())
        assertEquals("6 serii zalogowanych", 6, setsLogged.size)
        assertTrue("zalogowane serie są wykonane", setsLogged.all { it.isCompleted })

        // KROK 3 — zakończ trening
        repo.finish(workout.id)
        tr.section("KROK 3 — zakończ")
        val active = repo.observeActive().first()
        val finished = repo.getWorkout(workout.id)!!
        tr.verdict("brak aktywnego treningu", (active == null).toString(), "")
            .verdict("trening zakończony", "isActive=${finished.isActive}", "")

        assertTrue("po finish brak aktywnego treningu", active == null)
        assertFalse("zakończony trening nie jest aktywny", finished.isActive)

        // KROK 4 — trening w historii
        val inHistory = repo.observeAll().first().any { it.id == workout.id && !it.isActive }
        tr.section("KROK 4 — historia")
            .verdict("trening na liście historii", inHistory.toString(), "")
            .emit()
        assertTrue("zakończony trening trafia do historii", inHistory)
    }

    @Test
    fun `flow - planowane serie z planu potwierdzane przez usera`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.workoutRepo
        val exercise = db.exerciseDao().getAll().first()

        val workout = repo.startOrResume()
        // serie z planu — placeholdery (isCompleted=false)
        val planned = (1..3).map {
            repo.addPlannedSet(workout.id, exercise.id, reps = 5, weightKg = 100.0)
        }
        assertTrue("planowane serie nie są jeszcze wykonane",
            planned.none { it.isCompleted })

        // user wykonuje serie — potwierdza
        planned.forEach { repo.confirmSet(it.id, actualReps = 5) }
        val afterConfirm = repo.getSetsForWorkout(workout.id)

        TraceReport("flow-planned-sets")
            .section("POTWIERDZANIE SERII Z PLANU")
            .kv("serie z planu", planned.size.toString())
            .kv("po potwierdzeniu wykonane",
                afterConfirm.count { it.isCompleted }.toString())
            .emit()

        assertEquals("wszystkie 3 serie potwierdzone jako wykonane",
            3, afterConfirm.count { it.isCompleted })
    }
}
