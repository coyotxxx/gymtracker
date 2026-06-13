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
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.DiagnosticLogger
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
    @Inject lateinit var diag: DiagnosticLogger

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

        // Cancel notyfikację (synchronicznie — i tak natychmiastowe)
        if (notificationId > 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }

        // v2.23.0 (K4 fix): goAsync() trzyma proces żywy do końca zapisu. Bez tego system
        // mógł ubić proces po onReceive PRZED zapisem statusu/adherence → utrata kliku.
        val pending = goAsync()
        scope.launch {
            try {
                diag.info(DiagnosticCategory.USER_ACTION, "MealStatusReceiver", "meal_action_from_notification",
                    "Akcja z notyfikacji: ${mealType.name} → ${status.name}",
                    dataJson = """{"mealType":"${mealType.name}","status":"${status.name}"}""", success = true)
                consumptionRepo.setStatus(dateMs, mealType, status)
                runCatching { adherenceCalc.computeForDate(dateMs) }
            } catch (t: Throwable) {
                diag.error(DiagnosticCategory.ERROR, "MealStatusReceiver", "meal_action_failed",
                    "Błąd zapisu statusu posiłku z notyfikacji", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "pl.filebit.gymtracker.MEAL_STATUS"
        const val EXTRA_MEAL_TYPE = "meal_type"
        const val EXTRA_STATUS = "status"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
