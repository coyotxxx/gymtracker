package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27 — FAZA 0.2 — sanity Trace Reporter.
 * Czysty Kotlin (bez Robolectric) — sprawdza że raport składa się poprawnie.
 */
class TraceReportTest {

    @Test
    fun `raport zawiera wszystkie sekcje i werdykty`() {
        val report = TraceReport("test-sanity")
            .section("DANE (pokrycie)")
            .kv("treningi", "60")
            .coverage("HRV", false)
            .coverage("waga", true, "45 pomiarów")
            .section("DETEKTORY")
            .verdict("DeloadDetector", "REFEED_RECOMMENDED", "avgRPE 14d=10.0")
            .section("CO WIDZI USER")
            .shows("REFEED ZALECANY", "Treningi intensywne")
            .hidden("TrainingPhaseCard", "DeloadSuggestion ma priorytet")
            .section("OCENA")
            .note("brak HRV — recovery tylko z RPE")
            .render()

        assertTrue("brak nagłówka scenariusza", report.contains("SCENARIUSZ: test-sanity"))
        assertTrue("brak sekcji DANE", report.contains("DANE (pokrycie)"))
        assertTrue("brak coverage BRAK", report.contains("[BRAK] HRV"))
        assertTrue("brak coverage OK", report.contains("[OK] waga (45 pomiarów)"))
        assertTrue("brak werdyktu", report.contains("DeloadDetector -> REFEED_RECOMMENDED"))
        assertTrue("brak uzasadnienia", report.contains("powod: avgRPE 14d=10.0"))
        assertTrue("brak widocznej karty", report.contains("[WIDOCZNE] REFEED ZALECANY"))
        assertTrue("brak ukrytej karty", report.contains("[UKRYTE]   TrainingPhaseCard"))
        assertTrue("brak oceny", report.contains("(!) brak HRV"))
    }
}
