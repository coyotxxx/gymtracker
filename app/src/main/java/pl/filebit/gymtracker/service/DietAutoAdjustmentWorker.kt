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
import pl.filebit.gymtracker.data.repository.AutoAdjustmentService
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.util.AdjustmentAction

/**
 * Co ~14 dni: silnik analizuje trend wagi + adherence + objętość treningu.
 * Jeśli wynik != HOLD/NEEDS_MORE_DATA → zapisuje DietAdjustment (savePreview)
 * i wysyła notyfikację z deep linkiem do DietScreen, gdzie user akceptuje
 * lub odrzuca propozycję.
 *
 * Ważne: worker NIE stosuje zmian sam — tylko proponuje. SafetyGuard +
 * świadoma decyzja usera są obowiązkowe (filozofia "AI tłumaczy, user decyduje").
 */
@HiltWorker
class DietAutoAdjustmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoAdjust: AutoAdjustmentService,
    private val dietPrefs: DietPreferences,
    // v2.15.0 (P1-3) — monit ważenia gdy dietetyk jest ślepy (brak pomiarów)
    private val bodyDao: pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao,
    private val diag: pl.filebit.gymtracker.data.repository.DiagnosticLogger
) : CoroutineWorker(appContext, params) {

    private val cat = pl.filebit.gymtracker.data.entity.DiagnosticCategory.DIET
    private val src = "DietAutoAdjustmentWorker"

    override suspend fun doWork(): Result {
        val config = dietPrefs.load()
        if (!config.autoCheckAdjustments) return Result.success()

        // v2.15.0 (P1-3): bez ważenia silnik jest ślepy (NEEDS_MORE_DATA → cisza).
        // Jeśli ostatni pomiar ≥7 dni temu (lub nigdy) — przypomnij. Worker leci co 7 dni,
        // więc max 1 monit/tydz (bez spamu).
        val latestWeighIn = runCatching { bodyDao.getLatest() }.getOrNull()
        val daysSinceWeighIn = latestWeighIn?.let {
            (System.currentTimeMillis() - it.date) / (24L * 3600 * 1000)
        }
        if (latestWeighIn == null || (daysSinceWeighIn ?: Long.MAX_VALUE) >= 7) {
            diag.info(cat, src, "weigh_in_nudge", "Wysłano monit ważenia (ostatni pomiar: ${daysSinceWeighIn ?: "nigdy"} dni temu)")
            notifyWeighIn(applicationContext)
        }

        val decision = runCatching { autoAdjust.analyzeNow() }.getOrNull()
            ?: return Result.success()

        // Milczymy gdy nie ma sygnału
        if (decision.action == AdjustmentAction.HOLD ||
            decision.action == AdjustmentAction.NEEDS_MORE_DATA
        ) {
            diag.info(cat, src, "adjustment_hold", "Brak korekty kcal: ${decision.action.name} (${decision.reason})")
            return Result.success()
        }
        diag.info(cat, src, "adjustment_proposed", "Zaproponowano korektę: ${decision.action.name} → ${decision.newKcal} kcal", success = true)

        // Zapisz preview do bazy (z AI explanation jeśli klucz dostępny)
        val adjId = runCatching { autoAdjust.savePreview(decision) }.getOrNull()
            ?: return Result.success()

        val (title, body) = when (decision.action) {
            AdjustmentAction.DECREASE_KCAL -> "↓ Sugestia: obniż kcal" to
                "Trend wagi sugeruje korektę. ${decision.kcalDeltaProposed} kcal → ${decision.newKcal}/dzień. Otwórz dietę by zobaczyć szczegóły."
            AdjustmentAction.INCREASE_KCAL -> "↑ Sugestia: dodaj kcal" to
                "Trend wagi sugeruje korektę. +${decision.kcalDeltaProposed} kcal → ${decision.newKcal}/dzień. Otwórz dietę by zobaczyć szczegóły."
            AdjustmentAction.DELOAD -> "🔄 Czas na deload" to
                "Adherence + objętość spadają. Sprawdź propozycję deloadu w diecie."
            AdjustmentAction.SIMPLIFY_PLAN -> "🛠 Uprość plan" to
                "Niska adherence — silnik sugeruje uproszczenie planu zamiast zmiany kcal."
            AdjustmentAction.REFEED_DAY -> "🍝 Refeed day" to
                "Wysoki głód + waga stoi → +${decision.kcalDeltaProposed} kcal jutro (głównie węgle), potem powrót do bazy."
            else -> return Result.success()
        }
        notify(ctx = applicationContext, title = title, body = body, adjustmentId = adjId)
        return Result.success()
    }

    private fun notify(ctx: Context, title: String, body: String, adjustmentId: Long) {
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DIET, true)
            putExtra(EXTRA_ADJUSTMENT_ID, adjustmentId)
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /** v2.15.0 (P1-3) — przypomnienie o ważeniu (osobne ID, ten sam kanał). */
    private fun notifyWeighIn(ctx: Context) {
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DIET, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID_WEIGH_IN, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val body = "Nie widzę pomiaru wagi od tygodnia. Bez ważenia nie ocenię, czy redukcja działa — " +
            "zważ się rano (po toalecie, przed jedzeniem) i loguj co 2-3 dni."
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚖ Czas się zważyć")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_WEIGH_IN, notification)
    }

    companion object {
        const val CHANNEL_ID = "diet_adjustments"
        const val NOTIFICATION_ID = 5601
        const val NOTIFICATION_ID_WEIGH_IN = 5602
        const val UNIQUE_WORK_NAME = "diet_auto_adjustment"
        const val EXTRA_OPEN_DIET = "open_diet"
        const val EXTRA_ADJUSTMENT_ID = "adjustment_id"
    }
}
