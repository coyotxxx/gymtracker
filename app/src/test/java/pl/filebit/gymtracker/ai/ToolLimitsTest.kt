package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.18.0 — testy dla ToolLimits (data class) + logika sliding window rate limit.
 *
 * Algorytm rate limit (z AiToolHandler.execute):
 *  1. windowStart = now - 60_000
 *  2. usuń timestamps < windowStart z deque
 *  3. jeśli timestamps.size >= maxCallsPerMinute → error
 *  4. dodaj now do deque
 *
 * Test sprawdza algorytm w izolacji — bez DI Hilt.
 */
class ToolLimitsTest {

    @Test
    fun `defaults sane`() {
        val l = ToolLimits()
        assertEquals(30, l.maxCallsPerMinute)
        assertEquals(500 * 1024, l.maxResultSizeBytes)
        assertEquals(30, l.maxWorkouts)
        assertEquals(100, l.maxEvents)
    }

    /**
     * Symulacja sliding window — 30 wywołań w 60s mieści się w limicie,
     * 31. odrzucone.
     */
    @Test
    fun `30 calls fit in window, 31st rejected`() {
        val limit = 30
        val window = 60_000L
        val deque = ArrayDeque<Long>()
        val now = 1_700_000_000_000L

        // 30 calls w sekundy 0..29
        for (i in 0 until 30) {
            val t = now + i * 1_000L
            // cleanup expired
            while (deque.isNotEmpty() && deque.first() < t - window) deque.removeFirst()
            assertTrue("call $i should fit", deque.size < limit)
            deque.addLast(t)
        }
        assertEquals(30, deque.size)

        // 31. call w sekundzie 30 — wszystko nadal w oknie → REJECT
        val t31 = now + 30_000L
        while (deque.isNotEmpty() && deque.first() < t31 - window) deque.removeFirst()
        assertTrue("31. call should be rejected", deque.size >= limit)
    }

    /**
     * Po 60+ sekundach pierwszy timestamp wypada z okna — kolejne calls znowu OK.
     */
    @Test
    fun `after 60s old timestamps expire, new calls allowed`() {
        val limit = 30
        val window = 60_000L
        val deque = ArrayDeque<Long>()
        val baseTime = 1_700_000_000_000L

        // Wypełnij 30 calls w sekundach 0..29
        for (i in 0 until 30) deque.addLast(baseTime + i * 1_000L)

        // Po 61 sekundach od pierwszej (=31s od ostatniej) — pierwsza powinna wygasnąć
        val tNew = baseTime + 61_000L
        while (deque.isNotEmpty() && deque.first() < tNew - window) deque.removeFirst()

        assertTrue("przynajmniej 1 stary timestamp wygasł", deque.size < 30)
        assertTrue("nowy call powinien się zmieścić", deque.size < limit)
    }

    @Test
    fun `size cap 500KB enforced`() {
        val l = ToolLimits()
        val justUnder = "x".repeat(l.maxResultSizeBytes - 1)
        val justOver = "x".repeat(l.maxResultSizeBytes + 1)
        assertTrue("just under fits", justUnder.toByteArray(Charsets.UTF_8).size <= l.maxResultSizeBytes)
        assertTrue("just over exceeds", justOver.toByteArray(Charsets.UTF_8).size > l.maxResultSizeBytes)
    }
}
