package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.DataMaturity
import pl.filebit.gymtracker.ai.LoadZone
import pl.filebit.gymtracker.ai.ReadinessZone
import pl.filebit.gymtracker.ai.TrainingLoad
import pl.filebit.gymtracker.ai.TrainingPhase
import pl.filebit.gymtracker.ai.TrainingPhaseStatus
import pl.filebit.gymtracker.ai.TrainingReadiness
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.data.repository.DismissedCardsPrefs
import pl.filebit.gymtracker.ui.home.HomeCardsResolver
import pl.filebit.gymtracker.ui.home.HomeCardsResult
import pl.filebit.gymtracker.ui.home.HomeUiState
import pl.filebit.gymtracker.util.DeloadRecommendation
import pl.filebit.gymtracker.util.DeloadSeverity

/**
 * v1.27 — FAZA 1 — pilot snapshot ekranu Home.
 *
 * Dla każdego stanu generuje raport "co widzi user, co świadomie ukryto,
 * dlaczego" przez TraceReport + HomeCardsResolver. Czysty test (resolver
 * to pure function) — bez Robolectric, sekundy.
 *
 * To PILOT — walidacja wzorca raportu. Po akceptacji (FAZA 1.5) wzorzec
 * skalujemy na pełny E2E (scenariusz → detektory → stan → resolver) i na
 * pozostałe 47 ekranów.
 */
class HomeSnapshotTest {

    // ── helpery budowy stanu ──────────────────────────────────────────────

    private fun phase(p: TrainingPhase) = TrainingPhaseStatus(p, 4, "")

    private fun load(zone: LoadZone, acwr: Double = 1.0) =
        TrainingLoad(1000.0, 1000.0, acwr, zone, 20, 6, "")

    private fun readiness(maturity: DataMaturity) =
        TrainingReadiness(72, ReadinessZone.GOOD, 70, 75, 70, "", maturity)

    private fun deloadSuggestion() =
        DeloadCardState.Suggestion(DeloadRecommendation(DeloadSeverity.HIGH, "RPE 10 przez 2 tyg"))

    /** Raport stanu Home — wspólny format dla wszystkich snapshotów. */
    private fun report(scenario: String, state: HomeUiState): HomeCardsResult {
        val result = HomeCardsResolver.resolve(state)
        val tr = TraceReport(scenario)
            .section("STAN WEJŚCIOWY")
            .verdict("deloadCard", state.deloadCard::class.simpleName ?: "?", "")
            .verdict("trainingPhase", state.trainingPhase?.phase?.name ?: "null", "")
            .verdict("trainingLoad", state.trainingLoad?.zone?.name ?: "null", "")
            .verdict("trainingReadiness", state.trainingReadiness?.maturity?.name ?: "null", "")
            .verdict("recoveryScore", if (state.recoveryScore != null) "obecny" else "null", "")
            .verdict("goalAchievement", if (state.goalAchievement != null) "obecny" else "null", "")
            .verdict("pendingDecisions", state.pendingDecisions.size.toString(), "")
            .kv("dismissedCards", state.dismissedCards.joinToString().ifBlank { "—" })
            .section("CO WIDZI USER (${result.visible.size} kart)")
        result.visible.forEach { tr.shows(it.key, it.title) }
        tr.section("UKRYTE (${result.hidden.size})")
        result.hidden.forEach { tr.hidden(it.key, it.reason) }
        tr.emit()
        return result
    }

    private fun HomeCardsResult.keys() = visible.map { it.key }
    private fun HomeCardsResult.hiddenKeys() = hidden.map { it.key }

    // ── snapshoty ─────────────────────────────────────────────────────────

    @Test
    fun `fresh — swiezy user, minimum kart`() {
        val r = report("home-fresh", HomeUiState(
            deloadCard = DeloadCardState.None,
            trainingPhase = phase(TrainingPhase.NO_DATA),
            trainingLoad = load(LoadZone.INSUFFICIENT)
        ))
        // świeży user: tylko Hero (brak planu), reszta ukryta/brak
        assertEquals(listOf("HERO"), r.keys())
        assertTrue("faza NO_DATA ukryta", "TRAINING_PHASE" in r.hiddenKeys())
        assertTrue("load INSUFFICIENT ukryty", "TRAINING_LOAD" in r.hiddenKeys())
    }

    @Test
    fun `healthy — aktywny user, karty metryk widoczne`() {
        val r = report("home-healthy", HomeUiState(
            deloadCard = DeloadCardState.None,
            todaysPlan = pl.filebit.gymtracker.data.entity.TrainingPlan(
                id = 1, name = "Plan", daysOfWeek = listOf(1, 3, 5)
            ),
            trainingPhase = phase(TrainingPhase.ACCUMULATION),
            trainingLoad = load(LoadZone.OPTIMAL),
            trainingReadiness = readiness(DataMaturity.MATURE)
        ))
        assertTrue("faza cyklu widoczna", "TRAINING_PHASE" in r.keys())
        assertTrue("obciążenie widoczne", "TRAINING_LOAD" in r.keys())
        assertTrue("gotowość widoczna", "TRAINING_READINESS" in r.keys())
        assertTrue("hero widoczne", "HERO" in r.keys())
    }

    @Test
    fun `overtraining — deload zalecany ukrywa duplikat fazy`() {
        val r = report("home-overtraining", HomeUiState(
            deloadCard = deloadSuggestion(),
            trainingPhase = phase(TrainingPhase.NEEDS_DELOAD),
            trainingLoad = load(LoadZone.OPTIMAL)
        ))
        // KLUCZOWA reguła v1.24.40 — bez duplikatu DELOAD + FAZA
        assertTrue("DELOAD ZALECANY widoczny", "DELOAD_SUGGESTION" in r.keys())
        assertFalse("FAZA CYKLU nie może dublować deloadu", "TRAINING_PHASE" in r.keys())
        assertTrue("faza ukryta z powodem duplikatu",
            r.hidden.any { it.key == "TRAINING_PHASE" && "duplikat" in it.reason })
    }

    @Test
    fun `injury — wykryto bol`() {
        val r = report("home-injury", HomeUiState(
            deloadCard = DeloadCardState.ActiveInjury(
                pl.filebit.gymtracker.util.ActiveInjuryRecommendation(
                    painArea = "LOWER_BACK",
                    occurrences = 1,
                    daysSinceLast = 1,
                    severity = pl.filebit.gymtracker.util.InjurySeverity.FLAG,
                    reason = "ból w ostatnim treningu"
                )
            )
        ))
        assertTrue("karta bólu widoczna", "ACTIVE_INJURY" in r.keys())
    }

    @Test
    fun `return after break — powrot po przerwie`() {
        val r = report("home-return", HomeUiState(
            deloadCard = DeloadCardState.ReturnAfterBreak(
                pl.filebit.gymtracker.util.ReturnAfterBreakRecommendation(
                    breakDays = 32,
                    severity = pl.filebit.gymtracker.util.ReturnSeverity.LONG_BREAK,
                    reason = "32 dni przerwy"
                )
            )
        ))
        assertTrue("karta powrotu widoczna", "RETURN_AFTER_BREAK" in r.keys())
    }

    @Test
    fun `dismissed — karty zamkniete przez usera sa ukryte`() {
        val r = report("home-dismissed", HomeUiState(
            deloadCard = DeloadCardState.None,
            trainingPhase = phase(TrainingPhase.ACCUMULATION),
            trainingLoad = load(LoadZone.OPTIMAL),
            trainingReadiness = readiness(DataMaturity.MATURE),
            dismissedCards = setOf(
                DismissedCardsPrefs.CardKeys.PHASE,
                DismissedCardsPrefs.CardKeys.LOAD,
                DismissedCardsPrefs.CardKeys.READINESS
            )
        ))
        assertEquals("wszystkie 3 metryki ukryte", listOf("HERO"), r.keys())
        assertEquals(3, r.hidden.size)
        assertTrue("ukryte z powodem 'user zamknął'",
            r.hidden.all { "zamkn" in it.reason })
    }
}
