package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.ui.plans.PlanExerciseWithDetail

class SupersetCleanerTest {

    private fun ped(id: Long, day: Int, group: String?): PlanExerciseWithDetail {
        return PlanExerciseWithDetail(
            planEx = PlanExercise(
                id = id,
                planId = 1L,
                exerciseId = 100L + id,
                dayOfWeek = day,
                orderIndex = id.toInt(),
                supersetGroup = group
            ),
            exercise = null,
            sets = emptyList()
        )
    }

    @Test
    fun `lone partner gets supersetGroup nulled`() {
        // Pn: ćw1 (A), ćw3 (samodzielne). ćw2 (A) został usunięty wcześniej.
        // Po wyczyszczeniu — ćw1 zostaje sam w grupie A → null.
        val cleaned = cleanOrphanedSupersets(
            listOf(
                ped(1L, day = 1, group = "A"),
                ped(3L, day = 1, group = null)
            )
        )
        assertNull(cleaned[0].planEx.supersetGroup)
        assertNull(cleaned[1].planEx.supersetGroup)
    }

    @Test
    fun `pair stays intact`() {
        // Dwa ćwiczenia w tej samej grupie A — nie czyść
        val cleaned = cleanOrphanedSupersets(
            listOf(
                ped(1L, day = 1, group = "A"),
                ped(2L, day = 1, group = "A")
            )
        )
        assertEquals("A", cleaned[0].planEx.supersetGroup)
        assertEquals("A", cleaned[1].planEx.supersetGroup)
    }

    @Test
    fun `triplet stays intact`() {
        // Trzy ćwiczenia w grupie B
        val cleaned = cleanOrphanedSupersets(
            listOf(
                ped(1L, day = 2, group = "B"),
                ped(2L, day = 2, group = "B"),
                ped(3L, day = 2, group = "B")
            )
        )
        assertEquals("B", cleaned[0].planEx.supersetGroup)
        assertEquals("B", cleaned[1].planEx.supersetGroup)
        assertEquals("B", cleaned[2].planEx.supersetGroup)
    }

    @Test
    fun `same group across different days are independent`() {
        // Pn:A (1 ćw, lone) | Wt:A (2 ćw, valid pair)
        // Pn:A → null (lone), Wt:A → zostaje
        val cleaned = cleanOrphanedSupersets(
            listOf(
                ped(1L, day = 1, group = "A"),
                ped(2L, day = 2, group = "A"),
                ped(3L, day = 2, group = "A")
            )
        )
        assertNull(cleaned[0].planEx.supersetGroup)  // Pn osierocone
        assertEquals("A", cleaned[1].planEx.supersetGroup)
        assertEquals("A", cleaned[2].planEx.supersetGroup)
    }

    @Test
    fun `empty list returns empty`() {
        val cleaned = cleanOrphanedSupersets(emptyList())
        assertEquals(0, cleaned.size)
    }

    @Test
    fun `mixed groups in same day`() {
        // Pn: ćw1+ćw2 = A, ćw3 = B (lone), ćw4+ćw5 = C
        val cleaned = cleanOrphanedSupersets(
            listOf(
                ped(1L, day = 1, group = "A"),
                ped(2L, day = 1, group = "A"),
                ped(3L, day = 1, group = "B"),
                ped(4L, day = 1, group = "C"),
                ped(5L, day = 1, group = "C")
            )
        )
        assertEquals("A", cleaned[0].planEx.supersetGroup)
        assertEquals("A", cleaned[1].planEx.supersetGroup)
        assertNull(cleaned[2].planEx.supersetGroup)  // B lone
        assertEquals("C", cleaned[3].planEx.supersetGroup)
        assertEquals("C", cleaned[4].planEx.supersetGroup)
    }
}
