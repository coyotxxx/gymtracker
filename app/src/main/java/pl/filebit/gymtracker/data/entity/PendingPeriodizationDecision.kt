package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Status oczekującej decyzji periodyzacyjnej.
 *
 *  - PENDING — czeka na user (widoczna w karcie "AI TRENER PROPONUJE" na Home)
 *  - ACCEPTED — user kliknął "Zastosuj" → orchestrator wykonał transition
 *  - MODIFIED — user kliknął "Zmień" → edytował proposal i zaakceptował (v1.16.0)
 *  - DISMISSED — user kliknął X / "Później"
 */
enum class DecisionStatus {
    PENDING,
    ACCEPTED,
    MODIFIED,
    DISMISSED
}

/**
 * v1.15.0 — propozycja AI dotycząca przejścia mezo-cyklu.
 *
 * Workflow:
 *   1. `PeriodizationOrchestrator.pulse()` wykryje TransitionDue (plannedEnd minął).
 *   2. `ProactiveAiCheckWorker` pyta AI z tool `propose_periodization_action`.
 *   3. AI zwraca decyzję (zazwyczaj zgodną z algorithm proposal lub modyfikuje daty).
 *   4. Worker zapisuje `PendingPeriodizationDecision` ze status=PENDING + wysyła push.
 *   5. User otwiera Home → karta "AI TRENER PROPONUJE" → klikni [Zastosuj]/[Zmień]/[Wyjaśnij].
 *   6. Status zmienia się: ACCEPTED (orchestrator.applyTransition) lub MODIFIED/DISMISSED.
 *
 * Audit trail: pełen historia decyzji AI w bazie. Useful dla:
 *  - User wglądu ("co AI mi sugerował?")
 *  - Debugowania algorytmu (czy AI proponuje sensowne rzeczy)
 *  - Future ML: rozważanie wzorców (które propozycje user accept'uje vs odrzuca)
 *
 * **Bezpieczeństwo:** decyzja NIGDY nie jest auto-zastosowana. User musi explicit accept.
 */
@Entity(
    tableName = "pending_periodization_decisions",
    indices = [
        Index("status"),
        Index("createdAt")
    ]
)
data class PendingPeriodizationDecision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Kiedy utworzona — przez ProactiveAiCheckWorker. */
    val createdAt: Long = System.currentTimeMillis(),

    /** TrainingMesocycle.id dla którego AI proponuje transition. */
    val currentMesoId: Long,

    /**
     * `TransitionProposal` z PeriodizationOrchestrator (algorithm side).
     * JSON dla flexibility — fields: fromPhase, recommendedNext, plannedStartDate,
     * plannedDurationWeeks, reasoning, confidence.
     */
    val algorithmProposalJson: String,

    /**
     * AI decision JSON (full tool response).
     * Pola: action, plannedStartDate, plannedDurationWeeks, intensityModifier,
     * volumeModifier, reasoning (text dla user'a).
     *
     * AI może zaproponować EXACTLY to co algorithm (pełna zgoda) lub MODYFIKOWAĆ
     * (np. "deload od środy zamiast od poniedziałku bo masz PR podejście w sobotę").
     */
    val aiDecisionJson: String,

    /** Plain text reasoning dla user'a — używany w karcie "Wyjaśnij więcej". */
    val aiReasoning: String,

    /** Pewność decyzji 0.0-1.0 (AI's self-reported confidence). */
    val confidence: Double,

    val status: DecisionStatus = DecisionStatus.PENDING,

    /** Kiedy user rozwiązał (null jeśli wciąż PENDING). */
    val resolvedAt: Long? = null,

    /** Tekstowy opis akcji user'a ("ACCEPTED — applied at 13:42"). */
    val resolvedAction: String? = null
)
