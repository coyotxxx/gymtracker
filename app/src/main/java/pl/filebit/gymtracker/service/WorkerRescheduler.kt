package pl.filebit.gymtracker.service

import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.data.repository.DiagnosticLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth dla rescheduling workerów.
 *
 * Używane przez:
 *  - `GymTrackerApp.onCreate()` (każdy start aplikacji, np. po update do nowej wersji)
 *  - `BootCompletedReceiver.onReceive()` (po restart urządzenia — workery są CANCELLED przez Android)
 *
 * Refaktor v1.14.1: wcześniej logika była zduplikowana między onCreate a BootReceiver.
 * Teraz jedno miejsce — łatwiej dodać nowy worker do listy reschedule.
 *
 * Workery które są SAVE per user preference:
 *  - ProactiveAiCheckScheduler — jeśli `profile.aiProactiveChecksEnabled = true`
 *  - DietAutoAdjustmentScheduler — jeśli `dietConfig.autoCheckAdjustments = true`
 *
 * Workery z dynamicznym schedulingiem (NIE w tej funkcji):
 *  - UnfinishedWorkoutScheduler — odpalany tylko gdy aktywny workout (z observe w GymTrackerApp)
 *  - HealthConnectSyncScheduler — tylko gdy user explicit włącza w DietSettings
 *  - DietReminderScheduler — tylko gdy user zmienia porę posiłków
 *
 * Workery one-shot z user actions (nigdy reschedule):
 *  - MealReminderWorker — onCreate per slot
 *  - RestTimerService — foreground (nie WorkManager)
 */
@Singleton
class WorkerRescheduler @Inject constructor(
    private val proactiveAiScheduler: ProactiveAiCheckScheduler,
    private val dietAdjustmentScheduler: DietAutoAdjustmentScheduler,
    private val weeklyReportScheduler: WeeklyReportScheduler,
    private val dailyReviewScheduler: DailyReviewScheduler,
    // v2.26.0: nullable-default — Hilt wstrzykuje realny logger, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    /**
     * Reschedule wszystkie periodic workery zgodnie z user preferences.
     * Bezpieczne dla wielokrotnego wywołania — schedulers używają ExistingPeriodicWorkPolicy.KEEP.
     */
    fun rescheduleAll(profile: UserProfile, dietConfig: DietConfig) {
        if (profile.aiProactiveChecksEnabled) {
            proactiveAiScheduler.schedulePeriodic()
        } else {
            proactiveAiScheduler.cancel()
        }
        if (dietConfig.autoCheckAdjustments) {
            dietAdjustmentScheduler.schedulePeriodic()
        } else {
            dietAdjustmentScheduler.cancel()
        }
        // v2.13.0 — auto-raport tygodniowy (domyślnie ON)
        if (profile.aiAutoGenerateWeeklyReports) {
            weeklyReportScheduler.schedulePeriodic()
        } else {
            weeklyReportScheduler.cancel()
        }
        // v2.31.0 — Bilans dnia (pod tym samym przełącznikiem co proaktywne checki)
        if (profile.aiProactiveChecksEnabled) {
            dailyReviewScheduler.schedulePeriodic()
        } else {
            dailyReviewScheduler.cancel()
        }
        diag?.info(DiagnosticCategory.WORKER, "WorkerRescheduler", "reschedule_all",
            "Przeplanowano workery (proactiveAI=${profile.aiProactiveChecksEnabled}, " +
                "dietAdjust=${dietConfig.autoCheckAdjustments}, weeklyReport=${profile.aiAutoGenerateWeeklyReports})",
            dataJson = """{"proactiveAi":${profile.aiProactiveChecksEnabled},"dietAdjust":${dietConfig.autoCheckAdjustments},"weeklyReport":${profile.aiAutoGenerateWeeklyReports}}""",
            success = true)
    }
}
