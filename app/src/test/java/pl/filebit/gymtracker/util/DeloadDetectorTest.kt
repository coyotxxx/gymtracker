package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeloadDetectorTest {

    @Test
    fun `light week with low RPE returns null`() {
        val r = detectDeloadNeed(avgRpe14d = 7.0, sessionsLast35d = 8)
        assertNull(r)
    }

    @Test
    fun `null RPE with few sessions returns null`() {
        val r = detectDeloadNeed(avgRpe14d = null, sessionsLast35d = 10)
        assertNull(r)
    }

    @Test
    fun `high RPE plus many sessions returns HIGH severity`() {
        val r = detectDeloadNeed(avgRpe14d = 8.7, sessionsLast35d = 16)
        assertNotNull(r)
        assertEquals(DeloadSeverity.HIGH, r!!.severity)
    }

    @Test
    fun `very high RPE returns MED regardless of session count`() {
        val r = detectDeloadNeed(avgRpe14d = 9.2, sessionsLast35d = 10)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `multiple stagnations returns MED`() {
        val r = detectDeloadNeed(avgRpe14d = 7.5, sessionsLast35d = 10, stagnationCount = 4)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `long cycle moderate RPE returns LOW`() {
        val r = detectDeloadNeed(avgRpe14d = 7.8, sessionsLast35d = 19)
        assertNotNull(r)
        assertEquals(DeloadSeverity.LOW, r!!.severity)
    }

    @Test
    fun `low session count even with high RPE returns MED via rule 2`() {
        val r = detectDeloadNeed(avgRpe14d = 9.5, sessionsLast35d = 5)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `2 stagnations not enough alone`() {
        val r = detectDeloadNeed(avgRpe14d = 7.5, sessionsLast35d = 10, stagnationCount = 2)
        assertNull(r)
    }

    @Test
    fun `boundary RPE 8_5 with 15 sessions triggers HIGH`() {
        val r = detectDeloadNeed(avgRpe14d = 8.5, sessionsLast35d = 15)
        assertNotNull(r)
        assertEquals(DeloadSeverity.HIGH, r!!.severity)
    }

    @Test
    fun `reason mentions concrete numbers`() {
        val r = detectDeloadNeed(avgRpe14d = 8.7, sessionsLast35d = 16)!!
        assert(r.reason.contains("8.7")) { "Reason should cite RPE: ${r.reason}" }
        assert(r.reason.contains("16")) { "Reason should cite sessions: ${r.reason}" }
    }
}
