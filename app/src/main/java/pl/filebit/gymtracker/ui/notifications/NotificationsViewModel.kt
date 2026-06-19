package pl.filebit.gymtracker.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.NotificationHistoryDao
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.NotificationKind
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.MealConsumptionRepository
import pl.filebit.gymtracker.data.repository.NotificationSeenPrefs
import java.util.Calendar
import javax.inject.Inject

/** Jeden wpis historii powiadomień (to, co apka faktycznie wysłała — to samo co w pushu). */
data class NotifHistoryItem(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val timeMs: Long,
    val unread: Boolean,
    /** MealType.name dla MEAL — pozwala na akcje Zjedzone/Pominięte z dzwonka. */
    val mealType: String?
)

/**
 * v2.47.0 — dzwonek = HISTORIA wysłanych powiadomień z DEDYKOWANEJ tabeli
 * (`notification_history`). To samo, co user dostał w pushu — plus akcje (MEAL).
 *
 * Karta coacha = „co teraz"; dzwonek = „co apka wysłała". Nieprzeczytane = wpisy
 * nowsze niż ostatnie otwarcie ([NotificationSeenPrefs]).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val dao: NotificationHistoryDao,
    private val seenPrefs: NotificationSeenPrefs,
    private val consumptionRepo: MealConsumptionRepository,
    private val adherenceCalc: AdherenceCalculator
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // v2.67.0 — JEDNO REAKTYWNE ŹRÓDŁO: wpisy z DB (Room Flow) × ostatnie otwarcie dzwonka
    // (Singleton StateFlow). Dzwonek (instancja VM w topbarze) i ekran historii (osobna
    // instancja) obserwują TE SAME Singletony, więc `markSeen()` gasi licznik NATYCHMIAST
    // wszędzie — koniec „zapala się 2, znika dopiero za drugim razem".
    private val recent: StateFlow<List<NotifHistoryItem>> =
        dao.observeRecent(80)
            .onEach { _loading.value = false }
            .combine(seenPrefs.lastSeenFlow) { rows, lastSeen ->
                rows.map { r ->
                    NotifHistoryItem(
                        id = r.id,
                        kind = runCatching { NotificationKind.valueOf(r.kind) }
                            .getOrDefault(NotificationKind.GENERIC),
                        title = r.title,
                        body = r.body,
                        timeMs = r.timestampMs,
                        unread = r.timestampMs > lastSeen,
                        mealType = r.payload
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<NotifHistoryItem>> = recent

    val unreadCount: StateFlow<Int> = recent
        .map { list -> list.count { it.unread } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Wywoływane gdy user otwiera ekran — wszystko do teraz staje się „przeczytane".
     *  Aktualizuje wspólny Singleton → licznik dzwonka gaśnie reaktywnie (bez reload). */
    fun markSeen() {
        seenPrefs.markSeen()
    }

    /** Akcja Zjedzone/Pominięte wprost z dzwonka — to samo co MealStatusReceiver. */
    fun markMeal(item: NotifHistoryItem, consumed: Boolean) {
        val mealType = runCatching { MealType.valueOf(item.mealType ?: return) }.getOrNull() ?: return
        val status = if (consumed) MealConsumptionStatus.CONSUMED else MealConsumptionStatus.SKIPPED
        val dateMs = startOfDay(item.timeMs)
        viewModelScope.launch {
            runCatching {
                consumptionRepo.setStatus(dateMs, mealType, status)
                adherenceCalc.computeForDate(dateMs)
            }
        }
    }

    private fun startOfDay(ms: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
