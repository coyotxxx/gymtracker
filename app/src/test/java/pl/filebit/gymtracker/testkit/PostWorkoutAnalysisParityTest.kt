package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.7.0 — gwarancja, że jednoprzebiegowa analyzePostWorkout() daje IDENTYCZNY
 * wynik co trzy osobne metody (detectNewPRs + progressionTipsForWorkout +
 * detectStagnation), które zastępuje w finishWorkout. Optymalizacja wydajności
 * NIE MOŻE zmienić logiki — ten test to pilnuje na realnych scenariuszach.
 *
 * Porównanie jako zbiory (toSet) — kolejność w dialogu nieistotna, liczą się
 * te same elementy.
 */
class PostWorkoutAnalysisParityTest : TestHarness() {

    private fun checkParity(scenario: String) = runBlocking {
        loadScenario(scenario)
        val kit = ViewModelKit(db, context)
        val repo = kit.statsRepo
        val finished = db.workoutDao().observeAllOnce().filter { it.finishedAt != null }
        // scenariusz musi mieć jakąkolwiek historię, inaczej test jest pusty
        assertEquals(
            "scenariusz '$scenario' powinien mieć zakończone treningi",
            true, finished.isNotEmpty()
        )
        for (w in finished) {
            val combined = repo.analyzePostWorkout(w.id)
            val prs = repo.detectNewPRs(w.id)
            val tips = repo.progressionTipsForWorkout(w.id)
            val stag = repo.detectStagnation(w.id)
            assertEquals("PR parity [$scenario w=${w.id}]", prs.toSet(), combined.prs.toSet())
            assertEquals("tips parity [$scenario w=${w.id}]", tips.toSet(), combined.tips.toSet())
            assertEquals("stagnation parity [$scenario w=${w.id}]", stag.toSet(), combined.stagnations.toSet())
        }
    }

    @Test fun `parity - smoke`() = checkParity("smoke")
    @Test fun `parity - overtraining_cut`() = checkParity("overtraining_cut")
    @Test fun `parity - return_after_break`() = checkParity("return_after_break")
    @Test fun `parity - injury`() = checkParity("injury")
    @Test fun `parity - healthy`() = checkParity("healthy")
}
