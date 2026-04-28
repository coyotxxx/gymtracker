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
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            exerciseSeeder.seedIfEmpty()
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
