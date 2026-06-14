package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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

    /** Czas ostatniego otwarcia dzwonka (epoch ms). 0 = nigdy. */
    fun lastSeenMs(): Long = prefs.getLong(KEY_LAST_SEEN_MS, 0L)

    /** Oznacz wszystko do tej chwili jako przeczytane. */
    fun markSeen() {
        prefs.edit().putLong(KEY_LAST_SEEN_MS, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val KEY_LAST_SEEN_MS = "last_seen_ms"
    }
}
