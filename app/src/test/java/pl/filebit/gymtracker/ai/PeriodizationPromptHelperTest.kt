package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.repository.TransitionProposal

/**
 * Test PeriodizationPromptHelper (v1.15.0 — top-level pure function).
 *
 * Sprawdza że sekcja promptu wstrzykuje wszystkie potrzebne info dla AI:
 *  - aktualny mesocykl (faza, dni, daty)
 *  - propozycja algorytmu (recommendedNext, confidence, reasoning)
 *  - stagnacja (opcjonalna)
 *  - instrukcje "Twoja rola" dla AI
 */
class PeriodizationPromptHelperTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun mesocycle(
        phase: MesocyclePhase,
        weekInPhase: Int = 2,
        plannedEndDaysFromNow: Int = 7
    ) = TrainingMesocycle(
        id = 1L,
        startDateMs = now - 14L * day,
        plannedEndDateMs = now + plannedEndDaysFromNow * day,
        phase = phase,
        weekInPhase = weekInPhase,
        phaseLengthWeeks = 3,
        status = MesocycleStatus.ACTIVE,
        notes = "test notatka"
    )

    private fun proposal(from: MesocyclePhase, to: MesocyclePhase, confidence: Double = 0.75) =
        TransitionProposal(
            fromPhase = from,
            recommendedNext = to,
            plannedStartDate = now,
            plannedDurationWeeks = 3,
            reasoning = "Test reasoning $from to $to",
            confidence = confidence
        )

    @Test
    fun `null meso null proposal - pusta sekcja`() {
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = null,
            algorithmProposal = null,
            nowMs = now
        )
        assertTrue("Pusta sekcja gdy brak danych", section.isEmpty())
    }

    @Test
    fun `tylko meso - sekcja zawiera fazę i dni`() {
        val meso = mesocycle(MesocyclePhase.ACCUMULATION, weekInPhase = 2)
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = meso,
            algorithmProposal = null,
            nowMs = now
        )
        assertTrue("Header obecny", section.contains("PERIODYZACJA TRENINGOWA"))
        assertTrue("Faza akumulacji", section.contains("Akumulacja"))
        assertTrue("Tydzień widoczny", section.contains("tydzień 2/3"))
        assertTrue("Target RPE widoczny", section.contains("RPE"))
        assertTrue("Notatki widoczne", section.contains("test notatka"))
    }

    @Test
    fun `meso + proposal - sekcja zawiera Twoja rola dla AI`() {
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = mesocycle(MesocyclePhase.INTENSIFICATION),
            algorithmProposal = proposal(MesocyclePhase.INTENSIFICATION, MesocyclePhase.DELOAD),
            nowMs = now
        )
        assertTrue("Header proposal", section.contains("Algorithm proposal"))
        assertTrue("Twoja rola dla AI", section.contains("Twoja rola"))
        assertTrue("Tool name", section.contains("propose_periodization_action"))
        assertTrue("Akcje wymienione", section.contains("Zaakceptować") || section.contains("Przesunąć"))
    }

    @Test
    fun `stagnacja 30+ procent - dodatkowa adnotacja`() {
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = mesocycle(MesocyclePhase.ACCUMULATION),
            algorithmProposal = proposal(MesocyclePhase.ACCUMULATION, MesocyclePhase.DELOAD),
            stagnatingExercisesCount = 4,
            totalAnalyzedExercises = 10,  // 40%
            nowMs = now
        )
        assertTrue("Sygnał stagnacji", section.contains("stagnacji"))
        assertTrue("Procent widoczny", section.contains("(40%)"))
        assertTrue("Próg przekroczony", section.contains("30%") && section.contains("przekroczony"))
    }

    @Test
    fun `stagnacja 20% - brak adnotacji o progu`() {
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = mesocycle(MesocyclePhase.ACCUMULATION),
            algorithmProposal = proposal(MesocyclePhase.ACCUMULATION, MesocyclePhase.INTENSIFICATION),
            stagnatingExercisesCount = 2,
            totalAnalyzedExercises = 10,  // 20%
            nowMs = now
        )
        assertTrue("Sygnał stagnacji wciąż widoczny", section.contains("stagnuje (20%)"))
        assertFalse("Próg NIE przekroczony", section.contains("przekroczony"))
    }

    @Test
    fun `confidence widoczna jako procent`() {
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = mesocycle(MesocyclePhase.DELOAD),
            algorithmProposal = proposal(MesocyclePhase.DELOAD, MesocyclePhase.ACCUMULATION, confidence = 0.85),
            nowMs = now
        )
        assertTrue("Confidence widoczna", section.contains("85%"))
    }

    @Test
    fun `phaseLabelPl - wszystkie 5 faz po polsku`() {
        assertEquals("Akumulacja", PeriodizationPromptHelper.phaseLabelPl(MesocyclePhase.ACCUMULATION))
        assertEquals("Intensyfikacja", PeriodizationPromptHelper.phaseLabelPl(MesocyclePhase.INTENSIFICATION))
        assertEquals("Deload", PeriodizationPromptHelper.phaseLabelPl(MesocyclePhase.DELOAD))
        assertEquals("Peaking", PeriodizationPromptHelper.phaseLabelPl(MesocyclePhase.PEAKING))
        assertEquals("Recovery", PeriodizationPromptHelper.phaseLabelPl(MesocyclePhase.RECOVERY))
    }

    @Test
    fun `format daty PL DD_MM_YYYY`() {
        val meso = mesocycle(MesocyclePhase.ACCUMULATION)
        val section = PeriodizationPromptHelper.toPromptSection(
            meso = meso,
            algorithmProposal = null,
            nowMs = now
        )
        // now = 1_700_000_000_000L = ~2023-11-14
        // start (14 dni wcześniej) ~ 31.10
        // end (7 dni naprzód) ~ 21.11
        // Sprawdzam tylko format DD.MM.YYYY (regex)
        val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
        assertTrue("Format daty obecny", datePattern.containsMatchIn(section))
    }
}
