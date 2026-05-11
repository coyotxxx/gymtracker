package pl.filebit.gymtracker.ui.periodization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle

/**
 * Test pure functions `mapToUiState` i `mapToCardUi` z PeriodizationPlanViewModel.kt.
 *
 * Sprawdza że mapowanie z entity TrainingMesocycle → UI state dla ekranu "Plan cyklu"
 * jest poprawne dla wszystkich statusów (ACTIVE/COMPLETED/SKIPPED/PLANNED).
 */
class PeriodizationPlanViewModelTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun meso(
        id: Long = 1L,
        phase: MesocyclePhase = MesocyclePhase.ACCUMULATION,
        status: MesocycleStatus = MesocycleStatus.ACTIVE,
        startDaysAgo: Int = 14,
        plannedEndDaysFromNow: Int = 7,
        endDateMs: Long? = null,
        weekInPhase: Int = 2,
        phaseLengthWeeks: Int = 3
    ) = TrainingMesocycle(
        id = id,
        startDateMs = now - startDaysAgo * day,
        endDateMs = endDateMs,
        plannedEndDateMs = now + plannedEndDaysFromNow * day,
        phase = phase,
        weekInPhase = weekInPhase,
        phaseLengthWeeks = phaseLengthWeeks,
        status = status,
        notes = "test"
    )

    @Test
    fun `pusta lista cykli — empty state`() {
        val state = mapToUiState(cycles = emptyList(), nowMs = now)
        assertNull(state.activeCard)
        assertTrue(state.historyCards.isEmpty())
        assertEquals(0, state.cycles.size)
    }

    @Test
    fun `tylko aktywny — activeCard zapełniony historyCards pusta`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.ACTIVE)),
            nowMs = now
        )
        assertNotNull(state.activeCard)
        assertTrue(state.historyCards.isEmpty())
        assertEquals("TRWA", state.activeCard!!.statusLabel)
    }

    @Test
    fun `1 aktywny + 2 historyczne — wszystkie w odpowiednich slotach`() {
        val state = mapToUiState(
            cycles = listOf(
                meso(id = 1, status = MesocycleStatus.ACTIVE, startDaysAgo = 7, plannedEndDaysFromNow = 14),
                meso(id = 2, status = MesocycleStatus.COMPLETED, startDaysAgo = 28, plannedEndDaysFromNow = -7),
                meso(id = 3, status = MesocycleStatus.COMPLETED, startDaysAgo = 49, plannedEndDaysFromNow = -28)
            ),
            nowMs = now
        )
        assertEquals(1L, state.activeCard?.id)
        assertEquals(2, state.historyCards.size)
        // History posortowane DESC po startDateMs (newest first)
        assertEquals(2L, state.historyCards[0].id)
        assertEquals(3L, state.historyCards[1].id)
    }

    @Test
    fun `ACTIVE cykl — progress oblicza się poprawnie`() {
        // 14 dni minęło, plannedEnd za 7 dni => totalDays = 21, elapsed = 14
        // progress = 14/21 * 100 = ~66%
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.ACTIVE, startDaysAgo = 14, plannedEndDaysFromNow = 7)),
            nowMs = now
        )
        val active = state.activeCard!!
        assertTrue("Progress między 60 a 70%, było ${active.progressPct}",
            active.progressPct in 60..70)
    }

    @Test
    fun `COMPLETED cykl — statusLabel UKOŃCZONY, progress 100`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.COMPLETED, startDaysAgo = 28, plannedEndDaysFromNow = -7)),
            nowMs = now
        )
        val card = state.historyCards.first()
        assertEquals("UKOŃCZONY", card.statusLabel)
        assertEquals(100, card.progressPct)
    }

    @Test
    fun `SKIPPED cykl — statusLabel POMINIĘTY`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.SKIPPED, startDaysAgo = 30, plannedEndDaysFromNow = -7)),
            nowMs = now
        )
        val card = state.historyCards.first()
        assertEquals("POMINIĘTY", card.statusLabel)
    }

    @Test
    fun `wszystkie 5 faz mapuje się na różne kolory`() {
        val cycles = MesocyclePhase.entries.mapIndexed { idx, phase ->
            meso(id = idx.toLong(), phase = phase, status = MesocycleStatus.COMPLETED,
                startDaysAgo = (10 + idx) * 7, plannedEndDaysFromNow = -idx * 7)
        }
        val state = mapToUiState(cycles, now)
        // Wszystkie kolory różne
        val uniqueColors = state.historyCards.map { it.phase.color() }.distinct()
        assertEquals(5, uniqueColors.size)
    }

    @Test
    fun `daysRemainingLabel formatowany poprawnie dla 1 dnia`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.ACTIVE, plannedEndDaysFromNow = 1)),
            nowMs = now
        )
        assertEquals("1 dzień", state.activeCard?.daysRemainingLabel)
    }

    @Test
    fun `daysRemainingLabel dla wielu dni`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.ACTIVE, plannedEndDaysFromNow = 5)),
            nowMs = now
        )
        assertEquals("5 dni", state.activeCard?.daysRemainingLabel)
    }

    @Test
    fun `subtitle ACTIVE zawiera weekInPhase`() {
        val state = mapToUiState(
            cycles = listOf(meso(status = MesocycleStatus.ACTIVE, weekInPhase = 2, phaseLengthWeeks = 3)),
            nowMs = now
        )
        val sub = state.activeCard!!.subtitle
        assertTrue("Subtitle zawiera tydzień", sub.contains("Tydzień 2/3"))
    }
}
