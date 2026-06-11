package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planuje WeeklyReportWorker co 7 dni, z initial delay do najbliższego
 * poniedziałku rano (~09:00). Dzięki temu worker raportuje ostatni ZAKOŃCZONY
 * tydzień (poniedziałek = świeże podsumowanie minionego tygodnia).
 *
 * v2.13.0 — domyślnie aktywny (UserProfile.aiAutoGenerateWeeklyReports = true).
 */
@Singleton
class WeeklyReportScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextMondayMorning(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeeklyReportWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WeeklyReportWorker.UNIQUE_WORK_NAME)
    }

    /** Czas (ms) do najbliższego poniedziałku 09:00. Jeśli teraz pon. <09:00 → dziś. */
    private fun millisUntilNextMondayMorning(): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // przesuń na poniedziałek
        while (target.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 7)  // już minął ten poniedziałek → za tydzień
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
    }
}
