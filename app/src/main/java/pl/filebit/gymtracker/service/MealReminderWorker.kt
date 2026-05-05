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
import pl.filebit.gymtracker.R

/**
 * Wysyła powiadomienie "Pora na posiłek!" o wybranej godzinie.
 * Worker jest planowany przez DietReminderScheduler — jeden Worker per slot per dzień.
 *
 * Input data:
 *   - "slot_index": Int (1..N)
 *   - "slot_label": String (np. "Śniadanie", "Obiad")
 */
@HiltWorker
class MealReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val slotIndex = inputData.getInt("slot_index", 1)
        val slotLabel = inputData.getString("slot_label") ?: "Posiłek $slotIndex"

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_BASE_ID + slotIndex, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🍽️ Pora na $slotLabel")
            .setContentText("Otwórz GymTracker → Dieta i odznacz zjedzony posiłek.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_BASE_ID + slotIndex, notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "meal_reminders"
        const val NOTIFICATION_BASE_ID = 5500   // +slotIndex
        const val WORK_NAME_PREFIX = "meal_reminder_slot_"
    }
}
