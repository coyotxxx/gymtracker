package pl.filebit.gymtracker.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.DiagnosticEventDao
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.repository.NotificationSeenPrefs
import javax.inject.Inject

/** Jeden wpis historii powiadomień (to, co apka faktycznie wysłała). */
data class NotifHistoryItem(
    val id: Long,
    val title: String,
    val body: String,
    val timeMs: Long,
    val unread: Boolean
)

/**
 * v2.45.0 — dzwonek = HISTORIA wysłanych powiadomień.
 *
 * Źródło: `diagnostic_events` (kategoria NOTIFICATION) — log „co apka zrobiła",
 * który i tak powstaje. Zero nowej tabeli, zero duplikatu Karty coacha (ta pokazuje
 * stan „teraz", dzwonek — historię „co poszło"). Nieprzeczytane = wpisy nowsze niż
 * ostatnie otwarcie ([NotificationSeenPrefs]).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val diagnosticEventDao: DiagnosticEventDao,
    private val seenPrefs: NotificationSeenPrefs
) : ViewModel() {

    private val _items = MutableStateFlow<List<NotifHistoryItem>>(emptyList())
    val items: StateFlow<List<NotifHistoryItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init { reload() }

    fun reload() {
        _loading.value = true
        viewModelScope.launch {
            val cat = DiagnosticCategory.NOTIFICATION.name
            val lastSeen = seenPrefs.lastSeenMs()
            val events = runCatching { diagnosticEventDao.getRecentByCategory(cat, 80) }
                .getOrDefault(emptyList())
            val mapped = events.map { e ->
                NotifHistoryItem(
                    id = e.id,
                    title = cleanTitle(e.message.ifBlank { e.event }),
                    body = cleanBody(e.dataJson),
                    timeMs = e.timestampMs,
                    unread = e.timestampMs > lastSeen
                )
            }
            val deduped = dedupe(mapped)
            _items.value = deduped
            // Licznik liczony z ODFILTROWANEJ listy — spójny z tym, co user widzi (nie z surowych
            // wierszy logu, gdzie powtórki zawyżałyby badge).
            _unreadCount.value = deduped.count { it.unread }
            _loading.value = false
        }
    }

    /** Wywoływane gdy user otwiera ekran — wszystko do teraz staje się „przeczytane". */
    fun markSeen() {
        seenPrefs.markSeen()
        _unreadCount.value = 0
        _items.value = _items.value.map { it.copy(unread = false) }
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 30L * 60 * 1000  // 30 min

        /**
         * Czyści tytuł do ludzkiej postaci. Stare wpisy (sprzed v2.45.0) miały w `message`
         * meta-opis logu („Wysłano notyfikację…") — zdejmujemy te prefiksy. Nowe wpisy mają
         * już czysty tytuł, więc przechodzą bez zmian.
         */
        internal fun cleanTitle(raw: String): String {
            var t = raw.trim()
            t = t.removePrefix("Wysłano notyfikację trenera w tle: ").trim()
            t = t.removePrefix("Wysłano notyfikację alertu: ").trim()
            t = t.removePrefix("Wysłano notyfikację ").trim()
            // „Przypomnienie o posiłku: kolacja" → „🍽️ Pora na kolację"
            val mealPrefix = "Przypomnienie o posiłku: "
            if (t.startsWith(mealPrefix)) {
                t = "🍽️ Pora na " + t.removePrefix(mealPrefix).trim()
            }
            return t.ifBlank { "Powiadomienie" }
        }

        /** Surowy JSON debugowy ({"slotIndex":3,…}) nie jest treścią dla usera — ukrywamy. */
        internal fun cleanBody(dataJson: String?): String {
            val b = dataJson?.trim().orEmpty()
            return if (b.startsWith("{") || b.startsWith("[")) "" else b
        }

        /** Zwija powtórki tego samego tytułu w oknie [DEDUP_WINDOW_MS] (np. przypomnienie ×9). */
        internal fun dedupe(items: List<NotifHistoryItem>): List<NotifHistoryItem> {
            val kept = mutableListOf<NotifHistoryItem>()
            for (item in items) {  // wejście posortowane malejąco po czasie
                val dup = kept.any {
                    it.title == item.title && kotlin.math.abs(it.timeMs - item.timeMs) <= DEDUP_WINDOW_MS
                }
                if (!dup) kept += item
            }
            return kept
        }
    }
}
