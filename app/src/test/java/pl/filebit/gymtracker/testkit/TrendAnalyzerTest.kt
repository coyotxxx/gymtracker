package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.util.TrendAnalyzer
import pl.filebit.gymtracker.util.TrendDirection

/**
 * v1.27 — FAZA 2.7 — testy TrendAnalyzer (analiza trendu wagi).
 *
 * Pure function: List<BodyMeasurement> → WeightTrend. Zasila silnik korekt
 * kcal (CalorieAdjustmentEngine). Slope = średnia ostatnich 7 dni − średnia
 * poprzednich 7 dni (kg/tydz). Test pokrywa kierunki + flagi tempa.
 */
class TrendAnalyzerTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun m(daysAgo: Int, weight: Double) =
        BodyMeasurement(date = now - daysAgo * day, weightKg = weight)

    /** 3 pomiary w poprzednim tygodniu + 3 w ostatnim. */
    private fun series(prevWeek: Double, recentWeek: Double) = listOf(
        m(12, prevWeek), m(10, prevWeek), m(8, prevWeek),
        m(5, recentWeek), m(3, recentWeek), m(1, recentWeek)
    )

    private fun report(name: String, t: pl.filebit.gymtracker.util.WeightTrend) {
        TraceReport("trend-$name")
            .section("TREND WAGI")
            .verdict("direction", t.direction.name, "slope=${t.slopeKgPerWeek} kg/tydz")
            .kv("flagi", "stagnacja=${t.isStagnationLikely}, " +
                "szybki spadek=${t.isFastLoss}, szybki wzrost=${t.isFastGain}")
            .emit()
    }

    @Test
    fun `mniej niz 3 pomiary - NO_DATA`() {
        val t = TrendAnalyzer.analyze(listOf(m(2, 80.0), m(1, 79.0)), nowMs = now)
        assertEquals(TrendDirection.INSUFFICIENT_DATA, t.direction)
        assertFalse("za mało danych", t.hasEnoughData)
    }

    @Test
    fun `waga spada szybko - FALLING i isFastLoss`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 81.0, recentWeek = 79.0), nowMs = now)
        report("fast-loss", t)
        assertEquals("slope −2 kg/tydz → FALLING", TrendDirection.FALLING, t.direction)
        assertTrue("−2 kg/tydz < −1.5 → szybki spadek", t.isFastLoss)
    }

    @Test
    fun `waga stoi - FLAT i isStagnationLikely`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 80.0, recentWeek = 80.0), nowMs = now)
        report("stagnation", t)
        assertEquals("brak zmiany → FLAT", TrendDirection.FLAT, t.direction)
        assertTrue("|slope| < 0.1 → stagnacja", t.isStagnationLikely)
        assertFalse("stagnacja to nie szybki spadek", t.isFastLoss)
    }

    @Test
    fun `waga rosnie szybko - RISING i isFastGain`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 80.0, recentWeek = 80.7), nowMs = now)
        report("fast-gain", t)
        assertEquals("slope +0.7 kg/tydz → RISING", TrendDirection.RISING, t.direction)
        assertTrue("+0.7 kg/tydz > 0.5 → szybki wzrost", t.isFastGain)
    }

    /**
     * v2.39.0 REGRESJA (bug zgłoszony przez Macieja): RZADKIE pomiary (1 w ostatnim tygodniu)
     * NIE mogą dawać fałszywego "fast loss". Stara metoda 'recent.avg − prev.avg' dawała −1.575
     * (→ błędny INCREASE_KCAL), regresja liniowa daje ~−1.0 (realne). Dane = realny przypadek.
     */
    /**
     * v2.62.0 REGRESJA (sweep 37 scenariuszy): RÓWNE, gładkie chudnięcie (0.5 kg/tydz bez
     * dziennych skoków ≥0.2 kg) NIE może być „early plateau". Stara logika (tylko max-min<0.5)
     * myliła to z plateau i silnik niepotrzebnie tnął kcal. Fix: wymagamy też slope > -0.2.
     */
    @Test
    fun `rowne gladkie chudniecie NIE jest early plateau`() {
        val data = (0..14).map { i -> m(28 - i * 2, 85.0 - i * (2.0 / 14)) } // 85→83 przez 28 dni
        val t = TrendAnalyzer.analyze(data, nowMs = now)
        report("steady-loss-not-plateau", t)
        assertEquals("realnie spada", TrendDirection.FALLING, t.direction)
        assertFalse("równe chudnięcie ~0.5 kg/tydz to NIE plateau", t.isEarlyPlateau)
    }

    @Test
    fun `rzadkie pomiary nie daja falszywego fast loss`() {
        val data = listOf(
            m(31, 87.0), m(29, 87.0), m(23, 86.6), m(20, 86.45),
            m(14, 86.35), m(10, 85.3), m(0, 84.25)  // tylko 1 pomiar w ostatnim tygodniu
        )
        val t = TrendAnalyzer.analyze(data, nowMs = now)
        report("sparse-no-false-fastloss", t)
        assertFalse("regresja ~−1.0 kg/tydz NIE jest fast loss (<−1.5)", t.isFastLoss)
        assertEquals("kierunek FALLING (waga realnie spada)", TrendDirection.FALLING, t.direction)
        assertTrue("slope w realnym przedziale (−1.3..−0.7)",
            t.slopeKgPerWeek!! in -1.3..-0.7)
    }
}
