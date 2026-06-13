package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.coach.CoachAction
import pl.filebit.gymtracker.data.coach.CoachActionType
import pl.filebit.gymtracker.data.coach.CoachDomain
import pl.filebit.gymtracker.data.coach.CoachPriority
import pl.filebit.gymtracker.data.coach.CoachReaction
import pl.filebit.gymtracker.data.coach.arbitrateCoach

/**
 * v2.33.0 (U3) — arbiter CoachOrchestratora. Czysta funkcja: priorytet + anti-konflikt + dedup.
 * Patrz docs/COACH-DESIGN-SPEC.md §4-5.
 */
class CoachOrchestratorTest {

    private fun r(
        id: String, domain: CoachDomain, prio: CoachPriority,
        action: CoachActionType = CoachActionType.NONE
    ) = CoachReaction(id, domain, prio, id, id,
        actions = listOf(CoachAction(action, "x")), source = "test")

    @Test
    fun `brak kandydatow daje pusty werdykt`() {
        val v = arbitrateCoach(emptyList())
        assertTrue(v.isEmpty); assertNull(v.primary)
    }

    @Test
    fun `priorytet - kontuzja wygrywa z korekta kcal`() {
        val injury = r("injury", CoachDomain.HEALTH, CoachPriority.HEALTH, CoachActionType.REST_INJURY)
        val kcal = r("kcal", CoachDomain.DIET, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_KCAL_ADJUST)
        val v = arbitrateCoach(listOf(kcal, injury))
        assertEquals("injury", v.primary?.id)
        assertEquals(1, v.secondary.size)
        assertEquals("kcal", v.secondary.first().id)
    }

    @Test
    fun `anti-konflikt - kontuzja ucisza deload-sugestie treningu`() {
        val injury = r("injury", CoachDomain.HEALTH, CoachPriority.HEALTH, CoachActionType.REST_INJURY)
        val deload = r("deload", CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_DELOAD)
        val v = arbitrateCoach(listOf(injury, deload))
        // deload treningu zniknął całkowicie (ból ≠ przetrenowanie)
        assertEquals("injury", v.primary?.id)
        assertTrue("deload nie powinien być w werdykcie", v.all.none { it.id == "deload" })
    }

    @Test
    fun `dedup deloadu - zostaje najwazniejszy`() {
        val trainingDeload = r("t_deload", CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_DELOAD)
        val dietDeload = r("d_deload", CoachDomain.RECOVERY, CoachPriority.RECOVERY, CoachActionType.APPLY_DELOAD)
        val v = arbitrateCoach(listOf(trainingDeload, dietDeload))
        // RECOVERY (rank 2) < OPTIMIZATION (rank 5) → zostaje dietDeload, training znika
        assertEquals("d_deload", v.primary?.id)
        assertTrue("zduplikowany deload treningu usunięty", v.all.none { it.id == "t_deload" })
    }

    @Test
    fun `sama dieta - korekta kcal jako dominujaca`() {
        val kcal = r("kcal", CoachDomain.DIET, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_KCAL_ADJUST)
        val v = arbitrateCoach(listOf(kcal))
        assertEquals("kcal", v.primary?.id)
        assertTrue(v.secondary.isEmpty())
    }

    @Test
    fun `pelna drabina priorytetow sortuje poprawnie`() {
        val opt = r("opt", CoachDomain.DIET, CoachPriority.OPTIMIZATION)
        val cons = r("cons", CoachDomain.CONSISTENCY, CoachPriority.CONSISTENCY)
        val rec = r("rec", CoachDomain.RECOVERY, CoachPriority.RECOVERY)
        val v = arbitrateCoach(listOf(opt, cons, rec))
        assertEquals("rec", v.primary?.id)  // RECOVERY rank 2 najważniejszy z tych trzech
        assertEquals(listOf("cons", "opt"), v.secondary.map { it.id })
    }
}
