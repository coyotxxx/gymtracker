package pl.filebit.gymtracker.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.filebit.gymtracker.data.entity.ActivitySource
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.health.HealthConnectAvailability
import pl.filebit.gymtracker.data.health.HealthConnectManager
import pl.filebit.gymtracker.data.repository.ActivityRepository
import pl.filebit.gymtracker.data.repository.DiagnosticLogger

/**
 * Periodic worker — synchronizuje kroki z Health Connect raz dziennie.
 * Pobiera kroki z ostatnich 7 dni i zapisuje do DailyActivityLog.
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val healthConnect: HealthConnectManager,
    private val activityRepo: ActivityRepository,
    private val diag: DiagnosticLogger
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (healthConnect.checkAvailability() != HealthConnectAvailability.INSTALLED) {
            diag.info(DiagnosticCategory.WORKER, "HealthConnectSyncWorker", "hc_sync_skipped",
                "Sync kroków pominięty — Health Connect niezainstalowany", success = true)
            return Result.success() // brak HC = nie ma co robić, ale nie błąd
        }
        if (!healthConnect.hasAllPermissions()) {
            diag.warn(DiagnosticCategory.WORKER, "HealthConnectSyncWorker", "hc_sync_no_permission",
                "Sync kroków pominięty — brak uprawnień Health Connect")
            return Result.success() // brak permission = milcząco kończymy
        }

        val stepsByDay = runCatching { healthConnect.readStepsForLastDays(7) }.getOrElse {
            diag.error(DiagnosticCategory.ERROR, "HealthConnectSyncWorker", "hc_sync_read_failed",
                "Błąd odczytu kroków z Health Connect", it)
            return Result.success()
        }

        var savedDays = 0
        for ((dayMs, steps) in stepsByDay) {
            if (steps > 0) {
                runCatching {
                    activityRepo.setSteps(dayMs, steps, source = ActivitySource.HEALTH_CONNECT)
                    savedDays++
                }
            }
        }
        diag.info(DiagnosticCategory.WORKER, "HealthConnectSyncWorker", "hc_sync_done",
            "Zsynchronizowano kroki z Health Connect — $savedDays dni",
            dataJson = """{"savedDays":$savedDays}""", success = true)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health_connect_sync"
    }
}
