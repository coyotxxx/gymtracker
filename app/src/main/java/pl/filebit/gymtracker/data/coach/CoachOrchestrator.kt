package pl.filebit.gymtracker.data.coach

import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.repository.AutoAdjustmentService
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.data.repository.DeloadService
import pl.filebit.gymtracker.data.repository.DiagnosticLogger
import pl.filebit.gymtracker.util.AdjustmentAction
import pl.filebit.gymtracker.util.AdjustmentDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.33.0 (U3) — CoachOrchestrator: JEDEN punkt decyzji.
 *
 * Spina istniejących doradców (Tier A, deterministyczni) w jedną, spójną, priorytetyzowaną
 * reakcję. NIE liczy sam — wywołuje silniki które już są i już częściowo arbitrażują w swojej
 * domenie (DeloadService.cardState = ból>powrót>opuszczony>deload; AutoAdjustmentService =
 * decyzja diety z uwzględnieniem regeneracji/NEAT/nawodnienia). Tu dokładamy arbitraż MIĘDZY
 * domenami: drabina priorytetów + anti-konflikt + dedup.
 *
 * MODUŁOWOŚĆ (spec §12): doradca bez danych zwraca None/NEEDS_MORE_DATA → brak reakcji. Trening
 * bez diety i odwrotnie działają same z siebie — orchestrator nie zakłada obecności żadnej domeny.
 *
 * Ten komponent jest DODATKOWY (U3). Podłączenie do UI/notyfikacji = U4 (jeden kanał).
 * AI-nadzór (polerowanie werdyktu) = U5.
 *
 * Patrz docs/COACH-DESIGN-SPEC.md.
 */
@Singleton
class CoachOrchestrator @Inject constructor(
    private val deloadService: DeloadService,
    private val autoAdjustmentService: AutoAdjustmentService,
    // v2.34.0 (U4b krok 1): NotificationCenter jako DORADCA — wnosi sygnały recovery/ACWR/faza/
    // brak treningu/brak wagi. Dzięki temu orchestrator jest nadzbiorem dzwonka in-app i można
    // go bezpiecznie usunąć (U4b krok 3) bez utraty funkcji.
    private val notificationCenter: pl.filebit.gymtracker.ai.NotificationCenter,
    private val diag: DiagnosticLogger? = null
) {

    /** Zbiera werdykty domenowe i zwraca JEDNĄ dominującą reakcję + resztę zwiniętą. */
    suspend fun evaluate(): CoachVerdict {
        val candidates = mutableListOf<CoachReaction>()

        // === TRENING (DeloadService.cardState — już zarbitrażowany wewnątrz domeny) ===
        runCatching { deloadService.cardState() }.getOrNull()?.let { card ->
            trainingReaction(card)?.let { candidates += it }
        }

        // === DIETA (AutoAdjustmentService.analyzeNow — uwzględnia regenerację/NEAT/nawodnienie) ===
        runCatching { autoAdjustmentService.analyzeNow() }.getOrNull()?.let { decision ->
            dietReaction(decision)?.let { candidates += it }
        }

        // === SYGNAŁY DZWONKA (recovery score/ACWR/faza/brak treningu/brak wagi) ===
        runCatching { notificationCenter.computeNotifications() }.getOrNull()?.forEach { n ->
            candidates += notificationReaction(n)
        }

        val verdict = arbitrateCoach(candidates)
        logVerdict(verdict, candidates.size)
        return verdict
    }

    private fun notificationReaction(n: pl.filebit.gymtracker.ai.AppNotification): CoachReaction {
        val crit = n.severity == pl.filebit.gymtracker.ai.NotificationSeverity.CRITICAL
        val (domain, priority, action) = when (n.actionType) {
            pl.filebit.gymtracker.ai.NotificationAction.APPLY_DELOAD ->
                Triple(CoachDomain.RECOVERY, CoachPriority.RECOVERY, CoachActionType.APPLY_DELOAD)
            pl.filebit.gymtracker.ai.NotificationAction.START_WORKOUT ->
                Triple(CoachDomain.CONSISTENCY, CoachPriority.CONSISTENCY, CoachActionType.START_WORKOUT)
            pl.filebit.gymtracker.ai.NotificationAction.AUDIT_PLAN ->
                Triple(CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.OPEN_TRAINING)
            pl.filebit.gymtracker.ai.NotificationAction.OPEN_PERIODIZATION_PLAN ->
                Triple(CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.OPEN_PERIODIZATION)
            pl.filebit.gymtracker.ai.NotificationAction.ADD_WEIGHT ->
                Triple(CoachDomain.CONSISTENCY, CoachPriority.CONSISTENCY, CoachActionType.OPEN_DIET)
            pl.filebit.gymtracker.ai.NotificationAction.SEND_HEALTH_SCREEN ->
                Triple(CoachDomain.CONSISTENCY, CoachPriority.CONSISTENCY, CoachActionType.NONE)
            pl.filebit.gymtracker.ai.NotificationAction.NONE ->
                Triple(if (crit) CoachDomain.RECOVERY else CoachDomain.GOAL,
                    if (crit) CoachPriority.RECOVERY else CoachPriority.OPTIMIZATION, CoachActionType.NONE)
        }
        val actions = buildList {
            if (action != CoachActionType.NONE) add(CoachAction(action, n.title.take(24)))
            add(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI"))
        }
        return CoachReaction(
            id = "nc_${n.id}", domain = domain, priority = priority,
            title = n.title, message = n.message, actions = actions, source = "NotificationCenter"
        )
    }

    // === MAPOWANIA doradca → CoachReaction ===
    private fun trainingReaction(card: DeloadCardState): CoachReaction? = when (card) {
        is DeloadCardState.ActiveInjury -> CoachReaction(
            id = "injury_${card.recommendation.painArea}",
            domain = CoachDomain.HEALTH, priority = CoachPriority.HEALTH,
            title = "Wykryto ból", message = card.recommendation.reason,
            actions = listOf(
                CoachAction(CoachActionType.REST_INJURY, "Odpuść tę partię"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "DeloadService.ActiveInjury"
        )
        is DeloadCardState.ReturnAfterBreak -> CoachReaction(
            id = "return_after_break",
            domain = CoachDomain.TRAINING, priority = CoachPriority.RETURN,
            title = "Powrót po przerwie", message = card.recommendation.reason,
            actions = listOf(
                CoachAction(CoachActionType.RETURN_LIGHT, "Wróć lżej"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "DeloadService.ReturnAfterBreak"
        )
        is DeloadCardState.MissedWorkout -> CoachReaction(
            id = "missed_workout",
            domain = CoachDomain.CONSISTENCY, priority = CoachPriority.CONSISTENCY,
            title = "Opuszczony trening", message = card.recommendation.reason,
            actions = listOf(
                CoachAction(CoachActionType.START_WORKOUT, "Zacznij trening"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "DeloadService.MissedWorkout"
        )
        is DeloadCardState.Suggestion -> {
            val r = card.recommendation
            if (r.recommendsDietBreak) CoachReaction(
                id = "refeed_suggestion",
                domain = CoachDomain.RECOVERY, priority = CoachPriority.RECOVERY,
                title = "Czas na refeed", message = r.reason,
                actions = listOf(
                    CoachAction(CoachActionType.APPLY_REFEED, "Zaplanuj refeed"),
                    CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
                ),
                source = "DeloadService.Suggestion(dietBreak)"
            ) else CoachReaction(
                id = "deload_suggestion",
                domain = CoachDomain.TRAINING, priority = CoachPriority.OPTIMIZATION,
                title = "Deload zalecany", message = r.reason,
                actions = listOf(
                    CoachAction(CoachActionType.APPLY_DELOAD, "Zastosuj deload"),
                    CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
                ),
                source = "DeloadService.Suggestion"
            )
        }
        // Active (deload w toku) i None — nie generują NOWEJ reakcji.
        is DeloadCardState.Active, DeloadCardState.None -> null
    }

    private fun dietReaction(d: AdjustmentDecision): CoachReaction? = when (d.action) {
        AdjustmentAction.DELOAD -> CoachReaction(
            id = "diet_deload",
            domain = CoachDomain.RECOVERY, priority = CoachPriority.RECOVERY,
            title = "Sygnał regeneracji", message = d.explanation,
            actions = listOf(
                CoachAction(CoachActionType.APPLY_DELOAD, "Lżejszy tydzień"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "AutoAdjustmentService.DELOAD"
        )
        AdjustmentAction.REFEED_DAY -> CoachReaction(
            id = "diet_refeed",
            domain = CoachDomain.RECOVERY, priority = CoachPriority.RECOVERY,
            title = "Dzień refeed", message = d.explanation,
            actions = listOf(
                CoachAction(CoachActionType.APPLY_REFEED, "Zastosuj refeed"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "AutoAdjustmentService.REFEED"
        )
        AdjustmentAction.SIMPLIFY_PLAN -> CoachReaction(
            id = "diet_simplify",
            domain = CoachDomain.CONSISTENCY, priority = CoachPriority.CONSISTENCY,
            title = "Uprość plan diety", message = d.explanation,
            actions = listOf(
                CoachAction(CoachActionType.SIMPLIFY_PLAN, "Uprość"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "AutoAdjustmentService.SIMPLIFY"
        )
        AdjustmentAction.DECREASE_KCAL, AdjustmentAction.INCREASE_KCAL -> CoachReaction(
            id = "diet_kcal_adjust",
            domain = CoachDomain.DIET, priority = CoachPriority.OPTIMIZATION,
            title = if (d.action == AdjustmentAction.DECREASE_KCAL) "Korekta: mniej kcal" else "Korekta: więcej kcal",
            message = d.explanation,
            actions = listOf(
                CoachAction(CoachActionType.APPLY_KCAL_ADJUST, "Zastosuj (${d.kcalDeltaProposed} kcal)"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "AutoAdjustmentService.${d.action.name}"
        )
        // HOLD / NEEDS_MORE_DATA → brak reakcji (plan działa / za mało danych — modułowość).
        AdjustmentAction.HOLD, AdjustmentAction.NEEDS_MORE_DATA -> null
    }

    private fun logVerdict(verdict: CoachVerdict, candidateCount: Int) {
        val p = verdict.primary
        if (p == null) {
            diag?.info(DiagnosticCategory.DETECTOR, "CoachOrchestrator", "coach_verdict_empty",
                "Coach: brak reakcji (kandydatów: $candidateCount)", success = true)
        } else {
            diag?.info(DiagnosticCategory.DETECTOR, "CoachOrchestrator", "coach_verdict",
                "Coach → ${p.priority.name}/${p.domain.name}: ${p.title} (+${verdict.secondary.size} zwiniętych)",
                dataJson = """{"primary":"${p.id}","priority":"${p.priority.name}","domain":"${p.domain.name}","secondary":${verdict.secondary.size},"candidates":$candidateCount}""",
                success = true)
        }
    }
}
