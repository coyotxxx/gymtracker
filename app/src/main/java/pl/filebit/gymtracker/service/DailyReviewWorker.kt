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
    // v2.41.0 (U5): AI-nadzór werdyktu RAZ DZIENNIE (tanio) — Tier B poleruje/łapie błędy Tier A.
    private val aiClient: pl.filebit.gymtracker.ai.AiClient,
    private val aiPrefs: pl.filebit.gymtracker.ai.AiPreferences,
    private val masterContextBuilder: pl.filebit.gymtracker.ai.MasterAiContextBuilder,
    private val diag: DiagnosticLogger,
    private val notifHistory: pl.filebit.gymtracker.data.repository.NotificationHistoryStore
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

        // === U5: AI-NADZÓR (raz dziennie, tanio) — Tier B sprawdza werdykt Tier A z pełnym
        // kontekstem. Łapie błędy/niuanse algorytmu (jak slope-bug). Bez klucza → pomijamy.
        val aiNote = verdict?.primary?.let { p -> reviewWithAi(p) }

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

        // Werdykt coacha na górze, komentarz AI pod spodem (jeśli jest), potem braki.
        val body = listOfNotNull(coachLine, aiNote?.let { "🤖 Trener AI: $it" }).plus(parts).joinToString("\n")
        sendNotification(applicationContext, body)
        diag.info(DiagnosticCategory.REPORT, "DailyReviewWorker", "daily_review_fired",
            "Bilans dnia — werdykt coacha: ${verdict?.primary?.id ?: "brak"}, braki: ${parts.size}",
            dataJson = """{"coachPrimary":${verdict?.primary?.let { "\"${it.id}\"" } ?: "null"},"trainingGap":$trainingGap,"unmarkedMeals":$unmarkedMeals,"mealsPerDay":$mealsPerDay,"markedMeals":$markedMeals}""",
            success = true)
        return Result.success()
    }

    /**
     * U5: AI recenzuje werdykt algorytmu z pełnym kontekstem. Zwraca krótki komentarz/korektę
     * lub null (brak klucza / błąd / AI nic nie dodaje). NIE zmienia danych — tylko ocenia.
     */
    private suspend fun reviewWithAi(primary: pl.filebit.gymtracker.data.coach.CoachReaction): String? {
        val cfg = runCatching { aiPrefs.load() }.getOrNull() ?: return null
        if (!cfg.isConnected) return null
        val ctx = runCatching { masterContextBuilder.build() }.getOrNull() ?: return null
        val h = pl.filebit.gymtracker.ai.MasterAiContextPromptHelper
        val contextStr = buildString {
            append(h.toDailyTargetsSection(ctx))
            append(h.toAdherenceSection(ctx))
            append(h.toRecoverySection(ctx))
            ctx.weightTrendSlopeKgPerWeek?.let { append("\n- Tempo wagi (regresja): %.2f kg/tydz\n".format(it)) }
            ctx.weightAvg7d?.let { append("- Średnia waga 7d: %.1f kg\n".format(it)) }
        }
        val prompt = buildString {
            append("Jesteś trenerem+dietetykiem nadzorującym algorytm tej aplikacji. ")
            append("Algorytm zdecydował dla usera:\n\"${primary.title} — ${primary.message}\"\n\n")
            append("Pełny kontekst usera:\n$contextStr\n")
            append("Oceń KRÓTKO (max 2 zdania): czy ten werdykt ma sens przy tych danych? ")
            append("Jeśli TAK — potwierdź jednym zdaniem. Jeśli widzisz BŁĄD lub ważny niuans ")
            append("(np. tempo/liczby się nie zgadzają) — powiedz wprost co skorygować. Po polsku.")
        }
        val resp = aiClient.chat(
            cfg,
            listOf(pl.filebit.gymtracker.ai.AiMessage(pl.filebit.gymtracker.ai.AiRole.USER, prompt)),
            source = "CoachAiOversight"
        ).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        diag.info(DiagnosticCategory.AI, "DailyReviewWorker", "coach_ai_review",
            "AI ocenił werdykt '${primary.id}': ${if (resp != null) "komentarz dodany" else "brak"}",
            dataJson = """{"reactionId":"${primary.id}","hasNote":${resp != null}}""", success = true)
        return resp
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
        // v2.47.0: to samo co w pushu → historia dzwonka.
        notifHistory.record(
            pl.filebit.gymtracker.data.entity.NotificationKind.REVIEW, "Bilans dnia", body
        )
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
