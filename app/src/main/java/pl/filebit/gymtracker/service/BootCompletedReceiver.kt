package pl.filebit.gymtracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject

/**
 * v1.14.1 — luka K6 z audytu (2026-05-10): workery WorkManager są CANCELLED przez Android
 * po restarcie urządzenia. Bez tego receiver'a aplikacja po reboot tracila ProactiveAiCheck
 * (codzienny check zdrowia) i DietAutoAdjustment (cotygodniowy check kcal).
 *
 * Trigger: `Intent.ACTION_BOOT_COMPLETED` (manifest + RECEIVE_BOOT_COMPLETED permission).
 *
 * Logika delegowana do `WorkerRescheduler` — single source of truth, używana też
 * przez `GymTrackerApp.onCreate()`. Bez tego deduplikacja kodu.
 *
 * Bezpieczeństwo: receiver może być wywołany tylko z system broadcast (BOOT_COMPLETED).
 * Read-only operations w tle (Dispatchers.IO) — nie blokuje boot.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var workerRescheduler: WorkerRescheduler
    @Inject lateinit var profileRepo: UserProfileRepository
    @Inject lateinit var dietPrefs: DietPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        // Reschedule w tle — load profile + dietConfig wymagają DB query.
        // Pending result żeby Android nie zakończył receivera przed launch coroutine.
        val pending = goAsync()
        scope.launch {
            try {
                val profile = profileRepo.get()
                val dietConfig = dietPrefs.load()
                workerRescheduler.rescheduleAll(profile, dietConfig)
            } finally {
                pending.finish()
            }
        }
    }
}
