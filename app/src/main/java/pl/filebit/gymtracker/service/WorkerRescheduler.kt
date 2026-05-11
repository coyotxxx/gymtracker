package pl.filebit.gymtracker.service

import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.DietConfig
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
    private val dietAdjustmentScheduler: DietAutoAdjustmentScheduler
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
    }
}
