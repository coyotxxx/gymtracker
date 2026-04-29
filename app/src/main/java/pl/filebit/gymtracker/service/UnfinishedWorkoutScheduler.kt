package pl.filebit.gymtracker.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnfinishedWorkoutScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(delayHours: Int) {
        val request = OneTimeWorkRequestBuilder<UnfinishedWorkoutWorker>()
            .setInitialDelay(delayHours.toLong(), TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UnfinishedWorkoutWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(UnfinishedWorkoutWorker.UNIQUE_WORK_NAME)
    }
}
