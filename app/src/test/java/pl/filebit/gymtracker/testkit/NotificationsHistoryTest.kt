package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.DiagnosticEvent
import pl.filebit.gymtracker.data.entity.DiagnosticLevel
import pl.filebit.gymtracker.data.repository.NotificationSeenPrefs
import pl.filebit.gymtracker.ui.notifications.NotificationsViewModel

/**
 * v2.45.0 — dzwonek = historia wysłanych powiadomień (z diagnostic_events/NOTIFICATION).
 * Sprawdza: tylko kategoria NOTIFICATION, tytuł=message, treść=dataJson, licznik
 * nieprzeczytanych po czasie + markSeen() gasi licznik.
 */
class NotificationsHistoryTest : TestHarness() {

    private fun seed(catName: String, title: String, body: String?, ts: Long) = runBlocking {
        db.diagnosticEventDao().insert(
            DiagnosticEvent(
                timestampMs = ts, category = catName, level = DiagnosticLevel.INFO.name,
                source = "Test", event = "notification_sent", message = title, dataJson = body
            )
        )
    }

    @Test
    fun `dzwonek pokazuje historie powiadomien i liczy nieprzeczytane`() = runBlocking {
        val now = System.currentTimeMillis()
        // 2 powiadomienia + 1 wpis innej kategorii (musi być odfiltrowany).
        seed(DiagnosticCategory.NOTIFICATION.name, "📋 Bilans dnia", "1 z 3 posiłków nieoznaczonych", now - 1000)
        seed(DiagnosticCategory.NOTIFICATION.name, "🍽️ Pora na kolację", "Oznacz status", now - 2000)
        seed(DiagnosticCategory.DETECTOR.name, "debug detektor", null, now - 500)

        val vm = NotificationsViewModel(db.diagnosticEventDao(), NotificationSeenPrefs(context))
        // init{reload()} jest async (Room suspend) — poczekaj aż się załaduje.
        var tries = 0
        while (vm.loading.value && tries++ < 200) Thread.sleep(15)

        val items = vm.items.value
        assertEquals("tylko powiadomienia (DETECTOR odfiltrowany)", 2, items.size)
        assertTrue("tytuł z message", items.any { it.title == "📋 Bilans dnia" })
        assertTrue("treść z dataJson", items.any { it.body == "1 z 3 posiłków nieoznaczonych" })
        assertEquals("oba nieprzeczytane (lastSeen=0)", 2, vm.unreadCount.value)

        vm.markSeen()
        assertEquals("po otwarciu licznik gaśnie", 0, vm.unreadCount.value)
        assertTrue("wszystkie oznaczone przeczytane", vm.items.value.none { it.unread })
    }
}
