package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.repository.PeriodizationOrchestrator
import pl.filebit.gymtracker.data.repository.PeriodizationState

/**
 * v2.63.0 („wykonaj, chcę wszystko sprawdzić") — E2E periodyzacji w CZASIE.
 * PeriodizationOrchestrator.pulse(nowMs) przyjmuje wstrzykiwalny czas, więc symulujemy
 * upływ tygodni mezocyklu i sprawdzamy NIEZMIENNIKI przejść faz:
 *  - weekInPhase rośnie monotonicznie i NIGDY > phaseLengthWeeks (żadnego „tydzień 15/4"),
 *  - po końcu cyklu → TransitionDue z sensowną propozycją kolejnej fazy (≠ obecna logicznie, czas>0),
 *  - daleko w przyszłości też TransitionDue (nie rozjeżdża się), brak wyjątku/crasha,
 *  - applyTransition startuje nową fazę od tygodnia 1.
 */
class PeriodizationE2ETest : TestHarness() {

    private val day = 24L * 3600 * 1000
    private val t0 = 1_700_000_000_000L

    private fun orchestrator(): PeriodizationOrchestrator {
        val kit = ViewModelKit(db, context)
        return PeriodizationOrchestrator(db.trainingMesocycleDao(), kit.phaseAnalyzer, kit.stagnationAnalyzer)
    }

    @Test
    fun `mezocykl przechodzi przez tygodnie i konczy sie transition`() = runBlocking {
        val orch = orchestrator()
        // 4-tygodniowa akumulacja startująca w t0
        db.trainingMesocycleDao().upsert(
            TrainingMesocycle(
                startDateMs = t0, plannedEndDateMs = t0 + 28 * day,
                phase = MesocyclePhase.ACCUMULATION, weekInPhase = 1, phaseLengthWeeks = 4,
                status = MesocycleStatus.ACTIVE, triggerReason = "test"
            )
        )

        val weeksSeen = mutableListOf<Int>()
        // pulse co tydzień przez 4 tygodnie
        for (d in listOf(0, 7, 14, 21)) {
            val s = orch.pulse(t0 + d * day)
            assertTrue("dzień $d powinien być Active", s is PeriodizationState.Active)
            val meso = (s as PeriodizationState.Active).meso
            weeksSeen += meso.weekInPhase
            assertTrue("weekInPhase ${meso.weekInPhase} nie może przekroczyć phaseLengthWeeks (4)",
                meso.weekInPhase in 1..4)
        }
        assertEquals("tygodnie rosną 1→4", listOf(1, 2, 3, 4), weeksSeen)

        // koniec cyklu (dzień 28) → TransitionDue
        val end = orch.pulse(t0 + 28 * day)
        assertTrue("po 28 dniach → TransitionDue", end is PeriodizationState.TransitionDue)
        val td = end as PeriodizationState.TransitionDue
        val proposal = td.proposal
        val currentMesoId = td.current.id
        assertTrue("propozycja ma dodatni czas trwania", proposal.plannedDurationWeeks > 0)
        assertEquals("z poprzedniej fazy = AKUMULACJA", MesocyclePhase.ACCUMULATION, proposal.fromPhase)

        // daleko w przyszłości (dzień 100) — nadal TransitionDue, weekInPhase ograniczone
        val far = orch.pulse(t0 + 100 * day)
        assertTrue("100 dni → wciąż TransitionDue (nie rozjazd)", far is PeriodizationState.TransitionDue)
        assertTrue("weekInPhase ograniczone do phaseLength",
            (far as PeriodizationState.TransitionDue).current.weekInPhase in 1..4)

        // applyTransition → nowa faza od tygodnia 1
        orch.applyTransition(proposal, currentMesoId = currentMesoId, nowMs = t0 + 28 * day)
        val afterTransition = orch.pulse(t0 + 28 * day + day) // dzień po transition
        assertTrue("po transition mamy aktywny nowy mezocykl", afterTransition is PeriodizationState.Active)
        val newMeso = (afterTransition as PeriodizationState.Active).meso
        assertEquals("nowa faza = rekomendowana", proposal.recommendedNext, newMeso.phase)
        assertTrue("nowa faza startuje od tygodnia 1", newMeso.weekInPhase == 1)
    }
}
