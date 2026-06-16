package pl.filebit.gymtracker.data.coach

/**
 * v2.33.0 (U3) — JEDEN model reakcji coacha. Wszystkie domeny (trening, dieta, regeneracja,
 * systematyczność) wyrażają reakcję tym samym typem, żeby był JEDEN kanał wyjścia (U4),
 * jeden priorytet i brak duplikacji.
 *
 * Patrz docs/COACH-DESIGN-SPEC.md (§4 drabina priorytetów, §5 anti-konflikt).
 */

enum class CoachDomain { TRAINING, DIET, RECOVERY, CONSISTENCY, HEALTH, GOAL }

/**
 * Drabina priorytetów (best practice trenerska, spec §4). Niższy rank = ważniejsze.
 * Dominująca reakcja = najniższy rank wśród aktywnych.
 */
enum class CoachPriority(val rank: Int) {
    HEALTH(1),         // kontuzja/ból, niebezpieczny deficyt — „najpierw nie szkodzić"
    RECOVERY(2),       // zła regeneracja → odpoczynek/refeed przed forsowaniem
    RETURN(3),         // ostrożny powrót po przerwie (≠ przetrenowanie)
    CONSISTENCY(4),    // opuszczone treningi/nieoznaczone posiłki → zachowanie przed liczbami
    OPTIMIZATION(5)    // korekty kcal, deload-sugestia, zwiększenie obciążenia, periodyzacja
}

/** Typ akcji przyciskowej (działa BEZ AI — to są deterministyczne akcje Tier A). */
enum class CoachActionType {
    NONE,
    APPLY_DELOAD, APPLY_REFEED, APPLY_KCAL_ADJUST, SIMPLIFY_PLAN, REST_INJURY, RETURN_LIGHT,
    START_WORKOUT, ADD_SNACK, OPEN_DIET, OPEN_TRAINING, OPEN_PERIODIZATION, OPEN_RECOVERY,
    // v2.59.0 (U9+U10): trener pyta „dlaczego nie trenujesz?" — payload = TrainingPauseReason.name.
    RECORD_PAUSE_REASON,
    ASK_AI, DISMISS
}

/** `payload` — opcjonalny ładunek (np. nazwa powodu przerwy dla RECORD_PAUSE_REASON). */
data class CoachAction(val type: CoachActionType, val label: String, val payload: String? = null)

/**
 * Pojedyncza, spójna reakcja coacha. `id` = stabilny klucz logiczny (dedup/dismiss).
 * `source` = który silnik ją wygenerował (audyt + log).
 */
data class CoachReaction(
    val id: String,
    val domain: CoachDomain,
    val priority: CoachPriority,
    val title: String,
    val message: String,
    val actions: List<CoachAction> = emptyList(),
    val source: String
)

/**
 * Wynik orchestratora: JEDNA dominująca reakcja + reszta zwinięta (spec §5 — „1 dominujący
 * na karcie/notyfikacji + reszta pod 'zobacz więcej'"). Pusty = brak reakcji (czysto).
 */
data class CoachVerdict(
    val primary: CoachReaction?,
    val secondary: List<CoachReaction> = emptyList()
) {
    val all: List<CoachReaction> get() = listOfNotNull(primary) + secondary
    val isEmpty: Boolean get() = primary == null
}

/** Czy reakcja jest „deload-podobna" (do dedup). */
internal fun CoachReaction.isDeloadLike(): Boolean =
    actions.any { it.type == CoachActionType.APPLY_DELOAD || it.type == CoachActionType.APPLY_REFEED }

/** v2.59.0 (U9+U10) — czy reakcja to „nagabywanie o trening" (opuszczony / zacznij logować). */
internal fun CoachReaction.isTrainingNag(): Boolean =
    id == "missed_workout" || actions.any { it.type == CoachActionType.START_WORKOUT }

/** Pytanie „dlaczego nie trenujesz?" z szybkimi odpowiedziami (payload = TrainingPauseReason.name). */
internal fun trainingPauseQuestion(everRecorded: Boolean): CoachReaction = CoachReaction(
    id = "training_pause_ask",
    domain = CoachDomain.CONSISTENCY, priority = CoachPriority.CONSISTENCY,
    title = if (everRecorded) "Wracasz do treningów?" else "Nie trenowałeś — co się stało?",
    message = if (everRecorded)
        "Minęła umówiona przerwa. Wracasz do treningów, czy potrzebujesz więcej czasu? " +
            "Powiedz mi co się dzieje — dostosuję plan i dietę."
    else
        "W tym tygodniu nie widzę treningów. Zanim cokolwiek zmienimy — co się stało? " +
            "To pozwoli mi dobrać plan i ochronę formy, zamiast wmawiać Ci „idź ćwiczyć”.",
    actions = listOf(
        CoachAction(CoachActionType.RECORD_PAUSE_REASON, "Brak czasu", "NO_TIME"),
        CoachAction(CoachActionType.RECORD_PAUSE_REASON, "Brak sprzętu/miejsca", "NO_ACCESS"),
        CoachAction(CoachActionType.RECORD_PAUSE_REASON, "Kontuzja", "INJURY"),
        CoachAction(CoachActionType.ASK_AI, "Napiszę trenerowi")
    ),
    source = "DeloadPreferences.trainingPause.ask"
)

/**
 * U9+U10 (pura, testowalna): gdy ZNAMY przyczynę (`pauseActive`) → usuń nagabywanie o trening.
 * Gdy nie znamy, a było nagabywanie → zamień je na JEDNO pytanie. Brak nagabywania → bez zmian.
 */
internal fun applyTrainingPauseRule(
    candidates: List<CoachReaction>,
    pauseActive: Boolean,
    hadPause: Boolean
): List<CoachReaction> {
    if (candidates.none { it.isTrainingNag() }) return candidates
    val withoutNags = candidates.filterNot { it.isTrainingNag() }
    return if (pauseActive) withoutNags else withoutNags + trainingPauseQuestion(hadPause)
}

/**
 * ARBITER (spec §4 priorytet + §5 anti-konflikt) — czysta funkcja, testowalna bez DB.
 * Zasady: kontuzja (HEALTH) wygrywa i ucisza deload-sugestię treningu; dedup „deloadu" zostawia
 * najważniejszy; sortowanie po priorytecie → dominująca = najniższy rank.
 */
internal fun arbitrateCoach(raw: List<CoachReaction>): CoachVerdict {
    if (raw.isEmpty()) return CoachVerdict(null)

    var list = raw
    // Anti-konflikt: kontuzja → pomijamy deload-sugestię treningu (ból ≠ przetrenowanie).
    if (list.any { it.priority == CoachPriority.HEALTH }) {
        list = list.filterNot {
            it.domain == CoachDomain.TRAINING && it.priority == CoachPriority.OPTIMIZATION
        }
    }
    // Dedup deloadu: jeśli wiele reakcji to deload/refeed, zostaw najważniejszą.
    val deloadKinds = list.filter { it.isDeloadLike() }
    if (deloadKinds.size > 1) {
        val keep = deloadKinds.minByOrNull { it.priority.rank }!!
        list = list.filterNot { it.isDeloadLike() && it !== keep }
    }
    val sorted = list.sortedBy { it.priority.rank }
    return CoachVerdict(primary = sorted.firstOrNull(), secondary = sorted.drop(1))
}
