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
 * v2.31.0 — planuje DailyReviewWorker codziennie o ~21:00 (initial delay do najbliższej 21:00).
 * Domyślnie aktywny pod przełącznikiem aiProactiveChecksEnabled (jak inne proaktywne checki).
 */
@Singleton
class DailyReviewScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<DailyReviewWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextEvening(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyReviewWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(DailyReviewWorker.UNIQUE_WORK_NAME)
    }

    /** Czas (ms) do najbliższej 21:00. Jeśli teraz jest po 21:00 → jutro 21:00. */
    private fun millisUntilNextEvening(): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, REVIEW_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
    }

    companion object {
        const val REVIEW_HOUR = 21
    }
}
