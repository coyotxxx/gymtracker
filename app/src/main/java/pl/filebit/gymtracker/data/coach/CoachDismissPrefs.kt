package pl.filebit.gymtracker.data.coach

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.34.1 (U4b) — zamknięcie Karty coacha. Odrzucenie obowiązuje DO KOŃCA DNIA:
 * reakcja znika dziś, ale jeśli sygnał nadal aktualny — wróci jutro (nie chowamy na zawsze
 * czegoś, co realnie trzeba zrobić). Klucz = `CoachReaction.id`.
 */
@Singleton
class CoachDismissPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("coach_dismiss", Context.MODE_PRIVATE)

    fun dismiss(reactionId: String) {
        prefs.edit().putLong("d_$reactionId", System.currentTimeMillis()).apply()
    }

    /** Id-ki odrzucone DZIŚ (ten sam dzień kalendarzowy). Następny dzień → puste. */
    fun activeDismissed(): Set<String> {
        val todayStart = startOfToday()
        return prefs.all.entries
            .filter { it.key.startsWith("d_") && (it.value as? Long ?: 0L) >= todayStart }
            .map { it.key.removePrefix("d_") }
            .toSet()
    }

    private fun startOfToday(): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
