package pl.filebit.gymtracker.testkit

import pl.filebit.gymtracker.ai.LoadZone
import pl.filebit.gymtracker.ai.ReadinessZone
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.ui.home.HomeUiState

/**
 * v1.27 — FAZA 1.6 — aktywny detektor sprzeczności sygnałów Home.
 *
 * Ekran Home agreguje kilka niezależnych systemów (DeloadService,
 * TrainingPhaseAnalyzer, TrainingLoadAnalyzer, TrainingReadinessAnalyzer).
 * Mogą dawać werdykty sprzeczne kierunkowo — np. "deload zalecany"
 * (przeciążenie) i jednocześnie "gotowość GOOD". Test sam tego nie zauważy
 * patrząc na pojedyncze pola — checker porównuje sygnały PARAMI i flaguje
 * niespójności w raporcie (sekcja OCENA).
 *
 * To wprost adresuje wymóg Macieja: "czy wszystko ze sobą współgra,
 * gdzie jest niespójność".
 */
data class Inconsistency(
    val severity: Severity,
    val message: String
) {
    enum class Severity { KONFLIKT, OSTRZEZENIE }
}

object HomeConsistencyChecker {

    fun check(state: HomeUiState): List<Inconsistency> {
        val issues = mutableListOf<Inconsistency>()
        val deload = state.deloadCard
        val phase = state.trainingPhase?.phase
        val loadZone = state.trainingLoad?.zone
        val readinessZone = state.trainingReadiness?.zone

        // 1. Deload zalecany, ale gotowość wysoka — sprzeczne sygnały
        if (deload is DeloadCardState.Suggestion &&
            readinessZone in setOf(ReadinessZone.PEAK, ReadinessZone.GOOD)) {
            issues += Inconsistency(
                Inconsistency.Severity.KONFLIKT,
                "DeloadService zaleca deload (przeciążenie), ale TrainingReadiness " +
                    "= $readinessZone (gotowy). Sprzeczne werdykty dwóch systemów."
            )
        }

        // 2. Faza mówi NEEDS_DELOAD, ale karta deloadu nie istnieje
        if (phase == TrainingPhase.NEEDS_DELOAD && deload is DeloadCardState.None) {
            issues += Inconsistency(
                Inconsistency.Severity.KONFLIKT,
                "TrainingPhase = NEEDS_DELOAD, ale DeloadService nie wystawił " +
                    "żadnej karty (None). Dwa systemy oceniają potrzebę deloadu inaczej."
            )
        }

        // 3. Deload zalecany, ale obciążenie OPTIMAL
        if (deload is DeloadCardState.Suggestion && loadZone == LoadZone.OPTIMAL) {
            issues += Inconsistency(
                Inconsistency.Severity.OSTRZEZENIE,
                "DeloadService zaleca deload, ale TrainingLoad = OPTIMAL " +
                    "(obciążenie w normie). Werdykt deloadu nie ma potwierdzenia w ACWR."
            )
        }

        // 4. Faza mówi że deload TRWA, ale brak aktywnego deloadu
        if (phase == TrainingPhase.DELOAD && deload !is DeloadCardState.Active) {
            issues += Inconsistency(
                Inconsistency.Severity.KONFLIKT,
                "TrainingPhase = DELOAD (faza deloadu trwa), ale DeloadService " +
                    "nie ma aktywnego deloadu (${deload::class.simpleName}). Niespójność stanu."
            )
        }

        // 5. Recovery wysoki, ale deload zalecany
        val recovery = state.recoveryScore
        if (deload is DeloadCardState.Suggestion && recovery != null && recovery.score >= 75) {
            issues += Inconsistency(
                Inconsistency.Severity.OSTRZEZENIE,
                "DeloadService zaleca deload, ale RecoveryScore = ${recovery.score} " +
                    "(wysoki). Sygnał regeneracji nie potwierdza przeciążenia."
            )
        }

        return issues
    }
}
