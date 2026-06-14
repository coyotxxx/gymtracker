package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generyczny prefs do dismiss kart alertowych na bieżący dzień kalendarzowy.
 *
 * Każda karta ma własny `cardKey` (np. "recovery", "phase", "readiness", "load").
 * Dismiss obowiązuje do końca dnia — nowy dzień karty wracają.
 */
@Singleton
class DismissedCardsPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("dismissed_cards_prefs", Context.MODE_PRIVATE)

    /** Czy karta o danym kluczu została zamknięta na dziś? */
    fun isDismissedToday(cardKey: String): Boolean {
        val saved = prefs.getLong(cardKey, 0L)
        if (saved == 0L) return false
        return saved == startOfTodayMs()
    }

    fun dismissToday(cardKey: String) {
        prefs.edit().putLong(cardKey, startOfTodayMs()).apply()
    }

    /** Reset wszystkich (np. dla debugu). */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    object CardKeys {
        const val PHASE = "phase"
        const val READINESS = "readiness"
        const val LOAD = "load"
    }
}
