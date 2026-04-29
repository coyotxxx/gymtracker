package pl.filebit.gymtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.service.RestTimerService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        installCrashLogger()
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            exerciseSeeder.seedIfEmpty()
        }
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
            val channel = NotificationChannel(
                RestTimerService.CHANNEL_ID,
                getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_timer_desc)
                enableVibration(true)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
