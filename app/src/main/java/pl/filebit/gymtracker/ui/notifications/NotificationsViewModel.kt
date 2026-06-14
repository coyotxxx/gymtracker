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
            val events = runCatching { diagnosticEventDao.getRecentByCategory(cat, 60) }
                .getOrDefault(emptyList())
            _items.value = events.map { e ->
                NotifHistoryItem(
                    id = e.id,
                    title = e.message.ifBlank { e.event },
                    body = e.dataJson.orEmpty(),
                    timeMs = e.timestampMs,
                    unread = e.timestampMs > lastSeen
                )
            }
            _unreadCount.value = runCatching {
                diagnosticEventDao.countByCategorySince(cat, lastSeen)
            }.getOrDefault(0)
            _loading.value = false
        }
    }

    /** Wywoływane gdy user otwiera ekran — wszystko do teraz staje się „przeczytane". */
    fun markSeen() {
        seenPrefs.markSeen()
        _unreadCount.value = 0
        _items.value = _items.value.map { it.copy(unread = false) }
    }
}
