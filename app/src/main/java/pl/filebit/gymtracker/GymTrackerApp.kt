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
class GymTrackerApp : Application(), Configuration.Provider, coil3.SingletonImageLoader.Factory {

    /**
     * v1.25.0 — Coil 3 ImageLoader z GIF decoderem + cache na dysku.
     * Wymagane dla GIFów ExerciseDB z CDN static.exercisedb.dev.
     */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader {
        return coil3.ImageLoader.Builder(context)
            .components {
                add(coil3.gif.GifDecoder.Factory())
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory())
            }
            .build()
    }

    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var foodProductSeeder: pl.filebit.gymtracker.data.seed.FoodProductSeeder
    @Inject lateinit var recipeSeeder: pl.filebit.gymtracker.data.seed.RecipeSeeder
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workoutRepo: WorkoutRepository
    @Inject lateinit var profileRepo: UserProfileRepository
    @Inject lateinit var unfinishedScheduler: UnfinishedWorkoutScheduler
    @Inject lateinit var dietPrefs: pl.filebit.gymtracker.data.repository.DietPreferences
    @Inject lateinit var eventBackfillService: pl.filebit.gymtracker.ai.EventBackfillService
    @Inject lateinit var periodRollupService: pl.filebit.gymtracker.ai.PeriodRollupService
    @Inject lateinit var mesocycleBackfillService: pl.filebit.gymtracker.data.repository.MesocycleBackfillService
    @Inject lateinit var periodizationOrchestrator: pl.filebit.gymtracker.data.repository.PeriodizationOrchestrator
    // v1.14.1: zunifikowane reschedule (single source of truth, używane też przez BootCompletedReceiver)
    @Inject lateinit var workerRescheduler: pl.filebit.gymtracker.service.WorkerRescheduler
    // v1.20.3: naprawa wag po cumulative deload bug (np. 41.99 zamiast 42.5)
    @Inject lateinit var deloadService: pl.filebit.gymtracker.data.repository.DeloadService
    // v2.0.0: canonical exercise-db — 1317 ćwiczeń własnych z GIFami z R2 CDN
    @Inject lateinit var canonicalExerciseBootstrap: pl.filebit.gymtracker.data.repository.CanonicalExerciseBootstrap
    // v2.20.0: log diagnostyczny — cleanup starych wpisów przy starcie
    @Inject lateinit var diagnosticLogger: pl.filebit.gymtracker.data.repository.DiagnosticLogger

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        installCrashLogger()
        super.onCreate()
        createNotificationChannels()
        runCatching { diagnosticLogger.cleanup(30) }  // v2.20.0: sprzątanie logów >30 dni
        appScope.launch {
            // v2.0.0: canonical bootstrap MUSI iść PRZED exerciseSeeder
            // (seeder.markMacjiejFavorites używa slugów które dopiero canonical wstawia).
            android.util.Log.i("GymTrackerApp", "before canonicalBootstrap")
            runCatching { canonicalExerciseBootstrap.bootstrap() }
                .onFailure { android.util.Log.e("GymTrackerApp", "canonicalBootstrap failed", it) }
            android.util.Log.i("GymTrackerApp", "before exerciseSeeder.seedIfEmpty")
            runCatching { exerciseSeeder.seedIfEmpty() }
                .onFailure { android.util.Log.e("GymTrackerApp", "exerciseSeeder failed", it) }
            android.util.Log.i("GymTrackerApp", "before foodProductSeeder")
            runCatching { foodProductSeeder.seedIfEmpty() }
                .onFailure { android.util.Log.e("GymTrackerApp", "foodProductSeeder failed", it) }
            runCatching { recipeSeeder.seedIfEmpty() }
                .onFailure { android.util.Log.e("GymTrackerApp", "recipeSeeder failed", it) }
            // v1.14.1: zunifikowane reschedule (WorkerRescheduler — single source of truth,
            // używane też przez BootCompletedReceiver). Wcześniej logika była zduplikowana.
            workerRescheduler.rescheduleAll(profileRepo.get(), dietPrefs.load())
            // v1.11.60: backfill historycznych eventow (PR/INJURY/GAP) z istniejacych
            // treningow przy pierwszym starcie po update do v1.11.59+. Idempotentny -
            // jesli juz sa eventy nic nie robi.
            runCatching { eventBackfillService.backfillIfNeeded() }
            // v1.11.66: zamknij wszystkie zalegle okresy (week/month/quarter).
            // Idempotentny - rollup raz utworzony nie jest re-computowany.
            runCatching { periodRollupService.closeAllPeriodsIfNeeded() }
            // v1.13.0: jednorazowa rekonstrukcja historii mezo-cykli z TrainingEvent
            // DELOAD_DETECTED + workoutow ostatnich 12 tyg. Idempotentny.
            runCatching { mesocycleBackfillService.backfillFromHistory() }
            // v1.14.0: utrzymaj aktywny mezo-cykl. Jesli brak — tworzy z computed phase.
            // Jesli plannedEnd minal — zwraca TransitionDue (UI pokaze propozycje).
            runCatching { periodizationOrchestrator.pulse() }
            // v1.20.3: napraw wagi planu po cumulative deload bug (np. 41.99 zamiast 42.5).
            // Idempotentny — działa tylko gdy aktywny deload + wykryje korupcję.
            runCatching { deloadService.repairWeightsIfCorrupted() }
            // v2.0.0: canonical exercise-db — import 1317 ćwiczeń + reimport historii.
            // Idempotentny przez `slug` (UNIQUE). Nowe ćwiczenia dodaje automatycznie.
            runCatching { canonicalExerciseBootstrap.bootstrap() }
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
            // v1.24.0 — natychmiastowe alerty Home (ból, powrót po przerwie, deload)
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.HomeAlertNotifier.CHANNEL_ID,
                    "Alerty trenera",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Ostrzeżenia w czasie rzeczywistym: wykryty ból, powrót po przerwie, sugestia deload."
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
            // v2.13.0 — auto-raport tygodniowy (poniedziałek rano)
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.WeeklyReportWorker.CHANNEL_ID,
                    "Raport tygodniowy",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Cotygodniowe podsumowanie treningu z rekomendacjami (poniedziałek rano)."
                    setShowBadge(true)
                }
            )
            // v2.31.0 — Bilans dnia (wieczorny check braków: posiłki/trening)
            nm.createNotificationChannel(
                NotificationChannel(
                    pl.filebit.gymtracker.service.DailyReviewWorker.CHANNEL_ID,
                    "Bilans dnia",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Wieczorne podsumowanie: nieoznaczone posiłki, niezrobiony zaplanowany trening."
                    setShowBadge(true)
                }
            )
        }
    }
}
