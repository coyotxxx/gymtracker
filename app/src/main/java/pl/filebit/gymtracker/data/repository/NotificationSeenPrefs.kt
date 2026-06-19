package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.45.0 — stan „ostatnio otwarty dzwonek".
 *
 * Dzwonek pokazuje HISTORIĘ realnie wysłanych powiadomień (z `diagnostic_events`,
 * kategoria NOTIFICATION). Nieprzeczytane = wpisy nowsze niż `lastSeenMs`.
 * Otwarcie dzwonka → `markSeen()` ustawia teraz, licznik gaśnie.
 *
 * Świadomie BEZ id-setów i bez dziennego resetu (stary `ReadNotificationsPrefs`) —
 * historia ma timestampy, więc „nieprzeczytane" liczymy po czasie. Prościej, czyściej.
 */
@Singleton
class NotificationSeenPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("notification_seen_prefs", Context.MODE_PRIVATE)

    // v2.67.0 — reaktywne źródło prawdy: zmiana propaguje się NATYCHMIAST do wszystkich
    // obserwatorów (dzwonek w topbarze ORAZ ekran historii — różne instancje VM, wspólny
    // Singleton). Wcześniej dzwonek czytał pref tylko przy `reload()` → spóźniał się o wizytę.
    private val _lastSeenMs = MutableStateFlow(prefs.getLong(KEY_LAST_SEEN_MS, 0L))
    val lastSeenFlow: StateFlow<Long> = _lastSeenMs.asStateFlow()

    /** Czas ostatniego otwarcia dzwonka (epoch ms). 0 = nigdy. */
    fun lastSeenMs(): Long = _lastSeenMs.value

    /** Oznacz wszystko do tej chwili jako przeczytane. */
    fun markSeen() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SEEN_MS, now).apply()
        _lastSeenMs.value = now
    }

    companion object {
        private const val KEY_LAST_SEEN_MS = "last_seen_ms"
    }
}
