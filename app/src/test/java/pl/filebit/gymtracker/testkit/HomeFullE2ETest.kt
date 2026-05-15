package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.ui.home.HomeCardsResolver
import pl.filebit.gymtracker.ui.home.HomeUiState

/**
 * v1.27 — FAZA 1.4 — pełny E2E ekranu Home.
 *
 * Scenariusz danych → in-memory Room → PRAWDZIWE detektory liczą stan →
 * HomeCardsResolver → raport. W odróżnieniu od pilota v1 (syntetyczne stany)
 * to wykrywa bugi detektorów: jeśli DeloadDetector źle policzy z 30 treningów,
 * raport to pokaże.
 *
 * Raport (TraceReport) ma pełną strukturę:
 *   DANE (pokrycie) — co analiza miała, czego brak
 *   DETEKTORY — werdykt + liczby które do niego doprowadziły
 *   CO WIDZI USER / UKRYTE — finalne karty
 *   OCENA — auto-flagi jakości
 */
class HomeFullE2ETest : TestHarness() {

    private fun runScenario(scenario: String): HomeUiState = runBlocking {
        loadScenario(scenario)
        val detectors = HomeDetectors(db, context)
        val state = detectors.buildHomeState()
        val cards = HomeCardsResolver.resolve(state)

        // — pokrycie danych —
        val workouts = db.workoutDao().observeAllOnce()
        val sets = db.workoutSetDao().getAll()
        val measurements = db.bodyMeasurementDao().getAllAsc()
        val recoveryDays = state.recoveryScore?.daysOfData ?: 0

        val tr = TraceReport("home-e2e-$scenario")
            .section("DANE (pokrycie)")
            .coverage("treningi", workouts.isNotEmpty(), "${workouts.size}")
            .coverage("sety", sets.isNotEmpty(), "${sets.size}")
            .coverage("pomiary wagi", measurements.isNotEmpty(), "${measurements.size}")
            .coverage("dane recovery (HRV/sen)", recoveryDays > 0, "$recoveryDays dni")

        tr.section("DETEKTORY (werdykt + liczby)")
        // DeloadService
        when (val d = state.deloadCard) {
            is DeloadCardState.Suggestion -> tr.verdict(
                "DeloadService", "Suggestion",
                "severity=${d.recommendation.severity}, refeed=${d.recommendation.recommendsDietBreak}, " +
                    "powód: ${d.recommendation.reason}"
            )
            is DeloadCardState.Active -> tr.verdict(
                "DeloadService", "Active", "isFinished=${d.isFinished}"
            )
            is DeloadCardState.ReturnAfterBreak -> tr.verdict(
                "DeloadService", "ReturnAfterBreak", "przerwa ${d.recommendation.breakDays} dni"
            )
            is DeloadCardState.ActiveInjury -> tr.verdict(
                "DeloadService", "ActiveInjury", "obszar=${d.recommendation.painArea}"
            )
            DeloadCardState.None -> tr.verdict("DeloadService", "None", "brak alertu")
        }
        // TrainingPhase
        state.trainingPhase?.let { p ->
            tr.verdict("TrainingPhaseAnalyzer", p.phase.name,
                "tygodni od deloadu=${p.weeksSinceLastDeload}")
        }
        // TrainingLoad
        state.trainingLoad?.let { l ->
            tr.verdict("TrainingLoadAnalyzer", l.zone.name,
                "ACWR=${"%.2f".format(l.acwr)}, dni danych=${l.daysOfData}, " +
                    "treningi14d=${l.workoutsCount14d}")
        }
        // RecoveryScore
        state.recoveryScore?.let { r ->
            tr.verdict("RecoveryScoreCalculator", "score=${r.score}",
                "maturity=${r.maturity}, dni danych=${r.daysOfData}")
        }
        // TrainingReadiness
        state.trainingReadiness?.let { rd ->
            tr.verdict("TrainingReadinessAnalyzer", "${rd.zone} (${rd.score})",
                "maturity=${rd.maturity}")
        }

        tr.section("CO WIDZI USER (${cards.visible.size} kart)")
        cards.visible.forEach { tr.shows(it.key, it.title) }
        tr.section("UKRYTE (${cards.hidden.size})")
        cards.hidden.forEach { tr.hidden(it.key, it.reason) }

        // — OCENA: auto-flagi jakości —
        tr.section("OCENA")
        if (recoveryDays == 0) {
            tr.note("brak danych recovery (HRV/sen) — score regeneracji oparty na domyślnych")
        }
        if (workouts.size < 8) {
            tr.note("mało treningów (${workouts.size}) — analizy fazy/obciążenia mniepewne")
        }
        if (cards.visible.none { it.key.startsWith("DELOAD") || it.key == "TRAINING_LOAD" }
            && workouts.size >= 20) {
            tr.note("dużo treningów a brak karty obciążenia/deloadu — sprawdź detektory")
        }
        tr.emit()

        state
    }

    @Test
    fun `E2E overtraining_cut — 30 treningow wysokie RPE`() {
        val state = runScenario("overtraining_cut")
        // 30 treningów RPE 9-10, profil CUT — detektor PApowinien coś wykryć
        assertTrue("przy 30 treningach trainingLoad powinien być policzony",
            state.trainingLoad != null)
    }

    @Test
    fun `E2E healthy — 24 treningi normalne RPE`() {
        val state = runScenario("healthy")
        assertTrue("trainingPhase policzona", state.trainingPhase != null)
    }

    @Test
    fun `E2E return_after_break — przerwa 32 dni`() {
        runScenario("return_after_break")
    }

    @Test
    fun `E2E injury — ostatni trening z bolem`() {
        runScenario("injury")
    }

    @Test
    fun `E2E fresh — swiezy user`() {
        runScenario("fresh")
    }
}
