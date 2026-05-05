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
class HealthConnectSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Codzienny sync (24h) — KEEP policy (nie nadpisuje istniejącego). */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HealthConnectSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(HealthConnectSyncWorker.UNIQUE_WORK_NAME)
    }
}
