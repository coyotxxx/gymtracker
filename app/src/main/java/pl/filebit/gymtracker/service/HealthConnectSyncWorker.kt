package pl.filebit.gymtracker.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.filebit.gymtracker.data.entity.ActivitySource
import pl.filebit.gymtracker.data.health.HealthConnectAvailability
import pl.filebit.gymtracker.data.health.HealthConnectManager
import pl.filebit.gymtracker.data.repository.ActivityRepository

/**
 * Periodic worker — synchronizuje kroki z Health Connect raz dziennie.
 * Pobiera kroki z ostatnich 7 dni i zapisuje do DailyActivityLog.
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val healthConnect: HealthConnectManager,
    private val activityRepo: ActivityRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (healthConnect.checkAvailability() != HealthConnectAvailability.INSTALLED) {
            return Result.success() // brak HC = nie ma co robić, ale nie błąd
        }
        if (!healthConnect.hasAllPermissions()) {
            return Result.success() // brak permission = milcząco kończymy
        }

        val stepsByDay = runCatching { healthConnect.readStepsForLastDays(7) }.getOrNull()
            ?: return Result.success()

        for ((dayMs, steps) in stepsByDay) {
            if (steps > 0) {
                runCatching {
                    activityRepo.setSteps(dayMs, steps, source = ActivitySource.HEALTH_CONNECT)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health_connect_sync"
    }
}
