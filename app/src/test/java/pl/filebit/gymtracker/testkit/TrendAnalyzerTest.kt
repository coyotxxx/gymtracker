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
}
