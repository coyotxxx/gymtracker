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
    // v2.59.0 (U9+U10): pamięć przyczyny przerwy w treningach (istniejący store stanu alertów).
    // nullable-default — testy bez niego = stare zachowanie (nagabywanie).
    private val deloadPreferences: pl.filebit.gymtracker.data.repository.DeloadPreferences? = null,
    private val diag: DiagnosticLogger? = null
) {

    /**
     * Zbiera werdykty domenowe i zwraca JEDNĄ dominującą reakcję + resztę zwiniętą.
     * @param dismissedIds reakcje odrzucone przez usera (Karta coacha „X") — pomijane.
     */
    suspend fun evaluate(dismissedIds: Set<String> = emptySet()): CoachVerdict {
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

        // === U9+U10: trener PYTA o przyczynę zamiast nagabywać „idź ćwiczyć" ===
        applyTrainingPause(candidates)

        val visible = if (dismissedIds.isEmpty()) candidates
            else candidates.filterNot { it.id in dismissedIds }
        val verdict = arbitrateCoach(visible)
        logVerdict(verdict, visible.size)
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
            // v2.44.0: luka danych regeneracji → akcja otwiera dialog oceny. Niski priorytet
            // (CONSISTENCY) — realne sygnały (HEALTH/RECOVERY/RETURN) zawsze go przykrywają.
            pl.filebit.gymtracker.ai.NotificationAction.LOG_RECOVERY ->
                Triple(CoachDomain.RECOVERY, CoachPriority.CONSISTENCY, CoachActionType.OPEN_RECOVERY)
            // v2.78.0: trenujesz lżej niż zwykle → propozycja dołożenia obciążenia (przejmuje funkcję karty Obciążenie).
            pl.filebit.gymtracker.ai.NotificationAction.INCREASE_LOAD ->
                Triple(CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.INCREASE_LOAD)
            pl.filebit.gymtracker.ai.NotificationAction.NONE ->
                Triple(if (crit) CoachDomain.RECOVERY else CoachDomain.GOAL,
                    if (crit) CoachPriority.RECOVERY else CoachPriority.OPTIMIZATION, CoachActionType.NONE)
        }
        // Etykieta przycisku = czytelna nazwa akcji (nie ucięty tytuł notyfikacji).
        val actionLabel = when (action) {
            CoachActionType.APPLY_DELOAD -> "Zastosuj deload"
            CoachActionType.INCREASE_LOAD -> "Zwiększ obciążenie"
            CoachActionType.START_WORKOUT -> "Zacznij trening"
            CoachActionType.OPEN_RECOVERY -> "Oceń regenerację"
            CoachActionType.OPEN_DIET -> "Otwórz dietę"
            CoachActionType.OPEN_TRAINING -> "Otwórz trening"
            CoachActionType.OPEN_PERIODIZATION -> "Plan cyklu"
            else -> n.title.take(24)
        }
        val actions = buildList {
            if (action != CoachActionType.NONE) add(CoachAction(action, actionLabel))
            add(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI"))
        }
        return CoachReaction(
            id = "nc_${n.id}", domain = domain, priority = priority,
            title = n.title, message = n.message, actions = actions, source = "NotificationCenter"
        )
    }

    /**
     * U9+U10: gdy ZNAMY przyczynę przerwy (zapamiętaną w DeloadPreferences) → wyciszamy
     * nagabywanie o trening do terminu powrotu. Gdy NIE znamy, a jest nagabywanie → zamieniamy
     * je na JEDNO pytanie „co się stało?" (czysta reguła `applyTrainingPauseRule`). Bez store
     * (testy konstruujące orchestrator wprost) — stare zachowanie.
     */
    private fun applyTrainingPause(candidates: MutableList<CoachReaction>) {
        val prefs = deloadPreferences ?: return
        val pauseActive = runCatching { prefs.trainingPause() }.getOrNull() != null
        val hadPause = runCatching { prefs.hadTrainingPause() }.getOrDefault(false)
        val transformed = applyTrainingPauseRule(candidates, pauseActive, hadPause)
        candidates.clear()
        candidates.addAll(transformed)
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
            // U7: gdy korekta wynika z braku treningu — podnieś priorytet (regeneracja/spójność),
            // bo to ochrona mięśni, nie zwykła optymalizacja makro.
            domain = CoachDomain.DIET,
            priority = if (d.reason == "cut_no_training_protect_muscle") CoachPriority.CONSISTENCY else CoachPriority.OPTIMIZATION,
            title = when {
                d.reason == "cut_no_training_protect_muscle" -> "Dieta bez treningu"
                d.action == AdjustmentAction.DECREASE_KCAL -> "Korekta: mniej kcal"
                else -> "Korekta: więcej kcal"
            },
            message = d.explanation,
            actions = listOf(
                CoachAction(CoachActionType.APPLY_KCAL_ADJUST, "Zastosuj (${d.kcalDeltaProposed} kcal)"),
                CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
            ),
            source = "AutoAdjustmentService.${d.action.name}"
        )
        // HOLD: dietetyk WYJAŚNIA dlaczego nie zmienia diety (zamiast milczeć) — gdy powód jest
        // ochronny/actionable. „Szczęśliwe" HOLD (cut_progressing/default) i karencja = cisza,
        // żeby nie spamować. Każda taka karta jest zamykalna (X) per dzień.
        AdjustmentAction.HOLD -> when (d.reason) {
            "cut_no_training_hold" -> CoachReaction(
                id = "diet_no_training",
                domain = CoachDomain.DIET, priority = CoachPriority.CONSISTENCY,
                title = "Dieta bez treningu", message = d.explanation,
                actions = listOf(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")),
                source = "AutoAdjustmentService.no_training"
            )
            // Wstrzymanie z powodu regeneracji — akcja prowadzi do oceny regeneracji.
            "cut_recovery_poor_sleep_stress" -> CoachReaction(
                id = "diet_hold_recovery",
                domain = CoachDomain.RECOVERY, priority = CoachPriority.OPTIMIZATION,
                title = "Trzymam dietę — regeneracja", message = d.explanation,
                actions = listOf(
                    CoachAction(CoachActionType.OPEN_RECOVERY, "Oceń regenerację"),
                    CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
                ),
                source = "AutoAdjustmentService.hold_recovery"
            )
            // Wstrzymanie bo spadły kroki (NEAT), nie metabolizm — popraw kroki, nie tnij.
            "cut_neat_drop" -> CoachReaction(
                id = "diet_hold_neat",
                domain = CoachDomain.DIET, priority = CoachPriority.OPTIMIZATION,
                title = "Trzymam dietę — kroki", message = d.explanation,
                actions = listOf(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")),
                source = "AutoAdjustmentService.hold_neat"
            )
            // Wstrzymanie bo niskie nawodnienie maskuje progres.
            "cut_low_hydration" -> CoachReaction(
                id = "diet_hold_hydration",
                domain = CoachDomain.DIET, priority = CoachPriority.OPTIMIZATION,
                title = "Trzymam dietę — nawodnienie", message = d.explanation,
                actions = listOf(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")),
                source = "AutoAdjustmentService.hold_hydration"
            )
            // Waga stoi, ale realizujesz <80% treningów — najpierw frekwencja, nie cięcie.
            "cut_stagnation_low_workouts" -> CoachReaction(
                id = "diet_hold_low_workouts",
                domain = CoachDomain.CONSISTENCY, priority = CoachPriority.OPTIMIZATION,
                title = "Trzymam dietę — frekwencja", message = d.explanation,
                actions = listOf(CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")),
                source = "AutoAdjustmentService.hold_low_workouts"
            )
            // v2.62.0: jesz powyżej celu na redukcji → wprost, to nie cisza. Wyżej niż inne HOLD
            // (CONSISTENCY) — bo to realnie blokuje cel, user powinien to zobaczyć.
            "cut_overeating" -> CoachReaction(
                id = "diet_overeating",
                domain = CoachDomain.DIET, priority = CoachPriority.CONSISTENCY,
                title = "Jesz powyżej celu", message = d.explanation,
                actions = listOf(
                    CoachAction(CoachActionType.OPEN_DIET, "Otwórz dietę"),
                    CoachAction(CoachActionType.ASK_AI, "Zapytaj AI")
                ),
                source = "AutoAdjustmentService.overeating"
            )
            // cut_progressing / cut_default_hold / adjustment_cooldown / inne → cisza.
            else -> null
        }
        AdjustmentAction.NEEDS_MORE_DATA -> null
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
