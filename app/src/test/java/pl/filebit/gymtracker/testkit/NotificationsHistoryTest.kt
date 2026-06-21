package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType
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
        seed(NotificationKind.MEAL, "🍽️ Pora na Posiłek 3", "Oznacz status", now - 2000, payload = "3")

        val kit = ViewModelKit(db, context)
        val vm = NotificationsViewModel(
            db.notificationHistoryDao(), NotificationSeenPrefs(context),
            kit.mealConsumptionRepo, kit.adherenceCalc
        )
        var tries = 0
        while (vm.loading.value && tries++ < 200) Thread.sleep(15)

        val items = vm.items.value
        assertEquals("2 wpisy", 2, items.size)
        assertTrue("MEAL niesie payload (numer slotu) do akcji",
            items.any { it.kind == NotificationKind.MEAL && it.mealSlot == 3 })
        assertEquals("oba nieprzeczytane (lastSeen=0)", 2, vm.unreadCount.value)

        vm.markSeen()
        // v2.67.0 — licznik jest teraz REAKTYWNY (Room Flow × lastSeenFlow): markSeen
        // aktualizuje wspólny Singleton, a przeliczenie leci przez combine asynchronicznie.
        var seenTries = 0
        while (vm.unreadCount.value != 0 && seenTries++ < 200) Thread.sleep(15)
        assertEquals("po otwarciu licznik gaśnie", 0, vm.unreadCount.value)
    }

    @Test
    fun `wpis MEAL niesie utrwalony status konsumpcji (po powrocie pokazuje zjedzone)`() = runBlocking {
        val now = System.currentTimeMillis()
        seed(NotificationKind.MEAL, "🍽️ Pora na Posiłek 2", "Oznacz status", now - 1000, payload = "2")

        val kit = ViewModelKit(db, context)
        // user oznaczył Posiłek 2 jako zjedzony (jak z ekranu Diety) — setStatus normalizuje datę do start-dnia
        kit.mealConsumptionRepo.setStatus(now, 2, MealConsumptionStatus.CONSUMED)

        val vm = NotificationsViewModel(
            db.notificationHistoryDao(), NotificationSeenPrefs(context),
            kit.mealConsumptionRepo, kit.adherenceCalc
        )
        // czekaj aż reaktywny pipeline (Room Flow × lastSeen × consumption) dostarczy status
        var tries = 0
        while (vm.items.value.firstOrNull { it.mealSlot == 2 }?.mealStatus !=
            MealConsumptionStatus.CONSUMED && tries++ < 200) Thread.sleep(15)

        val item = vm.items.value.first { it.mealSlot == 2 }
        assertEquals("status z bazy (nie z ulotnego UI) = CONSUMED",
            MealConsumptionStatus.CONSUMED, item.mealStatus)
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
