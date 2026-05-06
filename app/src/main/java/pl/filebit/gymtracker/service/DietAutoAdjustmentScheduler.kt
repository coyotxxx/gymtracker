package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planuje DietAutoAdjustmentWorker co 14 dni.
 *
 * Filozofia: krótsze interwały generują szum (waga waha się ±1 kg w trakcie tygodnia).
 * 14 dni = wystarczająco długi okno żeby zauważyć trend, wystarczająco krótki
 * żeby reagować zanim zboczymy z toru.
 */
@Singleton
class DietAutoAdjustmentScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<DietAutoAdjustmentWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(7, TimeUnit.DAYS)
            .build()
        // REPLACE — żeby zaktualizować z 14d na 7d u userów którzy już mają zaplanowany worker
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DietAutoAdjustmentWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(DietAutoAdjustmentWorker.UNIQUE_WORK_NAME)
    }
}
