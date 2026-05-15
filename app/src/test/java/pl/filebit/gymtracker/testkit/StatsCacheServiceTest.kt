package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.repository.StatsCacheService

/**
 * v1.27 — FAZA 2.6 — testy StatsCacheService.
 *
 * snapshot() robi bulk-fetch (3 zapytania SQL) zamiast N+1 queries —
 * fundament wydajności. Test sprawdza SPÓJNOŚĆ snapshotu: mapy muszą
 * pokrywać dokładnie te same dane co listy (zły snapshot = złe statystyki).
 */
class StatsCacheServiceTest : TestHarness() {

    private fun service() =
        StatsCacheService(db.workoutDao(), db.exerciseDao(), db.workoutSetDao())

    @Test
    fun `snapshot jest spojny — mapy pokrywaja listy`() = runBlocking {
        loadScenario("smoke")
        val snap = service().snapshot()

        val tr = TraceReport("stats-cache-snapshot")
            .section("SNAPSHOT")
            .kv("allWorkouts", snap.allWorkouts.size.toString())
            .kv("finishedWorkouts", snap.finishedWorkouts.size.toString())
            .kv("allExercises", snap.allExercises.size.toString())
            .kv("allSets", snap.allSets.size.toString())
            .section("OCENA spójności")
        val workoutsByIdOk = snap.workoutsById.size == snap.allWorkouts.size
        val exercisesByIdOk = snap.exercisesById.size == snap.allExercises.size
        val setsCoverageOk = snap.setsByWorkoutId.values.sumOf { it.size } == snap.allSets.size
        if (workoutsByIdOk && exercisesByIdOk && setsCoverageOk) {
            tr.line("snapshot spójny — mapy pokrywają listy 1:1")
        } else {
            tr.note("[KONFLIKT] snapshot niespójny: workoutsById=$workoutsByIdOk " +
                "exercisesById=$exercisesByIdOk setsCoverage=$setsCoverageOk")
        }
        tr.emit()

        assertEquals("workoutsById pokrywa allWorkouts",
            snap.allWorkouts.size, snap.workoutsById.size)
        assertEquals("exercisesById pokrywa allExercises",
            snap.allExercises.size, snap.exercisesById.size)
        assertEquals("setsByWorkoutId pokrywa wszystkie sety",
            snap.allSets.size, snap.setsByWorkoutId.values.sumOf { it.size })
        assertTrue("finishedWorkouts ⊆ allWorkouts",
            snap.finishedWorkouts.size <= snap.allWorkouts.size)
    }

    @Test
    fun `pusta baza — snapshot pusty ale spojny`() = runBlocking {
        val snap = service().snapshot()
        assertEquals(0, snap.allWorkouts.size)
        assertEquals(0, snap.allSets.size)
        assertTrue("seed ćwiczeń mógł się załadować lub nie — mapa spójna z listą",
            snap.exercisesById.size == snap.allExercises.size)
    }
}
