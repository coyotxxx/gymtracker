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
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.db.dao.WorkoutDao

/**
 * Codzienny check AI — sprawdza recovery/stagnacje/ból z ostatnich treningów
 * i wysyła pojedynczą notyfikację z najsilniejszym sygnałem. Tap na notyfikację
 * otwiera AI Trener.
 *
 * Reguły wyboru sygnału (priorytet od najwyższego):
 * 1. Powtarzający się ból w ≥2 z 3 ostatnich treningów (PILNE)
 * 2. Stagnacja wykryta w ostatnim treningu (WAŻNE)
 * 3. Partia mięśniowa nie trenowana ≥7 dni (PRZYPOMNIENIE)
 *
 * Brak sygnałów → bez notyfikacji.
 */
@HiltWorker
class ProactiveAiCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profileRepo: UserProfileRepository,
    private val statsRepo: StatsRepository,
    private val workoutDao: WorkoutDao,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService,
    // v1.15.0: 4. reguła — periodyzacja z AI
    private val periodizationOrchestrator: pl.filebit.gymtracker.data.repository.PeriodizationOrchestrator,
    private val pendingDecisionDao: pl.filebit.gymtracker.data.db.dao.PendingPeriodizationDecisionDao,
    private val aiClient: pl.filebit.gymtracker.ai.AiClient,
    private val aiPrefs: pl.filebit.gymtracker.ai.AiPreferences,
    private val aiToolHandler: pl.filebit.gymtracker.ai.AiToolHandler,
    // v1.18.0 — auto-cleanup AiLog (>30 dni)
    private val aiLogRepository: pl.filebit.gymtracker.data.repository.AiLogRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profile = profileRepo.get()
        if (!profile.aiProactiveChecksEnabled) return Result.success()

        // v1.18.0 — auto-cleanup starszych niż 30 dni logów AI (chroni rozmiar DB).
        runCatching { aiLogRepository.deleteOlderThanDays(30) }

        // 1. Sprawdź ostatnie treningi pod kątem powtarzającego się bólu
        val recent = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedByDescending { it.startedAt }
            .take(3)
        val recurringPain = recent
            .mapNotNull { it.painArea }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .keys
            .firstOrNull()

        // 2. Stagnacje z ostatniego treningu
        val lastWorkoutId = recent.firstOrNull()?.id
        val stagnation = if (lastWorkoutId != null) {
            statsRepo.detectStagnation(lastWorkoutId, threshold = 3).firstOrNull()
        } else null

        // 3. Partia >7 dni bez treningu
        val recovery = statsRepo.recoveryByMuscleFast(statsCacheService.snapshot())
        val staleMuscle = recovery
            .filter { it.daysAgo >= 7 }
            .maxByOrNull { it.daysAgo }

        val (title, body, prompt) = when {
            recurringPain != null -> Triple(
                "⚠ Ostrzeżenie od AI",
                "Zgłaszałeś ból ($recurringPain) w ≥2 z ostatnich 3 treningów. Sprawdźmy co zmienić.",
                "PAIN_RECOVERY"
            )
            stagnation != null -> Triple(
                "📊 Stagnacja w ${stagnation.exerciseName}",
                "Już ${stagnation.workoutsAtSameWeight} treningów na tej samej wadze. Czas na deload?",
                "DELOAD"
            )
            staleMuscle != null -> Triple(
                "💪 ${staleMuscle.muscle.displayName()} bez treningu ${staleMuscle.daysAgo} dni",
                "Czas wrócić — kliknij by AI zaproponowało dzisiejszą sesję.",
                "TODAY"
            )
            else -> {
                // v1.15.0 — 4. reguła: AI proaktywnie decyduje o cyklu periodyzacyjnym
                if (checkPeriodizationDecision()) {
                    // Jeśli AI zapisał PendingDecision — wyślij dedykowaną notyfikację
                    Triple(
                        "🤖 AI Trener ma propozycję",
                        "Faza cyklu wymaga decyzji. Otwórz Home → karta 'AI TRENER PROPONUJE'.",
                        "PERIODIZATION"
                    )
                } else {
                    return Result.success()  // brak sygnału — milczymy
                }
            }
        }

        notify(title, body, prompt)
        return Result.success()
    }

    /**
     * v1.15.0 — sprawdź czy `PeriodizationOrchestrator.pulse()` zwraca TransitionDue.
     * Jeśli TAK, pyta AI (chatWithTools) — AI używa toola `propose_periodization_action`
     * który zapisuje PendingPeriodizationDecision (status=PENDING). User zobaczy na Home.
     *
     * @return true jeśli zapisano nową propozycję (= wyślij notyfikację)
     */
    private suspend fun checkPeriodizationDecision(): Boolean {
        // Anti-spam: jeśli już są PENDING decyzje, nie pytaj AI znowu
        if (pendingDecisionDao.countPending() > 0) return false

        // Czy AI skonfigurowane?
        val aiConfig = aiPrefs.load()
        if (!aiConfig.isConnected) return false

        // Sprawdź state z orchestrator
        val state = runCatching { periodizationOrchestrator.pulse() }.getOrNull() ?: return false
        val transitionDue = state as? pl.filebit.gymtracker.data.repository.PeriodizationState.TransitionDue
            ?: return false

        // Pytaj AI o decyzję
        val promptSection = pl.filebit.gymtracker.ai.PeriodizationPromptHelper.toPromptSection(
            meso = transitionDue.current,
            algorithmProposal = transitionDue.proposal
        )
        val userMessage = buildString {
            append("Twoje zadanie: ocenić propozycję periodyzacyjną algorytmu i zaproponować konkretną decyzję dla mnie.\n")
            append(promptSection)
            append("\nObowiązkowo użyj toola `propose_periodization_action` z konkretnymi parametrami — bez tego decyzja nie zostanie zapisana w bazie i nie zobaczę jej na Home.")
        }

        val result = runCatching {
            aiClient.chatWithTools(
                config = aiConfig,
                messages = listOf(
                    pl.filebit.gymtracker.ai.AiMessage(
                        role = pl.filebit.gymtracker.ai.AiRole.USER,
                        content = userMessage
                    )
                ),
                toolHandler = aiToolHandler,
                source = "ProactiveAi_periodization"
            )
        }.getOrNull() ?: return false

        // AiToolHandler.execProposePeriodizationAction zapisał PendingDecision do bazy.
        // Sprawdzamy czy są nowe pending (worker mógł też nie zapisać jeśli AI nie użył toola).
        return result.isSuccess && pendingDecisionDao.countPending() > 0
    }

    private fun notify(title: String, body: String, deepLinkAction: String) {
        val ctx = applicationContext
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AI_TRAINER, true)
            putExtra(EXTRA_QUICK_ACTION, deepLinkAction)
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

    companion object {
        const val CHANNEL_ID = "proactive_ai_check_channel"
        const val NOTIFICATION_ID = 5252
        const val UNIQUE_WORK_NAME = "proactive_ai_check"
        const val EXTRA_OPEN_AI_TRAINER = "open_ai_trainer"
        const val EXTRA_QUICK_ACTION = "ai_quick_action"
    }
}
