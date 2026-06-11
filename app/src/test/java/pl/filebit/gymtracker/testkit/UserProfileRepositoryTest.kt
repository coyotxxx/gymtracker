package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v2.16.0 (P1-5) — zmiana kierunku celu zamyka nieaktualną fazę diety.
 * Bez tego getCurrent() zwracał starą fazę (np. wciąż "CUT" po przejściu na masę).
 */
class UserProfileRepositoryTest : TestHarness() {

    private fun repo() = UserProfileRepository(db.userProfileDao(), db.dietPhaseDao())

    private suspend fun insertActiveCut(): Long = db.dietPhaseDao().insert(
        DietPhase(
            type = DietPhaseType.CUT,
            startDateMs = System.currentTimeMillis() - 24L * 3600 * 1000,
            endDateMs = null
        )
    )

    @Test
    fun `zmiana celu CUT na BULK zamyka aktywna faze CUT`() = runBlocking {
        val id = insertActiveCut()
        repo().save(UserProfile(goalType = DietGoalType.MUSCLE_GAIN))  // → BULK
        val phase = db.dietPhaseDao().getRecent(10).first { it.id == id }
        assertNotNull("faza CUT powinna zostać zamknięta (endDateMs ustawione)", phase.endDateMs)
    }

    @Test
    fun `cel pozostajacy w kierunku CUT nie zamyka fazy`() = runBlocking {
        val id = insertActiveCut()
        repo().save(UserProfile(goalType = DietGoalType.FAT_LOSS))  // → CUT (bez zmiany)
        val phase = db.dietPhaseDao().getRecent(10).first { it.id == id }
        assertNull("faza CUT nadal aktywna", phase.endDateMs)
    }
}
