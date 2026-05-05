package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.filebit.gymtracker.data.entity.MealType
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
            val slotLabel = labelFor(slotIndex, hours.size)
            val mealType = mealTypeFor(slotIndex, hours.size)
            val target = nextOccurrenceOf(h)
            val delayMs = (target - now).coerceAtLeast(60_000L)

            val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putInt("slot_index", slotIndex)
                        .putString("slot_label", slotLabel)
                        .putString("meal_type", mealType)
                        .build()
                )
                .build()

            workManager.enqueue(request)
        }
    }

    private fun mealTypeFor(slot: Int, total: Int): String = when {
        total == 2 && slot == 1 -> "BREAKFAST"
        total == 2 -> "DINNER"
        total == 3 && slot == 1 -> "BREAKFAST"
        total == 3 && slot == 2 -> "LUNCH"
        total == 3 -> "DINNER"
        total == 4 && slot == 1 -> "BREAKFAST"
        total == 4 && slot == 2 -> "SNACK"
        total == 4 && slot == 3 -> "LUNCH"
        total == 4 -> "DINNER"
        total == 5 && slot == 1 -> "BREAKFAST"
        total == 5 && slot == 2 -> "SNACK"
        total == 5 && slot == 3 -> "LUNCH"
        total == 5 && slot == 4 -> "SNACK"
        total == 5 -> "DINNER"
        else -> "SNACK"
    }

    fun cancelAll() {
        // Anulujemy przez tag (wszystkie meal_reminder_slot_*).
        // WorkManager nie ma cancelByPrefix — używamy unique work names per slot.
        for (i in 1..6) {
            WorkManager.getInstance(context).cancelUniqueWork("${MealReminderWorker.WORK_NAME_PREFIX}$i")
        }
    }

    private fun labelFor(slot: Int, total: Int): String = when {
        total == 2 && slot == 1 -> "śniadanie"
        total == 2 -> "kolacja"
        total == 3 && slot == 1 -> "śniadanie"
        total == 3 && slot == 2 -> "obiad"
        total == 3 -> "kolacja"
        total == 4 && slot == 1 -> "śniadanie"
        total == 4 && slot == 2 -> "drugie śniadanie"
        total == 4 && slot == 3 -> "obiad"
        total == 4 -> "kolacja"
        total >= 5 && slot == 1 -> "śniadanie"
        total >= 5 && slot == total -> "kolacja"
        else -> "posiłek $slot"
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
