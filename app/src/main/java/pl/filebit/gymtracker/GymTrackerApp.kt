package pl.filebit.gymtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.service.RestTimerService
import pl.filebit.gymtracker.service.UnfinishedWorkoutScheduler
import pl.filebit.gymtracker.service.UnfinishedWorkoutWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var foodProductSeeder: pl.filebit.gymtracker.data.seed.FoodProductSeeder
    @Inject lateinit var recipeSeeder: pl.filebit.gymtracker.data.seed.RecipeSeeder
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workoutRepo: WorkoutRepository
    @Inject lateinit var profileRepo: UserProfileRepository
    @Inject lateinit var unfinishedScheduler: UnfinishedWorkoutScheduler
    @Inject lateinit var proactiveAiScheduler: pl.filebit.gymtracker.service.ProactiveAiCheckScheduler
    @Inject lateinit var dietAdjustmentScheduler: pl.filebit.gymtracker.service.DietAutoAdjustmentScheduler
    @Inject lateinit var dietPrefs: pl.filebit.gymtracker.data.repository.DietPreferences
    @Inject lateinit var eventBackfillService: pl.filebit.gymtracker.ai.EventBackfillService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        installCrashLogger()
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            exerciseSeeder.seedIfEmpty()
            foodProductSeeder.seedIfEmpty()
            recipeSeeder.seedIfEmpty()
            // Reschedule proactive AI check przy starcie aplikacji (np. po update)
            val profile = profileRepo.get()
            if (profile.aiProactiveChecksEnabled) {
                proactiveAiScheduler.schedulePeriodic()
            }
            if (dietPrefs.load().autoCheckAdjustments) {
                dietAdjustmentScheduler.schedulePeriodic()
            } else {
                dietAdjustmentScheduler.cancel()
            }
            // v1.11.60: backfill historycznych eventow (PR/INJURY/GAP) z istniejacych
            // treningow przy pierwszym starcie po update do v1.11.59+. Idempotentny -
            // jesli juz sa eventy nic nie robi.
            runCatching { eventBackfillService.backfillIfNeeded() }
        }
        observeActiveWorkoutForReminder()
    }

    private fun observeActiveWorkoutForReminder() {
        combine(
            workoutRepo.observeActive(),
            profileRepo.observe()
        ) { workout, profile ->
            Triple(
                workout?.id,
                profile.unfinishedWorkoutNotifyEnabled,
                profile.unfinishedWorkoutNotifyHours
            )
        }
            .distinctUntilChanged()
            .onEach { (id, enabled, hours) ->
                when {
                    id == null -> unfinishedScheduler.cancel()
                    enabled -> unfinishedScheduler.schedule(hours.coerceAtLeast(1))
                    else -> unfinishedScheduler.cancel()
                }
            }
            .launchIn(appScope)
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    append("Timestamp: ").append(ts).append('\n')
                    append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
                    append("Model: ").append(Build.MODEL).append('\n')
                    append("SDK: ").append(Build.VERSION.SDK_INT).append('\n')
                    runCatching {
                        val pi = packageManager.getPackageInfo(packageName, 0)
                        append("App version: ").append(pi.versionName).append('\n')
                    }
                    append("Thread: ").append(thread.name).append('\n')
                    append("---\n")
                    append(throwable.stackTraceToString())
                }
                File(filesDir, "last_crash.txt").writeText(report)
            } catch (_: Throwable) {
                // never mask the original crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    RestTimerService.CHANNEL_ID,
                    getString(R.string.notif_channel_timer),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_timer_desc)
                    enableVibration(true)
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    UnfinishedWorkoutWorker.CHANNEL_ID,
                    getString(R.string.notif_channel_unfinished),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_channel_unfinished_desc)
                    setShowBadge(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.ProactiveAiCheckWorker.CHANNEL_ID,
                    "Codzienny check AI",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Codzienne przypomnienia od AI o regeneracji, stagnacjach i bólu"
                    setShowBadge(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.MealReminderWorker.CHANNEL_ID,
                    "Pora posiłku",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Przypomnienia o porach posiłków w oknie żywieniowym."
                    setShowBadge(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.DietAutoAdjustmentWorker.CHANNEL_ID,
                    "Korekty planu żywieniowego",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "AI sprawdza co 14 dni czy plan kcal nadal pasuje do trendu wagi."
                    setShowBadge(true)
                }
            )
        }
    }
}
