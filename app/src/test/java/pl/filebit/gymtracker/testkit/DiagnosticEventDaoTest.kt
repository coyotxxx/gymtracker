package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DiagnosticEvent

/**
 * v2.20.0 — warstwa storage logu diagnostycznego (insert / getRecent / cleanup).
 */
class DiagnosticEventDaoTest : TestHarness() {

    private fun ev(event: String, ts: Long = System.currentTimeMillis()) = DiagnosticEvent(
        timestampMs = ts, category = "WORKER", level = "INFO",
        source = "Test", event = event, message = "msg"
    )

    @Test
    fun `insert i getRecent zwraca najnowsze`() = runBlocking {
        val dao = db.diagnosticEventDao()
        dao.insert(ev("first", ts = 1000))
        dao.insert(ev("second", ts = 2000))
        val recent = dao.getRecent(10)
        assertEquals(2, recent.size)
        assertEquals("najnowszy pierwszy", "second", recent.first().event)
    }

    @Test
    fun `deleteOlderThan usuwa tylko stare wpisy`() = runBlocking {
        val dao = db.diagnosticEventDao()
        val now = System.currentTimeMillis()
        dao.insert(ev("old", ts = now - 40L * 24 * 3600 * 1000))   // 40 dni temu
        dao.insert(ev("fresh", ts = now))
        dao.deleteOlderThan(now - 30L * 24 * 3600 * 1000)          // cutoff 30 dni
        val recent = dao.getRecent(10)
        assertEquals(1, recent.size)
        assertEquals("fresh", recent.first().event)
    }
}
