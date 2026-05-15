package pl.filebit.gymtracker.testkit

import pl.filebit.gymtracker.ai.DataMaturity
import pl.filebit.gymtracker.ai.ReadinessZone
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
        val readinessZone = state.trainingReadiness?.zone

        // 1. Deload zalecany, ale gotowość wysoka — sprzeczne sygnały.
        // B4: liczy się TYLKO gdy readiness jest DOJRZAŁY (MATURE). Przy
        // maturity=LEARNING readiness to wartość domyślna (brak danych
        // recovery), resolver i tak ukrywa kartę — user nie widzi sprzeczności.
        if (deload is DeloadCardState.Suggestion &&
            readinessZone in setOf(ReadinessZone.PEAK, ReadinessZone.GOOD) &&
            state.trainingReadiness?.maturity == DataMaturity.MATURE) {
            issues += Inconsistency(
                Inconsistency.Severity.KONFLIKT,
                "DeloadService zaleca deload (przeciążenie), ale TrainingReadiness " +
                    "= $readinessZone (gotowy). Sprzeczne werdykty dwóch systemów."
            )
        }

        // Uwaga (symulacja realnego użytkowania): checker celowo NIE porównuje
        // TrainingPhase z DeloadService ani ACWR z deloadem. To różne wymiary:
        //  - TrainingPhase = periodyzacja PROAKTYWNA (upływ tygodni cyklu),
        //  - DeloadService = REAKTYWNE objawy (RPE, stagnacja, ból, powrót),
        //  - ACWR = tonaż, nie subiektywne zmęczenie.
        // Mogą się legalnie różnić (np. CUT: RPE↑ przy stałym tonażu = ACWR
        // OPTIMAL + deload-refeed — komplementarne, nie sprzeczne). Checker
        // flaguje tylko sygnały WPROST przeciwne i WIDOCZNE userowi
        // jednocześnie (deload "odpuść" vs gotowość/regeneracja "świetnie").

        // 2. Recovery wysoki, ale deload zalecany.
        // B4: tylko gdy RecoveryScore DOJRZAŁY — przy LEARNING score=75 to
        // wartość domyślna (0 dni danych HRV/sen), nie realny pomiar.
        val recovery = state.recoveryScore
        if (deload is DeloadCardState.Suggestion && recovery != null &&
            recovery.score >= 75 && recovery.maturity == DataMaturity.MATURE) {
            issues += Inconsistency(
                Inconsistency.Severity.OSTRZEZENIE,
                "DeloadService zaleca deload, ale RecoveryScore = ${recovery.score} " +
                    "(wysoki). Sygnał regeneracji nie potwierdza przeciążenia."
            )
        }

        return issues
    }
}
