package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType

/**
 * Test pure function `reconstructMesocycles()` (top-level w MesocycleBackfillService.kt).
 *
 * Testujemy bez DAO — tylko logika rekonstrukcji timeline'u na podstawie
 * pierwszego treningu + listy deload events + nowMs.
 *
 * Pełny scenariusz idempotencji (count() > 0 → skip) testowany byłby
 * w integration test z DAO (Robolectric / androidTest). Tu pure logic only.
 */
class MesocycleBackfillServiceTest {

    // ===== Helpery =====

    private val now = 1_700_000_000_000L  // fixed point dla determinizmu
    private val day = 24L * 60 * 60 * 1000
    private val week = 7 * day
    private val months3 = 90 * day

    private fun deload(daysAgo: Int) = TrainingEvent(
        id = 0,
        date = now - daysAgo * day,
        type = TrainingEventType.DELOAD_DETECTED,
        notes = "test fixture"
    )

    // ===== Test cases =====

    @Test
    fun `brak workoutów w oknie (firstWorkout = now) — pusta lista`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now,
            deloadEvents = emptyList(),
            nowMs = now
        )
        assertTrue("Oczekuję pustej listy gdy firstWorkout == now", cycles.isEmpty())
    }

    @Test
    fun `bardzo krótki window (3 dni) — brak cykli (próg minimalny 7 dni)`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - 3 * day,
            deloadEvents = emptyList(),
            nowMs = now
        )
        assertTrue("Mniej niż tydzień danych — brak cykli", cycles.isEmpty())
    }

    @Test
    fun `ciągłe 12 tyg bez deloadów — 1 aktywny cykl ACCUMULATION`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = emptyList(),
            nowMs = now
        )
        assertEquals("1 cykl powinien powstać", 1, cycles.size)
        val active = cycles.first()
        assertEquals(MesocycleStatus.ACTIVE, active.status)
        assertEquals(MesocyclePhase.ACCUMULATION, active.phase)
        assertNull("Active cycle ma endDateMs = null", active.endDateMs)
        assertEquals(now - months3, active.startDateMs)
    }

    @Test
    fun `1 deload w środku — 3 cykle (acc COMPLETED + deload COMPLETED + acc ACTIVE)`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(deload(daysAgo = 60)),
            nowMs = now
        )
        assertEquals("3 cykle: akumulacja → deload → nowy active", 3, cycles.size)

        val accumulation = cycles[0]
        val deloadCycle = cycles[1]
        val active = cycles[2]

        assertEquals(MesocyclePhase.ACCUMULATION, accumulation.phase)
        assertEquals(MesocycleStatus.COMPLETED, accumulation.status)
        assertNotNull("Completed cycle ma endDateMs", accumulation.endDateMs)

        assertEquals(MesocyclePhase.DELOAD, deloadCycle.phase)
        assertEquals(MesocycleStatus.COMPLETED, deloadCycle.status)
        assertEquals(now - 60 * day, deloadCycle.startDateMs)

        assertEquals(MesocyclePhase.ACCUMULATION, active.phase)
        assertEquals(MesocycleStatus.ACTIVE, active.status)
        assertNull(active.endDateMs)
    }

    @Test
    fun `2 deloady — 5 cykli (acc COMPLETED, del COMPLETED, acc COMPLETED, del COMPLETED, acc ACTIVE)`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(
                deload(daysAgo = 70),
                deload(daysAgo = 35)
            ),
            nowMs = now
        )
        assertEquals("5 cykli", 5, cycles.size)
        assertEquals(MesocyclePhase.ACCUMULATION, cycles[0].phase)
        assertEquals(MesocycleStatus.COMPLETED, cycles[0].status)
        assertEquals(MesocyclePhase.DELOAD, cycles[1].phase)
        assertEquals(MesocycleStatus.COMPLETED, cycles[1].status)
        assertEquals(MesocyclePhase.ACCUMULATION, cycles[2].phase)
        assertEquals(MesocycleStatus.COMPLETED, cycles[2].status)
        assertEquals(MesocyclePhase.DELOAD, cycles[3].phase)
        assertEquals(MesocycleStatus.COMPLETED, cycles[3].status)
        assertEquals(MesocyclePhase.ACCUMULATION, cycles[4].phase)
        assertEquals(MesocycleStatus.ACTIVE, cycles[4].status)
        assertNull(cycles[4].endDateMs)
    }

    @Test
    fun `deload trwający teraz (3 dni temu) — status ACTIVE dla deload phase`() {
        // Deload event 3 dni temu, deload phase trwa 7 dni → kończy się za 4 dni
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(deload(daysAgo = 3)),
            nowMs = now
        )
        // Cykl 1: ACC COMPLETED (firstWorkout → deload date)
        // Cykl 2: DELOAD ACTIVE (deload date → +7d), endDateMs = null bo jeszcze trwa
        // Cykl 3: brak (od deloadEnd do now jest -4 dni czyli mniej niż tydzień)
        assertEquals(2, cycles.size)
        val deloadCycle = cycles[1]
        assertEquals(MesocyclePhase.DELOAD, deloadCycle.phase)
        assertEquals(MesocycleStatus.ACTIVE, deloadCycle.status)
        assertNull("Trwający deload — endDateMs null", deloadCycle.endDateMs)
    }

    @Test
    fun `default scale factors per phase są poprawne`() {
        // ACCUMULATION: volume 1.1, intensity 0.95, rpe 7
        val accCycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = emptyList(),
            nowMs = now
        )
        val acc = accCycles.first()
        assertEquals(1.1, acc.volumeProgression, 0.001)
        assertEquals(0.95, acc.intensityProgression, 0.001)
        assertEquals(7, acc.targetRpe)

        // DELOAD: volume 0.6, intensity 0.9, rpe 6
        val delCycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(deload(daysAgo = 30)),
            nowMs = now
        )
        val delCycle = delCycles.first { it.phase == MesocyclePhase.DELOAD }
        assertEquals(0.6, delCycle.volumeProgression, 0.001)
        assertEquals(0.9, delCycle.intensityProgression, 0.001)
        assertEquals(6, delCycle.targetRpe)
    }

    @Test
    fun `trigger reasons są ustawione poprawnie dla każdego typu cyklu`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(deload(daysAgo = 50)),
            nowMs = now
        )
        assertEquals("backfill_from_workouts", cycles[0].triggerReason)
        assertEquals("backfill_from_deload_event", cycles[1].triggerReason)
        assertEquals("backfill_active_cycle", cycles[2].triggerReason)
    }

    @Test
    fun `wszystkie cykle mają wypełnione notes z datami (audit trail)`() {
        val cycles = reconstructMesocycles(
            firstWorkoutMs = now - months3,
            deloadEvents = listOf(deload(daysAgo = 50)),
            nowMs = now
        )
        cycles.forEach {
            assertTrue("Notes nie pusty dla każdego cyklu", it.notes.isNotBlank())
            assertTrue("Notes zaczyna się od 'Backfill v1.13.0'",
                it.notes.startsWith("Backfill v1.13.0"))
        }
    }
}
