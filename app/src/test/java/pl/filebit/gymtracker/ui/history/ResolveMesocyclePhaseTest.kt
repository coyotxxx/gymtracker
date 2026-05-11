package pl.filebit.gymtracker.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle

/**
 * v1.19.0 — pure function `resolveMesocyclePhase` mapuje (lista mesocykli, timestamp workoutu) → MesocyclePhase|null.
 *
 * Algorytm: szuka mesocyklu którego startDate ≤ workoutStartMs < (endDate ?: plannedEndDateMs).
 */
class ResolveMesocyclePhaseTest {

    private val day = 24L * 60 * 60 * 1000

    private fun meso(
        id: Long,
        phase: MesocyclePhase,
        startMs: Long,
        endMs: Long? = null,
        plannedEndMs: Long = startMs + 21 * day,
        status: MesocycleStatus = MesocycleStatus.COMPLETED
    ) = TrainingMesocycle(
        id = id,
        startDateMs = startMs,
        endDateMs = endMs,
        plannedEndDateMs = plannedEndMs,
        phase = phase,
        status = status
    )

    @Test
    fun `pusta lista — null`() {
        assertNull(resolveMesocyclePhase(emptyList(), 1_700_000_000_000L))
    }

    @Test
    fun `workout w środku COMPLETED cyklu — zwraca fazę`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(
            meso(1L, MesocyclePhase.ACCUMULATION, start, endMs = start + 21 * day)
        )
        val workoutAt = start + 10 * day
        assertEquals(MesocyclePhase.ACCUMULATION, resolveMesocyclePhase(mesocycles, workoutAt))
    }

    @Test
    fun `workout w aktywnym cyklu (endDate=null) — używa plannedEndDateMs`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(
            meso(1L, MesocyclePhase.INTENSIFICATION, start, endMs = null,
                plannedEndMs = start + 28 * day, status = MesocycleStatus.ACTIVE)
        )
        val workoutAt = start + 14 * day
        assertEquals(MesocyclePhase.INTENSIFICATION, resolveMesocyclePhase(mesocycles, workoutAt))
    }

    @Test
    fun `workout po zakończonym cyklu — null`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(
            meso(1L, MesocyclePhase.DELOAD, start, endMs = start + 7 * day)
        )
        val workoutAt = start + 10 * day // 3 dni PO końcu
        assertNull(resolveMesocyclePhase(mesocycles, workoutAt))
    }

    @Test
    fun `workout przed startem cyklu — null`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(
            meso(1L, MesocyclePhase.PEAKING, start, endMs = start + 14 * day)
        )
        val workoutAt = start - 5 * day
        assertNull(resolveMesocyclePhase(mesocycles, workoutAt))
    }

    @Test
    fun `wiele cykli sekwencyjnych — zwraca prawidłowy dla danego timestampu`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(
            meso(2L, MesocyclePhase.INTENSIFICATION, start + 21 * day, endMs = start + 42 * day),
            meso(1L, MesocyclePhase.ACCUMULATION, start, endMs = start + 21 * day)
        )
        // workout w akumulacji
        assertEquals(MesocyclePhase.ACCUMULATION, resolveMesocyclePhase(mesocycles, start + 10 * day))
        // workout w intensyfikacji
        assertEquals(MesocyclePhase.INTENSIFICATION, resolveMesocyclePhase(mesocycles, start + 30 * day))
    }

    @Test
    fun `workout dokładnie na startDateMs — zwraca fazę`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(meso(1L, MesocyclePhase.RECOVERY, start, endMs = start + 7 * day))
        assertEquals(MesocyclePhase.RECOVERY, resolveMesocyclePhase(mesocycles, start))
    }

    @Test
    fun `workout dokładnie na endDateMs — null (exclusive end)`() {
        val start = 1_700_000_000_000L
        val mesocycles = listOf(meso(1L, MesocyclePhase.DELOAD, start, endMs = start + 7 * day))
        // workoutAt == endDateMs nie jest "w cyklu" (exclusive end)
        assertNull(resolveMesocyclePhase(mesocycles, start + 7 * day))
    }
}
