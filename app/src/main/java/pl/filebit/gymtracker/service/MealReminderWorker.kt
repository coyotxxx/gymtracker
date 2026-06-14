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
    @Assisted params: WorkerParameters,
    private val notifHistory: pl.filebit.gymtracker.data.repository.NotificationHistoryStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val slotIndex = inputData.getInt("slot_index", 1)
        val slotLabel = inputData.getString("slot_label") ?: "Posiłek $slotIndex"
        val mealTypeName = inputData.getString("meal_type") ?: "SNACK"
        val notificationId = NOTIFICATION_BASE_ID + slotIndex

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Akcja "Zjedzone"
        val consumedIntent = Intent(ctx, MealStatusReceiver::class.java).apply {
            action = MealStatusReceiver.ACTION
            putExtra(MealStatusReceiver.EXTRA_MEAL_TYPE, mealTypeName)
            putExtra(MealStatusReceiver.EXTRA_STATUS, "CONSUMED")
            putExtra(MealStatusReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val consumedPi = PendingIntent.getBroadcast(
            ctx, notificationId * 10 + 1, consumedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Akcja "Pominięte"
        val skippedIntent = Intent(ctx, MealStatusReceiver::class.java).apply {
            action = MealStatusReceiver.ACTION
            putExtra(MealStatusReceiver.EXTRA_MEAL_TYPE, mealTypeName)
            putExtra(MealStatusReceiver.EXTRA_STATUS, "SKIPPED")
            putExtra(MealStatusReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val skippedPi = PendingIntent.getBroadcast(
            ctx, notificationId * 10 + 2, skippedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🍽️ Pora na $slotLabel")
            .setContentText("Oznacz status — Zjedzone lub Pominięte.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .addAction(android.R.drawable.checkbox_on_background, "✓ Zjedzone", consumedPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "✗ Pominięte", skippedPi)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
        // v2.47.0: to samo co w pushu → historia dzwonka. payload=typ posiłku → przyciski
        // Zjedzone/Pominięte działają wprost z dzwonka (jak w powiadomieniu systemowym).
        notifHistory.record(
            pl.filebit.gymtracker.data.entity.NotificationKind.MEAL,
            "🍽️ Pora na $slotLabel",
            "Oznacz status — Zjedzone lub Pominięte.",
            payload = mealTypeName
        )
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "meal_reminders"
        const val NOTIFICATION_BASE_ID = 5500   // +slotIndex
        const val WORK_NAME_PREFIX = "meal_reminder_slot_"
    }
}
