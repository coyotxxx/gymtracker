package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveAiCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Codzienny periodic work — AI sprawdza recovery/stagnacje/ból i wysyła
     * notyfikację gdy wykryje sygnał. Używa minimalnego okna (24h ± 30 min)
     * aby system mógł zaplanować na korzystną porę.
     */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<ProactiveAiCheckWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ProactiveAiCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ProactiveAiCheckWorker.UNIQUE_WORK_NAME)
    }
}
