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
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.MealConsumptionStatus
import pl.filebit.gymtracker.data.repository.DiagnosticLogger
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.MealConsumptionRepository
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import java.util.Calendar

/**
 * v2.31.0 — „Bilans dnia". Reakcja apki na BRAK akcji (nie tylko na to co user zaznaczył).
 *
 * Wieczorny check (domyślnie 21:00): porównuje CO BYŁO ZAPLANOWANE z CO SIĘ STAŁO i — jeśli
 * są luki — wysyła JEDNĄ notyfikację podsumowującą + zapisuje zdarzenie diagnostyczne.
 *
 * Filozofia (uwaga Macieja 2026-06-13): najczęstszy realny przypadek to bezczynność, nie
 * świadome „pominięte". Trener/dietetyk reaguje na brak: nieoznaczone posiłki, niezrobiony
 * zaplanowany trening. Rzeczy PILNE (ból, twardo opuszczony trening) zostają natychmiastowe
 * przez ProactiveAiCheckWorker — tu zbieramy resztę w jeden spokojny bilans.
 */
@HiltWorker
class DailyReviewWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profileRepo: UserProfileRepository,
    private val dietPrefs: DietPreferences,
    private val consumptionRepo: MealConsumptionRepository,
    private val trainingDietBridge: TrainingDietBridge,
    private val workoutDao: pl.filebit.gymtracker.data.db.dao.WorkoutDao,
    // v2.33.0 (U4a): zunifikowany werdykt coacha (trening+dieta) w jednym punkcie.
    private val coachOrchestrator: pl.filebit.gymtracker.data.coach.CoachOrchestrator,
    private val diag: DiagnosticLogger
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Pod tym samym przełącznikiem co inne proaktywne checki.
        val profile = runCatching { profileRepo.get() }.getOrNull() ?: return Result.success()
        if (!profile.aiProactiveChecksEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = todayBounds(now)

        // === DIETA: ile posiłków user OZNACZYŁ (zjadł/pominął) vs plan ===
        val mealsPerDay = runCatching { dietPrefs.load().mealsPerDay }.getOrDefault(0)
        val markedMeals = runCatching {
            consumptionRepo.getForDate(dayStart).count {
                it.status == MealConsumptionStatus.CONSUMED || it.status == MealConsumptionStatus.SKIPPED
            }
        }.getOrDefault(0)
        val unmarkedMeals = (mealsPerDay - markedMeals).coerceAtLeast(0)

        // === TRENING: zaplanowany dziś a nie wykonany ===
        val trainingPlanned = runCatching { trainingDietBridge.isPlannedTrainingDay(now) }.getOrDefault(false)
        val didWorkoutToday = runCatching {
            workoutDao.observeAllOnce().any { it.startedAt in dayStart until dayEnd }
        }.getOrDefault(false)
        val trainingGap = trainingPlanned && !didWorkoutToday

        // === ZUNIFIKOWANY WERDYKT COACHA (U4a) — jeden głos: trening + dieta razem ===
        // Orchestrator spina istniejących doradców (deload/dieta) w JEDNĄ priorytetyzowaną
        // reakcję. Bilans = pełny obraz dnia: werdykt coacha NA GÓRZE + braki systematyczności.
        val verdict = runCatching { coachOrchestrator.evaluate() }.getOrNull()
        val coachLine = verdict?.primary?.let { "💪 ${it.title}: ${it.message}" }

        // Zbuduj listę braków (systematyczność).
        val parts = mutableListOf<String>()
        if (trainingGap) parts.add("🏋 Zaplanowany trening — jeszcze nie zrobiony.")
        if (mealsPerDay > 0 && unmarkedMeals > 0) {
            parts.add("🍽 $unmarkedMeals z $mealsPerDay posiłków nieoznaczonych — daj znać, czy jadłeś.")
        }

        if (parts.isEmpty() && coachLine == null) {
            // Czysty dzień, coach nie ma uwag — nie zawracamy głowy, zostawiamy ślad.
            diag.info(DiagnosticCategory.REPORT, "DailyReviewWorker", "daily_review_clean",
                "Bilans dnia: brak luk i brak werdyktu coacha (posiłki $markedMeals/$mealsPerDay, trening ${if (trainingPlanned) "zrobiony" else "niezaplanowany"})",
                success = true)
            return Result.success()
        }

        // Werdykt coacha na górze (jeśli jest), potem braki.
        val body = listOfNotNull(coachLine).plus(parts).joinToString("\n")
        sendNotification(applicationContext, body)
        diag.info(DiagnosticCategory.REPORT, "DailyReviewWorker", "daily_review_fired",
            "Bilans dnia — werdykt coacha: ${verdict?.primary?.id ?: "brak"}, braki: ${parts.size}",
            dataJson = """{"coachPrimary":${verdict?.primary?.let { "\"${it.id}\"" } ?: "null"},"trainingGap":$trainingGap,"unmarkedMeals":$unmarkedMeals,"mealsPerDay":$mealsPerDay,"markedMeals":$markedMeals}""",
            success = true)
        return Result.success()
    }

    private fun sendNotification(ctx: Context, body: String) {
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📋 Bilans dnia")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        diag.info(DiagnosticCategory.NOTIFICATION, "DailyReviewWorker", "notification_sent",
            "Wysłano notyfikację Bilans dnia", success = true)
    }

    private fun todayBounds(nowMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    companion object {
        const val CHANNEL_ID = "daily_review_channel"
        const val NOTIFICATION_ID = 5400
        const val UNIQUE_WORK_NAME = "daily_review_check"
    }
}
