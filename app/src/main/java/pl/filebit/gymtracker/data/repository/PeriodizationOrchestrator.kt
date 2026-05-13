package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.ai.PlanStagnationReport
import pl.filebit.gymtracker.ai.StagnationAnalyzer
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseAnalyzer
import pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most między światami:
 *  - **Świat A** (pasywna detekcja): `TrainingPhaseAnalyzer` computed z volume tygodni.
 *    Brak dat startu/końca, brak prowadzenia user'a przez cykl.
 *  - **Świat B** (aktywny deload): `DeloadService` z 7-dniowym countdown gdy user
 *    explicit kliknął "Zastosuj deload".
 *  - **Nowy świat** (v1.13.0+): `TrainingMesocycle` entity z lifecycle PLANNED→ACTIVE→COMPLETED.
 *
 * Orchestrator (v1.14.0):
 *   1. Czyta aktywny mesocykl z DB.
 *   2. Jeśli brak → tworzy go z computed phase (mapowanie TrainingPhase → MesocyclePhase).
 *   3. Updateuje `weekInPhase` na podstawie `daysSinceStart`.
 *   4. Jeśli `plannedEndDateMs` minął → zwraca `TransitionDue` z proposal kolejnej fazy.
 *      **NIE** robi automatycznego transition — czeka na user/AI (v1.15.0).
 *
 * Wywoływany z:
 *  - `GymTrackerApp.onCreate()` (pierwszy puls przy starcie aplikacji)
 *  - `PeriodRollupService.closeWeekIfNeeded()` (po zamknięciu tygodnia)
 *  - `HomeViewModel` (refresh przy wejściu na Home)
 */
@Singleton
class PeriodizationOrchestrator @Inject constructor(
    private val mesoDao: TrainingMesocycleDao,
    private val phaseAnalyzer: TrainingPhaseAnalyzer,
    private val stagnationAnalyzer: StagnationAnalyzer
) {
    /**
     * Główne entry point.
     * @return aktualny stan periodyzacji do wyświetlenia w UI.
     */
    suspend fun pulse(nowMs: Long = System.currentTimeMillis()): PeriodizationState {
        val active = mesoDao.getActive()
        if (active == null) {
            return createInitialMesocycle(nowMs)
        }
        // Update weekInPhase jeśli zmieniło się od ostatniego pulse
        val daysSinceStart = active.daysSinceStart(nowMs)
        val newWeekInPhase = (daysSinceStart / 7 + 1).coerceIn(1, active.phaseLengthWeeks)
        val updated = if (active.weekInPhase != newWeekInPhase) {
            val u = active.copy(weekInPhase = newWeekInPhase)
            mesoDao.update(u)
            u
        } else active

        // v1.24.33 fix Bug #1 (raport SYM 3tyg): transition po przekroczeniu
        // totalDaysPlanned zamiast wyłącznie po plannedEndDateMs. Powód: integer
        // division `daysSinceStart` może równać się totalDaysPlanned mimo `nowMs <
        // plannedEndDateMs` o kilka godzin. Bez tego UI pokazywało "Dzień 22 z 21".
        // Sprawdź czy nie pora na transition
        val cycleOver = nowMs >= active.plannedEndDateMs || daysSinceStart >= active.totalDaysPlanned
        if (cycleOver) {
            val stagnation = runCatching { stagnationAnalyzer.analyzeAllRecent() }.getOrNull()
            val proposal = recommendNextPhase(
                currentMeso = updated,
                stagnationReport = stagnation,
                nowMs = nowMs
            )
            return PeriodizationState.TransitionDue(updated, proposal)
        }

        return PeriodizationState.Active(
            meso = updated,
            daysElapsed = daysSinceStart,
            daysRemaining = updated.daysRemaining(nowMs)
        )
    }

    /**
     * Tworzy pierwszy mesocykl gdy w bazie nie ma żadnego ACTIVE.
     * Używa TrainingPhaseAnalyzer żeby zgadnąć w jakiej fazie user jest.
     * Jeśli brak danych treningowych (< 4 workouts) → PeriodizationState.NoData.
     */
    private suspend fun createInitialMesocycle(nowMs: Long): PeriodizationState {
        val phaseStatus = runCatching { phaseAnalyzer.analyze() }.getOrNull()
        if (phaseStatus == null || phaseStatus.phase == TrainingPhase.NO_DATA) {
            return PeriodizationState.NoData
        }

        val mesoPhase = mapTrainingPhaseToMesocyclePhase(phaseStatus.phase)
        val durationWeeks = TrainingMesocycle.defaultLengthForPhase(mesoPhase)

        // Backdate start: jeśli user jest w tygodniu 3/3 akumulacji, cofnij start 2 tygodnie wstecz
        val weekInPhase = phaseStatus.weeksSinceLastDeload.coerceIn(1, durationWeeks)
        val startDateMs = nowMs - (weekInPhase - 1).toLong() * 7 * 24 * 3600 * 1000
        val plannedEndDateMs = startDateMs + durationWeeks.toLong() * 7 * 24 * 3600 * 1000

        val meso = TrainingMesocycle(
            startDateMs = startDateMs,
            plannedEndDateMs = plannedEndDateMs,
            phase = mesoPhase,
            weekInPhase = weekInPhase,
            phaseLengthWeeks = durationWeeks,
            volumeProgression = TrainingMesocycle.defaultVolumeProgression(mesoPhase),
            intensityProgression = TrainingMesocycle.defaultIntensityProgression(mesoPhase),
            targetRpe = TrainingMesocycle.defaultTargetRpe(mesoPhase),
            triggerReason = "computed_from_volume_v1_14_0",
            status = MesocycleStatus.ACTIVE,
            notes = "v1.14.0 — pierwszy mesocykl, computed z TrainingPhaseAnalyzer (faza=${phaseStatus.phase}, tydzień=${phaseStatus.weeksSinceLastDeload})"
        )
        val id = mesoDao.upsert(meso)
        val saved = meso.copy(id = id)
        return PeriodizationState.Active(
            meso = saved,
            daysElapsed = saved.daysSinceStart(nowMs),
            daysRemaining = saved.daysRemaining(nowMs)
        )
    }

    /**
     * Mapowanie TrainingPhase (computed) → MesocyclePhase (entity).
     * NEEDS_DELOAD traktujemy jak DELOAD (algorytm uważa że pora).
     */
    private fun mapTrainingPhaseToMesocyclePhase(p: TrainingPhase): MesocyclePhase = when (p) {
        TrainingPhase.ACCUMULATION -> MesocyclePhase.ACCUMULATION
        TrainingPhase.INTENSIFICATION -> MesocyclePhase.INTENSIFICATION
        TrainingPhase.DELOAD -> MesocyclePhase.DELOAD
        TrainingPhase.NEEDS_DELOAD -> MesocyclePhase.DELOAD
        TrainingPhase.NO_DATA -> MesocyclePhase.ACCUMULATION  // fallback (nie wywoływane bo guard wcześniej)
    }

    /**
     * Akceptuj propozycję transition — zamyka bieżący cykl + tworzy nowy.
     * Wywoływane gdy user klika "Zastosuj plan" w karcie "AI proponuje" (v1.15.0).
     * @return id nowego mesocyklu.
     */
    suspend fun applyTransition(
        proposal: TransitionProposal,
        currentMesoId: Long,
        nowMs: Long = System.currentTimeMillis(),
        aiDecision: String = ""
    ): Long {
        // Zamknij bieżący
        mesoDao.complete(currentMesoId, nowMs)
        // Stwórz nowy
        val plannedEnd = proposal.plannedStartDate + proposal.plannedDurationWeeks.toLong() * 7 * 24 * 3600 * 1000
        val newMeso = TrainingMesocycle(
            startDateMs = proposal.plannedStartDate,
            plannedEndDateMs = plannedEnd,
            phase = proposal.recommendedNext,
            weekInPhase = 1,
            phaseLengthWeeks = proposal.plannedDurationWeeks,
            volumeProgression = TrainingMesocycle.defaultVolumeProgression(proposal.recommendedNext),
            intensityProgression = TrainingMesocycle.defaultIntensityProgression(proposal.recommendedNext),
            targetRpe = TrainingMesocycle.defaultTargetRpe(proposal.recommendedNext),
            triggerReason = "transition_${proposal.fromPhase.name.lowercase()}_to_${proposal.recommendedNext.name.lowercase()}",
            aiAcceptedDecision = aiDecision,
            status = MesocycleStatus.ACTIVE,
            notes = "v1.14.0 — transition: ${proposal.reasoning}"
        )
        return mesoDao.upsert(newMeso)
    }

    /**
     * Skróć bieżący deload (przycisk "Zakończ wcześniej" w UI).
     * Zamyka deload + tworzy nowy cykl ACCUMULATION.
     */
    suspend fun endDeloadEarly(currentMesoId: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val current = mesoDao.getActive() ?: return -1L
        if (current.id != currentMesoId || current.phase != MesocyclePhase.DELOAD) return -1L

        mesoDao.complete(currentMesoId, nowMs)
        val newMeso = TrainingMesocycle(
            startDateMs = nowMs,
            plannedEndDateMs = nowMs + TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION).toLong() * 7 * 24 * 3600 * 1000,
            phase = MesocyclePhase.ACCUMULATION,
            weekInPhase = 1,
            phaseLengthWeeks = TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION),
            volumeProgression = TrainingMesocycle.defaultVolumeProgression(MesocyclePhase.ACCUMULATION),
            intensityProgression = TrainingMesocycle.defaultIntensityProgression(MesocyclePhase.ACCUMULATION),
            targetRpe = TrainingMesocycle.defaultTargetRpe(MesocyclePhase.ACCUMULATION),
            triggerReason = "user_ended_deload_early",
            status = MesocycleStatus.ACTIVE,
            notes = "v1.14.0 — user zakończył deload wcześniej, start akumulacji"
        )
        return mesoDao.upsert(newMeso)
    }

    /**
     * Przedłuż bieżący deload o tydzień (przycisk "Przedłuż o tydzień" w UI).
     */
    suspend fun extendCurrentPhase(currentMesoId: Long, addWeeks: Int = 1) {
        val current = mesoDao.getActive() ?: return
        if (current.id != currentMesoId) return
        val newPlannedEnd = current.plannedEndDateMs + addWeeks.toLong() * 7 * 24 * 3600 * 1000
        mesoDao.update(
            current.copy(
                plannedEndDateMs = newPlannedEnd,
                phaseLengthWeeks = current.phaseLengthWeeks + addWeeks,
                notes = current.notes + "\n[${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())}] przedłużono o $addWeeks tydz."
            )
        )
    }
}

sealed class PeriodizationState {
    /** <4 workoutów w bazie — za mało żeby ocenić fazę. */
    object NoData : PeriodizationState()

    /** Aktywny mesocykl w trakcie (między start a plannedEnd). */
    data class Active(
        val meso: TrainingMesocycle,
        val daysElapsed: Int,
        val daysRemaining: Int
    ) : PeriodizationState() {
        val progressPct: Int get() = if (meso.totalDaysPlanned == 0) 0
            else ((daysElapsed.toDouble() / meso.totalDaysPlanned) * 100).toInt().coerceIn(0, 100)
    }

    /** plannedEnd minęło — UI pokazuje "AI proponuje X" z buttonami. */
    data class TransitionDue(
        val current: TrainingMesocycle,
        val proposal: TransitionProposal
    ) : PeriodizationState()
}

/**
 * Propozycja przejścia fazy. Algorithm liczy, AI walidu (v1.15.0), user accept.
 */
data class TransitionProposal(
    val fromPhase: MesocyclePhase,
    val recommendedNext: MesocyclePhase,
    val plannedStartDate: Long,
    val plannedDurationWeeks: Int,
    val reasoning: String,
    val confidence: Double = 0.8
)

/**
 * Pure function — rekomendacja kolejnej fazy.
 *
 * Klasyczna periodyzacja liniowa:
 *   ACCUMULATION → INTENSIFICATION → DELOAD → ACCUMULATION (cykl 7 tyg total)
 *
 * Override'y:
 *   - ACCUMULATION + stagnacja ≥30% → przeskocz INTENSIFICATION, idź na DELOAD
 *   - INTENSIFICATION + stagnacja ≥30% → DELOAD (zaplanowany konieczny)
 *   - PEAKING → DELOAD (zawsze, post-peak recovery)
 *   - RECOVERY → ACCUMULATION (zaplanowane)
 *
 * Top-level dla testowalności bez DAO (wzorzec z `reconstructMesocycles` v1.13.0).
 */
internal fun recommendNextPhase(
    currentMeso: TrainingMesocycle,
    stagnationReport: PlanStagnationReport?,
    nowMs: Long
): TransitionProposal {
    val stagnationPct = stagnationReport?.stagnatingPct ?: 0.0
    val forceDeload = stagnationReport?.deloadRecommended == true || stagnationPct >= 30.0

    val nextPhase: MesocyclePhase
    val reasoning: String

    when (currentMeso.phase) {
        MesocyclePhase.ACCUMULATION -> {
            if (forceDeload) {
                nextPhase = MesocyclePhase.DELOAD
                reasoning = "Stagnacja w ${stagnationReport?.stagnatingCount ?: 0}/${stagnationReport?.totalAnalyzed ?: 0} ćwiczeń (>=30%). Skipping INTENSIFICATION, idziemy bezpośrednio na DELOAD żeby zresetować CNS."
            } else {
                nextPhase = MesocyclePhase.INTENSIFICATION
                reasoning = "Akumulacja ${currentMeso.phaseLengthWeeks} tyg ukończona, volume rośnie zgodnie z planem. Przejście do intensyfikacji: cięższe obciążenia, mniej setów, RPE 8-9."
            }
        }
        MesocyclePhase.INTENSIFICATION -> {
            nextPhase = MesocyclePhase.DELOAD
            reasoning = if (forceDeload)
                "Intensyfikacja + stagnacja ${stagnationPct.toInt()}% — koniecznie deload. Bez restu CNS się akumuluje, intensywność spada."
            else
                "Intensyfikacja ${currentMeso.phaseLengthWeeks} tyg ukończona. Planowany deload: -30% volume, -10% obciążenie, RPE ≤7."
        }
        MesocyclePhase.DELOAD -> {
            nextPhase = MesocyclePhase.ACCUMULATION
            reasoning = "Deload zakończony. Start akumulacji: budowanie volume, RPE 7-8, pauzy 60-90s."
        }
        MesocyclePhase.PEAKING -> {
            nextPhase = MesocyclePhase.DELOAD
            reasoning = "Peak testowanie zakończone. Konieczny deload post-peak żeby zregenerować CNS."
        }
        MesocyclePhase.RECOVERY -> {
            nextPhase = MesocyclePhase.ACCUMULATION
            reasoning = "Recovery zakończone. Start akumulacji z łagodnym volume."
        }
    }

    val durationWeeks = TrainingMesocycle.defaultLengthForPhase(nextPhase)
    val confidence = when {
        forceDeload && nextPhase == MesocyclePhase.DELOAD -> 0.9
        currentMeso.phase == MesocyclePhase.DELOAD -> 0.85  // standardowy powrót do akumulacji
        else -> 0.75
    }

    return TransitionProposal(
        fromPhase = currentMeso.phase,
        recommendedNext = nextPhase,
        plannedStartDate = nowMs,
        plannedDurationWeeks = durationWeeks,
        reasoning = reasoning,
        confidence = confidence
    )
}
