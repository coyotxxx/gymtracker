package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27 — FAZA 0.1 — sanity check fundamentu.
 *
 * Potwierdza że:
 *  1. Robolectric uruchamia in-memory Room w JVM unit teście (CI bez emulatora)
 *  2. BackupImporter ładuje scenariusz JSON — ta sama ścieżka co Debug Import
 *
 * Jeśli przejdzie w CI — fundament harness działa, można budować Trace Reporter.
 */
class HarnessSanityTest : TestHarness() {

    @Test
    fun `in-memory Room buduje sie i DAO odpowiada`() {
        assertNotNull("AppDatabase nie zbudowane", db)
        val count = runBlocking { db.exerciseDao().count() }
        assertEquals("Swieza in-memory baza powinna byc pusta", 0, count)
    }

    @Test
    fun `loadScenario smoke laduje dane przez BackupImporter`() = runBlocking {
        loadScenario("smoke")

        // Seed (193 ćwiczeń) + import — baza ma ćwiczenia
        val exCount = db.exerciseDao().count()
        assertTrue("Po imporcie baza powinna mieć ćwiczenia (seed+import), było $exCount",
            exCount > 0)

        // 2 workouty ze scenariusza
        val workouts = db.workoutDao().observeAllOnce()
        assertEquals("Scenariusz smoke ma 2 workouty", 2, workouts.size)

        // 3 sety ze scenariusza
        val sets = db.workoutSetDao().getAll()
        assertEquals("Scenariusz smoke ma 3 sety", 3, sets.size)

        // Profil zaimportowany
        val profile = db.userProfileDao().get()
        assertNotNull("Profil powinien być zaimportowany", profile)
        assertEquals(4, profile!!.daysPerWeek)
    }
}
