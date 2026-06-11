package pl.filebit.gymtracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.filebit.gymtracker.MainActivity
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.WeeklyReportService
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * Co tydzień (poniedziałek rano) generuje raport za OSTATNI ZAKOŃCZONY tydzień,
 * jeśli jeszcze nie istnieje. Po sukcesie wysyła notyfikację z deep linkiem do
 * ekranu raportu.
 *
 * Milczy (Result.success bez notyfikacji) gdy:
 *  - flaga aiAutoGenerateWeeklyReports = false,
 *  - brak skonfigurowanego AI,
 *  - raport tego tygodnia już istnieje (dedup),
 *  - brak treningów w tym tygodniu (generateForWeek → failure).
 */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profileRepo: UserProfileRepository,
    private val aiPrefs: AiPreferences,
    private val service: WeeklyReportService,
    private val reportDao: AiWeeklyReportDao,
    private val diag: pl.filebit.gymtracker.data.repository.DiagnosticLogger
) : CoroutineWorker(appContext, params) {

    private val cat = pl.filebit.gymtracker.data.entity.DiagnosticCategory.REPORT
    private val src = "WeeklyReportWorker"

    override suspend fun doWork(): Result {
        val profile = runCatching { profileRepo.get() }.getOrNull() ?: return Result.success()
        if (!profile.aiAutoGenerateWeeklyReports) return Result.success()
        if (!aiPrefs.load().isConnected) {
            diag.info(cat, src, "report_skipped", "Pominięto auto-raport: AI nieskonfigurowane", success = false)
            return Result.success()
        }

        val weekStart = service.lastCompletedWeekStartMillis()
        // Dedup — raport tego tygodnia już jest
        if (runCatching { reportDao.getForWeek(weekStart) }.getOrNull() != null) {
            diag.info(cat, src, "report_skipped", "Pominięto: raport tego tygodnia już istnieje (dedup)")
            return Result.success()
        }

        val result = runCatching { service.generateForWeek(weekStart) }.getOrNull()
        if (result?.isSuccess == true) {
            diag.info(cat, src, "report_generated", "Auto-raport tygodniowy wygenerowany", success = true)
            notifyReportReady()
        } else {
            val reason = result?.exceptionOrNull()?.message ?: "nieznany"
            diag.warn(cat, src, "report_failed", "Auto-raport nie powstał: $reason")
        }
        return Result.success()
    }

    private fun notifyReportReady() {
        val ctx = applicationContext
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WEEKLY_REPORT, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📊 Raport tygodniowy gotowy")
            .setContentText("Podsumowanie minionego tygodnia + rekomendacje na nowy. Otwórz, by zobaczyć.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Podsumowanie minionego tygodnia treningowego + konkretne rekomendacje na nowy. Otwórz, by zobaczyć.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "weekly_report_channel"
        const val NOTIFICATION_ID = 5300
        const val UNIQUE_WORK_NAME = "weekly_report"
        const val EXTRA_OPEN_WEEKLY_REPORT = "open_weekly_report"
    }
}
