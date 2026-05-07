package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stan UI: które ID powiadomień zostały oznaczone jako przeczytane.
 *
 * Filozofia:
 * - NotificationCenter generuje alerty live z analyzerów (nie persystuje)
 * - User otwiera NotificationsScreen → wszystkie obecne ID są zapisane jako "read"
 * - Counter w Bell = alerty których ID nie ma w "read" set
 * - Gdy alert znika (problem rozwiązany) — jego ID przestaje być w "active list",
 *   ale nadal jest w "read" — to OK, bo gdy znowu się pojawi, użytkownik chce notice
 *
 * **Reset codzienny:** żeby user codziennie rano miał świeży obraz, wszystkie
 * "read" są kasowane gdy data się zmieni. Czyli:
 * - dziś rano: 3 alerty, counter 3
 * - klikasz bell, counter 0
 * - wieczorem widzisz tę samą notyfikację — counter dalej 0 (przeczytałeś rano)
 * - jutro rano: te same alerty są ale counter znów 3 (nowy dzień)
 */
@Singleton
class ReadNotificationsPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("read_notifications_prefs", Context.MODE_PRIVATE)

    fun isRead(notificationId: String): Boolean {
        ensureFreshDay()
        val readIds = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        return notificationId in readIds
    }

    fun markAllRead(notificationIds: Collection<String>) {
        ensureFreshDay()
        val current = prefs.getStringSet(KEY_READ_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(notificationIds)
        prefs.edit()
            .putStringSet(KEY_READ_IDS, current)
            .putLong(KEY_LAST_DAY_MS, todayStartMs())
            .apply()
    }

    fun unreadCount(activeIds: Collection<String>): Int {
        ensureFreshDay()
        val readIds = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        return activeIds.count { it !in readIds }
    }

    /** Reset gdy data się zmieniła — codziennie rano użytkownik widzi powiadomienia od nowa. */
    private fun ensureFreshDay() {
        val savedDay = prefs.getLong(KEY_LAST_DAY_MS, 0L)
        val today = todayStartMs()
        if (savedDay != 0L && savedDay != today) {
            prefs.edit().remove(KEY_READ_IDS).putLong(KEY_LAST_DAY_MS, today).apply()
        }
    }

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val KEY_READ_IDS = "read_ids"
        private const val KEY_LAST_DAY_MS = "last_day_ms"
    }
}
