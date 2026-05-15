package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27 — FAZA 0.3 — weryfikacja że wszystkie scenariusze bazowe ładują się
 * poprawnie przez harness (placeholdery czasu + import + backfill).
 *
 * Jeśli przejdzie — generator produkuje poprawny JSON, scenariusze gotowe
 * jako wejście dla snapshotów ekranów (FAZA 1+).
 */
class ScenarioLoadTest : TestHarness() {

    private fun assertScenario(name: String, expectedWorkouts: Int) = runBlocking {
        loadScenario(name)
        val workouts = db.workoutDao().observeAllOnce()
        assertEquals("scenariusz '$name' — liczba workoutów", expectedWorkouts, workouts.size)
        // placeholdery rozwinięte — daty muszą być w przeszłości względem teraz
        val now = System.currentTimeMillis()
        assertTrue("scenariusz '$name' — daty workoutów w przeszłości",
            workouts.all { it.startedAt in 1..now })
        // finishedAt po startedAt
        assertTrue("scenariusz '$name' — finishedAt po startedAt",
            workouts.all { w -> w.finishedAt?.let { it >= w.startedAt } ?: true })
    }

    @Test fun `smoke laduje sie`() = assertScenario("smoke", 2)
    @Test fun `fresh laduje sie`() = assertScenario("fresh", 3)
    @Test fun `healthy laduje sie`() = assertScenario("healthy", 24)
    @Test fun `overtraining_cut laduje sie`() = assertScenario("overtraining_cut", 30)
    @Test fun `return_after_break laduje sie`() = assertScenario("return_after_break", 22)
    @Test fun `injury laduje sie`() = assertScenario("injury", 15)

    @Test
    fun `overtraining_cut ma pomiary wagi`() = runBlocking {
        loadScenario("overtraining_cut")
        val measurements = db.bodyMeasurementDao().getAllAsc()
        assertEquals("overtraining_cut — 12 pomiarów wagi", 12, measurements.size)
    }

    @Test
    fun `injury ma trening z bolem`() = runBlocking {
        loadScenario("injury")
        val withPain = db.workoutDao().observeAllOnce().filter { it.painArea != null }
        assertTrue("injury — co najmniej 1 trening z painArea", withPain.isNotEmpty())
    }
}
