package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.filebit.gymtracker.data.repository.DietConfig
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planuje powiadomienia o porach posiłków na bazie DietConfig.
 *
 * Strategia: planujemy NA NAJBLIŻSZE 24h każdą porę posiłku jako OneTimeWorkRequest.
 * Pierwsza notyfikacja po starcie aplikacji uruchomi również "rescheduling daily" worker
 * który codziennie o 0:01 przesunie kolejne 24h. (TODO: PeriodicWorkRequest at midnight.)
 *
 * Dla MVP: każdy reschedule wywołuje rescheduleAll() z DietScreen / settings change.
 */
@Singleton
class DietReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Anuluje wszystkie i ustawia nowe. */
    fun rescheduleAll(config: DietConfig) {
        cancelAll()
        if (!config.mealRemindersEnabled) return

        val hours = config.mealHoursDecimal()
        val now = System.currentTimeMillis()
        val workManager = WorkManager.getInstance(context)

        hours.forEachIndexed { idx, h ->
            val slotIndex = idx + 1
            // v2.73.0: spójna etykieta „Posiłek N"; tożsamość = numer slotu.
            val slotLabel = pl.filebit.gymtracker.util.MealSlots.label(slotIndex)
            val target = nextOccurrenceOf(h)
            val delayMs = (target - now).coerceAtLeast(60_000L)

            val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putInt("slot_index", slotIndex)
                        .putString("slot_label", slotLabel)
                        .build()
                )
                .build()

            // v2.46.0 FIX: enqueueUniqueWork (nie enqueue) — inaczej każdy rescheduleAll()
            // (np. każde otwarcie ekranu Diety) DOKŁADAŁ kolejną kopię workera, a cancelAll()
            // po nazwie unikalnej nic nie anulował → ten sam posiłek przypominał N razy.
            workManager.enqueueUniqueWork(
                "${MealReminderWorker.WORK_NAME_PREFIX}$slotIndex",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancelAll() {
        // Anulujemy przez unique work names per slot (WorkManager nie ma cancelByPrefix).
        // v2.73.0: zakres do MAX_MEALS (8).
        for (i in 1..pl.filebit.gymtracker.util.MealSlots.MAX_MEALS) {
            WorkManager.getInstance(context).cancelUniqueWork("${MealReminderWorker.WORK_NAME_PREFIX}$i")
        }
    }

    /** Najbliższa przyszła godzina z hourDecimal (np. 12.5 = 12:30). */
    private fun nextOccurrenceOf(hourDecimal: Double): Long {
        val cal = Calendar.getInstance()
        val targetHour = hourDecimal.toInt()
        val targetMinute = ((hourDecimal - targetHour) * 60).toInt()
        cal.set(Calendar.HOUR_OF_DAY, targetHour)
        cal.set(Calendar.MINUTE, targetMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
