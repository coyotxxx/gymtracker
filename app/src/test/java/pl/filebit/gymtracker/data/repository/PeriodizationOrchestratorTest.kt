package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.PlanStagnationReport
import pl.filebit.gymtracker.ai.ExerciseTrend
import pl.filebit.gymtracker.ai.ProgressionStatus
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle

/**
 * Test pure function `recommendNextPhase()` z PeriodizationOrchestrator.kt.
 *
 * Testuje logikę przejścia faz mesocyklu bez DAO/Room. Full integration test
 * (z mock DAO) zostanie dodany w v1.15.0+ razem z PendingPeriodizationDecision.
 */
class PeriodizationOrchestratorTest {

    private val now = 1_700_000_000_000L
    private val week = 7L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    // ===== Helpery =====

    private fun mesocycle(
        phase: MesocyclePhase,
        startDaysAgo: Int = 21,  // 3 tyg
        plannedEndDaysFromNow: Int = 0
    ): TrainingMesocycle = TrainingMesocycle(
        id = 1L,
        startDateMs = now - startDaysAgo * day,
        plannedEndDateMs = now + plannedEndDaysFromNow * day,
        phase = phase,
        weekInPhase = (startDaysAgo / 7).coerceAtLeast(1),
        phaseLengthWeeks = TrainingMesocycle.defaultLengthForPhase(phase),
        status = MesocycleStatus.ACTIVE
    )

    private fun stagnationReport(
        stagnatingCount: Int,
        totalAnalyzed: Int,
        deloadRecommended: Boolean = stagnatingCount.toDouble() / totalAnalyzed * 100 >= 30.0
    ): PlanStagnationReport = PlanStagnationReport(
        trends = emptyList(),
        totalAnalyzed = totalAnalyzed,
        stagnatingCount = stagnatingCount,
        regressingCount = 0,
        deloadRecommended = deloadRecommended,
        deloadReason = if (deloadRecommended) "test" else ""
    )

    // ===== Test cases =====

    @Test
    fun `ACCUMULATION bez stagnacji - przejscie do INTENSIFICATION`() {
        val current = mesocycle(MesocyclePhase.ACCUMULATION)
        val proposal = recommendNextPhase(
            currentMeso = current,
            stagnationReport = null,
            nowMs = now
        )
        assertEquals(MesocyclePhase.ACCUMULATION, proposal.fromPhase)
        assertEquals(MesocyclePhase.INTENSIFICATION, proposal.recommendedNext)
        assertTrue(proposal.reasoning.contains("intensyfikacji", ignoreCase = true))
    }

    @Test
    fun `ACCUMULATION ze stagnacja 30% - skok do DELOAD`() {
        val current = mesocycle(MesocyclePhase.ACCUMULATION)
        val stagnation = stagnationReport(stagnatingCount = 3, totalAnalyzed = 10) // 30%
        val proposal = recommendNextPhase(current, stagnation, now)
        assertEquals(MesocyclePhase.DELOAD, proposal.recommendedNext)
        assertTrue(proposal.reasoning.contains("DELOAD"))
    }

    @Test
    fun `ACCUMULATION ze stagnacja 50% - skok do DELOAD z wysoka confidence`() {
        val current = mesocycle(MesocyclePhase.ACCUMULATION)
        val stagnation = stagnationReport(stagnatingCount = 5, totalAnalyzed = 10) // 50%
        val proposal = recommendNextPhase(current, stagnation, now)
        assertEquals(MesocyclePhase.DELOAD, proposal.recommendedNext)
        assertTrue("Wysoka confidence dla forced deload", proposal.confidence >= 0.85)
    }

    @Test
    fun `ACCUMULATION ze stagnacja 20% - normalne przejscie do INTENSIFICATION`() {
        val current = mesocycle(MesocyclePhase.ACCUMULATION)
        val stagnation = stagnationReport(stagnatingCount = 2, totalAnalyzed = 10) // 20%
        val proposal = recommendNextPhase(current, stagnation, now)
        // Próg 30% nie osiągnięty
        assertEquals(MesocyclePhase.INTENSIFICATION, proposal.recommendedNext)
    }

    @Test
    fun `INTENSIFICATION - zawsze do DELOAD`() {
        val current = mesocycle(MesocyclePhase.INTENSIFICATION)
        val proposal = recommendNextPhase(current, null, now)
        assertEquals(MesocyclePhase.DELOAD, proposal.recommendedNext)
    }

    @Test
    fun `INTENSIFICATION ze stagnacja - DELOAD z innym reasoning`() {
        val current = mesocycle(MesocyclePhase.INTENSIFICATION)
        val stagnation = stagnationReport(stagnatingCount = 4, totalAnalyzed = 10)
        val proposal = recommendNextPhase(current, stagnation, now)
        assertEquals(MesocyclePhase.DELOAD, proposal.recommendedNext)
        assertTrue(proposal.reasoning.contains("stagnacja", ignoreCase = true))
    }

    @Test
    fun `DELOAD - powrot do ACCUMULATION`() {
        val current = mesocycle(MesocyclePhase.DELOAD)
        val proposal = recommendNextPhase(current, null, now)
        assertEquals(MesocyclePhase.ACCUMULATION, proposal.recommendedNext)
        assertTrue(proposal.reasoning.contains("akumulacji", ignoreCase = true))
    }

    @Test
    fun `PEAKING - obligatoryjny DELOAD po test`() {
        val current = mesocycle(MesocyclePhase.PEAKING)
        val proposal = recommendNextPhase(current, null, now)
        assertEquals(MesocyclePhase.DELOAD, proposal.recommendedNext)
        assertTrue(proposal.reasoning.contains("post-peak", ignoreCase = true))
    }

    @Test
    fun `RECOVERY - powrot do ACCUMULATION`() {
        val current = mesocycle(MesocyclePhase.RECOVERY)
        val proposal = recommendNextPhase(current, null, now)
        assertEquals(MesocyclePhase.ACCUMULATION, proposal.recommendedNext)
    }

    @Test
    fun `proposal duration matches default for new phase`() {
        // Każde przejście powinno mieć duration = default dla docelowej fazy
        val accToInt = recommendNextPhase(mesocycle(MesocyclePhase.ACCUMULATION), null, now)
        assertEquals(
            TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.INTENSIFICATION),
            accToInt.plannedDurationWeeks
        )
        val intToDel = recommendNextPhase(mesocycle(MesocyclePhase.INTENSIFICATION), null, now)
        assertEquals(
            TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.DELOAD),
            intToDel.plannedDurationWeeks
        )
        val delToAcc = recommendNextPhase(mesocycle(MesocyclePhase.DELOAD), null, now)
        assertEquals(
            TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION),
            delToAcc.plannedDurationWeeks
        )
    }

    @Test
    fun `plannedStartDate is now`() {
        val proposal = recommendNextPhase(mesocycle(MesocyclePhase.ACCUMULATION), null, now)
        assertEquals(now, proposal.plannedStartDate)
    }

    @Test
    fun `confidence range valid (0_0 to 1_0)`() {
        val cases = listOf(
            mesocycle(MesocyclePhase.ACCUMULATION),
            mesocycle(MesocyclePhase.INTENSIFICATION),
            mesocycle(MesocyclePhase.DELOAD),
            mesocycle(MesocyclePhase.PEAKING),
            mesocycle(MesocyclePhase.RECOVERY)
        )
        cases.forEach { meso ->
            val proposal = recommendNextPhase(meso, null, now)
            assertTrue("confidence ${proposal.confidence} dla ${meso.phase}",
                proposal.confidence in 0.0..1.0)
        }
    }
}
