package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.WeeklyPlanOverride

class WeekScheduleResolverTest {

    private val weekStart = 1_700_000_000_000L

    @Test
    fun `no overrides returns base schedule`() {
        val base = mapOf(1L to setOf(1, 3, 5))
        val result = resolveWeekSchedule(base, emptyList())
        assertEquals(3, result.size)
        assertEquals(1, result[1]?.size)
        assertEquals(1, result[3]?.size)
        assertEquals(1, result[5]?.size)
        assertTrue(result[1]?.first()?.isMoved == false)
    }

    @Test
    fun `move from monday to thursday`() {
        val base = mapOf(1L to setOf(1, 3, 5))
        val overrides = listOf(
            WeeklyPlanOverride(weekStartMillis = weekStart, planId = 1L, originalDayOfWeek = 1, targetDayOfWeek = 4)
        )
        val result = resolveWeekSchedule(base, overrides)
        assertNull(result[1])  // poniedziałek pusty
        assertEquals(1, result[4]?.size)
        assertEquals(true, result[4]?.first()?.isMoved)
        assertEquals(1, result[4]?.first()?.sourceDayOfWeek)
    }

    @Test
    fun `skip wednesday`() {
        val base = mapOf(1L to setOf(1, 3, 5))
        val overrides = listOf(
            WeeklyPlanOverride(weekStartMillis = weekStart, planId = 1L, originalDayOfWeek = 3, targetDayOfWeek = -1)
        )
        val result = resolveWeekSchedule(base, overrides)
        assertNull(result[3])  // środa skipped
        assertEquals(1, result[1]?.size)
        assertEquals(1, result[5]?.size)
    }

    @Test
    fun `multiple plans non-overlapping`() {
        val base = mapOf(
            1L to setOf(1, 3, 5),
            2L to setOf(2)
        )
        val result = resolveWeekSchedule(base, emptyList())
        assertEquals(4, result.size)
    }

    @Test
    fun `currentWeekStart returns Monday 00 00`() {
        // 2024-03-15 (Friday) 14:00 UTC+0
        val friday14 = 1_710_511_200_000L
        val ws = currentWeekStartMillis(friday14)
        // Sprawdź że ws < friday14 i że dayOfWeek dla ws to Monday
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ws }
        assertEquals(java.util.Calendar.MONDAY, cal.get(java.util.Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(java.util.Calendar.MINUTE))
    }
}
