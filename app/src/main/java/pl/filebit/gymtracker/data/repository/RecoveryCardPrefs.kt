package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stan UI: czy user dismissował kartę REGENERACJA na dziś.
 * Dismiss obowiązuje do końca dnia kalendarzowego — następny dzień karta wraca.
 *
 * SharedPreferences (nie DB) — to ulotny stan UI.
 */
@Singleton
class RecoveryCardPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("recovery_card_prefs", Context.MODE_PRIVATE)

    /** Czy user zamknął kartę dla bieżącego dnia kalendarzowego? */
    fun isDismissedForToday(): Boolean {
        val saved = prefs.getLong(KEY_DISMISSED_DATE, 0L)
        if (saved == 0L) return false
        return saved == startOfTodayMs()
    }

    fun dismissForToday() {
        prefs.edit().putLong(KEY_DISMISSED_DATE, startOfTodayMs()).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_DISMISSED_DATE).apply()
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val KEY_DISMISSED_DATE = "dismissed_date_ms"
    }
}
