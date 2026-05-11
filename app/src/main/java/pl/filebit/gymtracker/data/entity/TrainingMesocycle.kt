package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Faza mesocyklu treningowego (klasyczna periodyzacja).
 *
 *  - ACCUMULATION  — budowanie objętości (wyższa liczba setów, RPE 6-8, pauzy 60-90s). 2-4 tyg.
 *  - INTENSIFICATION — budowanie siły (mniej setów, cięższe ciężary, RPE 8-9, pauzy 3-5 min). 2-4 tyg.
 *  - DELOAD — regeneracja (-30% volume, -10% obciążenie, RPE ≤7). 1 tydzień (4-7 dni).
 *  - PEAKING — testowanie maksów (1-3 reps, RPE 9-10, długie pauzy). 1-2 tyg.
 *  - RECOVERY — całkowita przerwa lub bardzo lekki trening po kontuzji / długiej przerwie.
 */
enum class MesocyclePhase {
    ACCUMULATION,
    INTENSIFICATION,
    DELOAD,
    PEAKING,
    RECOVERY;

    /** v1.20.2 — polskie label fazy (single source of truth, używany przez UI + AI prompts). */
    fun labelPl(): String = when (this) {
        ACCUMULATION -> "Akumulacja"
        INTENSIFICATION -> "Intensyfikacja"
        DELOAD -> "Deload"
        PEAKING -> "Peaking"
        RECOVERY -> "Recovery"
    }
}

/**
 * Status cyklu w lifecycle.
 *
 *  - PLANNED — zaplanowany, jeszcze nie rozpoczęty (start w przyszłości lub czeka na decyzję AI).
 *  - ACTIVE — aktualnie trwa (między startDateMs a plannedEndDateMs/endDateMs).
 *  - COMPLETED — zakończony zgodnie z planem (endDateMs ustawione).
 *  - SKIPPED — odrzucony przez usera lub anulowany przez orchestrator.
 *
 * W każdym momencie powinien istnieć **co najwyżej jeden** mesocykl ze statusem ACTIVE.
 */
enum class MesocycleStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    SKIPPED
}

/**
 * Mezo-cykl treningowy — datowany blok 1-6 tygodni z konkretną fazą.
 *
 * v1.13.0 (audit 2026-05-10): fundament periodyzacji. Wcześniej faza była *computed*
 * z TrainingPhaseAnalyzer (volume tygodnia vs mediana 8 tyg) — bez dat startu/końca,
 * bez prowadzenia user'a przez cykl. Teraz cykl jest entity z lifecycle:
 *
 *  Algorithm proposes → PeriodizationOrchestrator (v1.14.0) → AI validates (v1.15.0)
 *  → user accepts → status PLANNED → startDate hits now → status ACTIVE
 *  → plannedEndDate hits now → status COMPLETED + new cycle starts.
 *
 * Filozofia Hybrid AI Engine:
 *  - algorytm = księgowy (twarde fakty: tydzień 6/6, ACWR 1.6, RPE rośnie)
 *  - AI = trener (decyduje: zastosuj/zmodyfikuj/przesuń, uzasadnia)
 *  - user = wykonawca (widzi konkret z buttonami)
 */
@Entity(
    tableName = "training_mesocycles",
    indices = [
        Index(value = ["startDateMs"]),
        Index(value = ["status"])
    ]
)
data class TrainingMesocycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Kiedy cykl się zaczyna (epoch ms, lokalne 00:00). */
    val startDateMs: Long,

    /** Faktyczne zakończenie (null gdy jeszcze trwa). */
    val endDateMs: Long? = null,

    /** Planowany koniec (do liczenia daysRemaining). Może być != endDateMs jeśli user zakończył wcześniej/przedłużył. */
    val plannedEndDateMs: Long,

    /** Aktualna faza. */
    val phase: MesocyclePhase,

    /** Aktualny tydzień w fazie (1..phaseLengthWeeks). Update po każdym tygodniowym rollupie. */
    val weekInPhase: Int = 1,

    /** Planowana długość fazy w tygodniach. ACCUMULATION/INTENSIFICATION typowo 2-4, DELOAD typowo 1. */
    val phaseLengthWeeks: Int = 3,

    /** Powiązany plan treningowy (TrainingPlan.id) — opcjonalne. */
    val trainingPlanId: Long? = null,

    /** Scale factor dla objętości (1.0 = normalne, 0.7 = deload, 1.1 = peak akumulacji). */
    val volumeProgression: Double = 1.0,

    /** Scale factor dla intensywności (1.0 = normalne, 0.9 = deload, 1.05 = intensyfikacja). */
    val intensityProgression: Double = 1.0,

    /** Target RPE dla setów roboczych w tej fazie (6-9). */
    val targetRpe: Int = 8,

    /** Powód uruchomienia (z PeriodizationOrchestrator): "stagnation_30pct", "max_weeks_no_deload", "user_request". */
    val triggerReason: String = "",

    /** Decyzja AI uzasadniająca akcję (tekst dla user'a do "Wyjaśnij dlaczego"). */
    val aiAcceptedDecision: String = "",

    /** Pewność AI co do tej decyzji (0.0-1.0). */
    val aiConfidence: Double? = null,

    /** Lifecycle: PLANNED → ACTIVE → COMPLETED (lub SKIPPED). */
    val status: MesocycleStatus = MesocycleStatus.PLANNED,

    /** User notes. */
    val notes: String = "",

    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status == MesocycleStatus.ACTIVE

    /** Ile dni zostało do planowanego końca (0 jeśli przekroczone). */
    fun daysRemaining(nowMs: Long = System.currentTimeMillis()): Int =
        ((plannedEndDateMs - nowMs) / DAY_MS).toInt().coerceAtLeast(0)

    /** Ile dni minęło od startu (0 jeśli startMs jeszcze nie minął). */
    fun daysSinceStart(nowMs: Long = System.currentTimeMillis()): Int =
        ((nowMs - startDateMs) / DAY_MS).toInt().coerceAtLeast(0)

    /** Całkowita długość cyklu w dniach (od start do plannedEnd). */
    val totalDaysPlanned: Int
        get() = ((plannedEndDateMs - startDateMs) / DAY_MS).toInt().coerceAtLeast(1)

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Domyślne długości faz w tygodniach (z literatury Schoenfeld/Helms/RP). */
        fun defaultLengthForPhase(phase: MesocyclePhase): Int = when (phase) {
            MesocyclePhase.ACCUMULATION -> 3
            MesocyclePhase.INTENSIFICATION -> 3
            MesocyclePhase.DELOAD -> 1
            MesocyclePhase.PEAKING -> 2
            MesocyclePhase.RECOVERY -> 1
        }

        /** Domyślne scale factors dla volume per faza. */
        fun defaultVolumeProgression(phase: MesocyclePhase): Double = when (phase) {
            MesocyclePhase.ACCUMULATION -> 1.1   // +10% nad baseline
            MesocyclePhase.INTENSIFICATION -> 0.9 // -10% (mniej setów, ciężej)
            MesocyclePhase.DELOAD -> 0.6         // -40% (-30% wg literatury, ale safer)
            MesocyclePhase.PEAKING -> 0.5
            MesocyclePhase.RECOVERY -> 0.3
        }

        /** Domyślne scale factors dla intensity per faza. */
        fun defaultIntensityProgression(phase: MesocyclePhase): Double = when (phase) {
            MesocyclePhase.ACCUMULATION -> 0.95
            MesocyclePhase.INTENSIFICATION -> 1.05
            MesocyclePhase.DELOAD -> 0.9
            MesocyclePhase.PEAKING -> 1.1
            MesocyclePhase.RECOVERY -> 0.7
        }

        /** Domyślne target RPE per faza. */
        fun defaultTargetRpe(phase: MesocyclePhase): Int = when (phase) {
            MesocyclePhase.ACCUMULATION -> 7
            MesocyclePhase.INTENSIFICATION -> 8
            MesocyclePhase.DELOAD -> 6
            MesocyclePhase.PEAKING -> 9
            MesocyclePhase.RECOVERY -> 5
        }
    }
}
