package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * v1.27 — FAZA 0.1 — sanity check fundamentu.
 *
 * Potwierdza że Robolectric uruchamia in-memory Room w JVM unit teście (CI).
 * Jeśli ten test przejdzie w CI — fundament działa, można budować dalej.
 */
class HarnessSanityTest : TestHarness() {

    @Test
    fun `in-memory Room buduje sie i DAO odpowiada`() {
        assertNotNull("AppDatabase nie zbudowane", db)
        val count = runBlocking { db.exerciseDao().count() }
        assertEquals("Swieza in-memory baza powinna byc pusta", 0, count)
    }

    @Test
    fun `wiele DAO dostepne`() = runBlocking {
        assertEquals(0, db.workoutDao().let { db.exerciseDao().count() })
        assertNotNull(db.workoutDao())
        assertNotNull(db.userProfileDao())
        assertNotNull(db.trainingMesocycleDao())
    }
}
