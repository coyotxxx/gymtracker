package pl.filebit.gymtracker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.MealConsumptionRepository
import javax.inject.Inject

/**
 * Odbiera akcje z notyfikacji MealReminder ("✓ Zjedzone" / "✗ Pominięte")
 * i zapisuje status do MealConsumption.
 */
@AndroidEntryPoint
class MealStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var consumptionRepo: MealConsumptionRepository
    @Inject lateinit var adherenceCalc: AdherenceCalculator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val mealTypeName = intent.getStringExtra(EXTRA_MEAL_TYPE) ?: return
        val statusName = intent.getStringExtra(EXTRA_STATUS) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val mealType = runCatching { MealType.valueOf(mealTypeName) }.getOrNull() ?: return
        val status = runCatching { MealConsumptionStatus.valueOf(statusName) }.getOrNull() ?: return

        // Today (start of day)
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dateMs = cal.timeInMillis

        scope.launch {
            consumptionRepo.setStatus(dateMs, mealType, status)
            // v2.11.0: akcja z notyfikacji ("Zjedzone/Pominięte") też przelicza adherence.
            runCatching { adherenceCalc.computeForDate(dateMs) }
        }

        // Cancel notyfikację
        if (notificationId > 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    companion object {
        const val ACTION = "pl.filebit.gymtracker.MEAL_STATUS"
        const val EXTRA_MEAL_TYPE = "meal_type"
        const val EXTRA_STATUS = "status"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
