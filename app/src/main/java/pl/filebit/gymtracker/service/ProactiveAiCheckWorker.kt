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
    private val aiLogRepository: pl.filebit.gymtracker.data.repository.AiLogRepository,
    // v2.5.0 — sprawdzanie przeciwwskazań canonical przy regule bólu
    private val exerciseDao: pl.filebit.gymtracker.data.db.dao.ExerciseDao,
    // v2.12.0 — reguła opuszczonego zaplanowanego treningu
    private val deloadService: pl.filebit.gymtracker.data.repository.DeloadService,
    // v2.14.0 — kanoniczne alerty trenera (te same co na Home) w tle
    private val homeAlertNotifier: HomeAlertNotifier,
    private val diag: pl.filebit.gymtracker.data.repository.DiagnosticLogger
) : CoroutineWorker(appContext, params) {

    private val diagCat = pl.filebit.gymtracker.data.entity.DiagnosticCategory.DETECTOR
    private val diagSrc = "ProactiveAiCheckWorker"

    override suspend fun doWork(): Result {
        val profile = profileRepo.get()
        if (!profile.aiProactiveChecksEnabled) return Result.success()

        // v1.18.0 — auto-cleanup starszych niż 30 dni logów AI (chroni rozmiar DB).
        runCatching { aiLogRepository.deleteOlderThanDays(30) }

        // v2.14.0 — kanoniczne alerty trenera (DeloadService.cardState: ból/powrót/
        // opuszczony trening/deload) w tle. HomeAlertNotifier dedupuje po wspólnym hashu,
        // więc to NIE zdubluje notyfikacji z renderu Home. Leci PIERWSZE; jeśli jest
        // realny alert — wysyłamy i kończymy (reguły ad-hoc poniżej to fallback).
        val card = runCatching { deloadService.cardState() }.getOrNull()
        if (card != null &&
            card !is pl.filebit.gymtracker.data.repository.DeloadCardState.None &&
            card !is pl.filebit.gymtracker.data.repository.DeloadCardState.Active
        ) {
            diag.info(diagCat, diagSrc, "alert_fired", "Alert trenera w tle: ${card::class.simpleName}", success = true)
            runCatching { homeAlertNotifier.maybeNotify(card) }
            return Result.success()
        }

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

        // 3. Opuszczony zaplanowany trening (v2.12.0)
        val missed = runCatching { deloadService.missedWorkoutSignal() }.getOrNull()

        // 4. Partia >7 dni bez treningu
        val recovery = statsRepo.recoveryByMuscleFast(statsCacheService.snapshot())
        val staleMuscle = recovery
            .filter { it.daysAgo >= 7 }
            .maxByOrNull { it.daysAgo }

        val (title, body, prompt) = when {
            recurringPain != null -> {
                // v2.5.0: sprawdź canonical contraindications — które ćwiczenia mogą
                // obciążać okolicę bólu. Konkretne ostrzeżenie zamiast ogólnego.
                val risky = riskyExercisesForPain(recurringPain)
                val extra = if (risky.isNotEmpty()) {
                    " Uwaga na: ${risky.joinToString(", ")} (mogą obciążać tę okolicę)."
                } else ""
                Triple(
                    "⚠ Ostrzeżenie od AI",
                    "Zgłaszałeś ból ($recurringPain) w ≥2 z ostatnich 3 treningów.$extra Sprawdźmy co zmienić.",
                    "PAIN_RECOVERY"
                )
            }
            stagnation != null -> Triple(
                "📊 Stagnacja w ${stagnation.exerciseName}",
                "Już ${stagnation.workoutsAtSameWeight} treningów na tej samej wadze. Czas na deload?",
                "DELOAD"
            )
            missed != null -> Triple(
                if (missed.severity == pl.filebit.gymtracker.util.MissedWorkoutSeverity.FIRM)
                    "🏋 Wracamy do rytmu" else "🏋 Przegapiony trening",
                missed.reason.take(180),
                "TODAY"
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
                    diag.info(diagCat, diagSrc, "no_signal", "Codzienny check: brak sygnału (cisza)")
                    return Result.success()  // brak sygnału — milczymy
                }
            }
        }

        diag.info(diagCat, diagSrc, "signal_fired", "Codzienny check — sygnał: $prompt", success = true)
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
        diag.info(pl.filebit.gymtracker.data.entity.DiagnosticCategory.NOTIFICATION, diagSrc,
            "notification_sent", "Wysłano notyfikację trenera w tle: $title", success = true)
    }

    /**
     * v2.5.0 — znajduje ulubione ćwiczenia usera których canonical contraindications
     * pasują do zgłaszanej okolicy bólu (painArea). Mapuje polski painArea na angielskie
     * tokeny przeciwwskazań. Zwraca max 3 nazwy. Pusta lista = brak dopasowania.
     */
    private suspend fun riskyExercisesForPain(painArea: String): List<String> {
        val pa = painArea.lowercase()
        // mapowanie potocznego painArea (PL/EN) na tokeny w contraindicationsJson
        val tokens = buildList {
            if (pa.contains("plec") || pa.contains("krzyż") || pa.contains("lędźw") || pa.contains("back") || pa.contains("lumbar")) {
                add("lower_back"); add("lumbar"); add("back_pain"); add("disc")
            }
            if (pa.contains("bark") || pa.contains("ramię") || pa.contains("ramie") || pa.contains("shoulder")) {
                add("shoulder"); add("rotator")
            }
            if (pa.contains("kolan") || pa.contains("knee")) { add("knee"); add("patell") }
            if (pa.contains("łok") || pa.contains("lok") || pa.contains("elbow")) { add("elbow"); add("epicond") }
            if (pa.contains("nadgarst") || pa.contains("wrist")) { add("wrist") }
            if (pa.contains("biodr") || pa.contains("hip")) { add("hip") }
            if (pa.contains("szyj") || pa.contains("kark") || pa.contains("neck") || pa.contains("cervical")) {
                add("neck"); add("cervical")
            }
        }
        if (tokens.isEmpty()) return emptyList()
        return runCatching {
            exerciseDao.getFavorites()
                .filter { ex ->
                    val c = ex.contraindicationsJson?.lowercase() ?: return@filter false
                    tokens.any { c.contains(it) }
                }
                .map { it.name }
                .take(3)
        }.getOrDefault(emptyList())
    }

    companion object {
        const val CHANNEL_ID = "proactive_ai_check_channel"
        const val NOTIFICATION_ID = 5252
        const val UNIQUE_WORK_NAME = "proactive_ai_check"
        const val EXTRA_OPEN_AI_TRAINER = "open_ai_trainer"
        const val EXTRA_QUICK_ACTION = "ai_quick_action"
    }
}
