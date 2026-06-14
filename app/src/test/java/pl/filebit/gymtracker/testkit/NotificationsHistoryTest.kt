package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.NotificationHistory
import pl.filebit.gymtracker.data.entity.NotificationKind
import pl.filebit.gymtracker.data.repository.NotificationHistoryStore
import pl.filebit.gymtracker.data.repository.NotificationSeenPrefs
import pl.filebit.gymtracker.ui.notifications.NotificationsViewModel

/**
 * v2.47.0 — dzwonek = dedykowana tabela `notification_history` (nie debug-log).
 * Sprawdza: mapowanie + licznik nieprzeczytanych + markSeen, oraz dedup zapisu w Store.
 */
class NotificationsHistoryTest : TestHarness() {

    private fun seed(kind: NotificationKind, title: String, body: String, ts: Long, payload: String? = null) =
        runBlocking {
            db.notificationHistoryDao().insert(
                NotificationHistory(timestampMs = ts, kind = kind.name, title = title, body = body, payload = payload)
            )
        }

    @Test
    fun `dzwonek mapuje historie i liczy nieprzeczytane`() = runBlocking {
        val now = System.currentTimeMillis()
        seed(NotificationKind.REVIEW, "Bilans dnia", "1 z 3 posiłków nieoznaczonych", now - 1000)
        seed(NotificationKind.MEAL, "🍽️ Pora na kolacja", "Oznacz status", now - 2000, payload = "DINNER")

        val kit = ViewModelKit(db, context)
        val vm = NotificationsViewModel(
            db.notificationHistoryDao(), NotificationSeenPrefs(context),
            kit.mealConsumptionRepo, kit.adherenceCalc
        )
        var tries = 0
        while (vm.loading.value && tries++ < 200) Thread.sleep(15)

        val items = vm.items.value
        assertEquals("2 wpisy", 2, items.size)
        assertTrue("MEAL niesie payload (typ posiłku) do akcji",
            items.any { it.kind == NotificationKind.MEAL && it.mealType == "DINNER" })
        assertEquals("oba nieprzeczytane (lastSeen=0)", 2, vm.unreadCount.value)

        vm.markSeen()
        assertEquals("po otwarciu licznik gaśnie", 0, vm.unreadCount.value)
    }

    @Test
    fun `store dedupuje powtorki tego samego tytulu`() = runBlocking {
        val store = NotificationHistoryStore(db.notificationHistoryDao())
        repeat(5) { store.record(NotificationKind.MEAL, "🍽️ Pora na kolacja", "Oznacz status", "DINNER") }
        // record() jest async (IO, serializowane) — poczekaj aż pierwszy wpis się pojawi,
        // potem chwilę dłużej (pozostałe 4 zostaną zdedupowane, nie zwiększą licznika).
        var tries = 0
        while (db.notificationHistoryDao().getRecent(50).isEmpty() && tries++ < 200) Thread.sleep(15)
        Thread.sleep(250)
        val count = db.notificationHistoryDao().getRecent(50).size
        assertEquals("5× ten sam tytuł w oknie → 1 wpis", 1, count)
    }
}
