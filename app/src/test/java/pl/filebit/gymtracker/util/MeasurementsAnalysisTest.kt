package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement

class MeasurementsAnalysisTest {

    private val now = 1_750_000_000_000L  // ustalone "teraz" dla testów (2025-06-15ish)
    private val day = 86_400_000L

    private fun m(daysAgo: Int, weight: Double? = null, waist: Double? = null, bf: Double? = null) =
        BodyMeasurement(
            id = daysAgo.toLong(),
            date = now - daysAgo * day,
            weightKg = weight,
            waistCm = waist,
            bodyFatPercent = bf
        )

    // ============================================================
    // filterMeasurementsByRange
    // ============================================================

    @Test
    fun `filter null range returns all sorted ascending`() {
        val all = listOf(m(30, 80.0), m(10, 78.0), m(20, 79.0))
        val r = filterMeasurementsByRange(all, null, now)
        assertEquals(3, r.size)
        assertEquals(80.0, r[0].weightKg!!, 0.001)  // najstarszy pierwszy
        assertEquals(78.0, r[2].weightKg!!, 0.001)
    }

    @Test
    fun `filter 30 days excludes older`() {
        val all = listOf(m(45, 82.0), m(20, 80.0), m(5, 78.0))
        val r = filterMeasurementsByRange(all, 30, now)
        assertEquals(2, r.size)
        assertEquals(80.0, r[0].weightKg!!, 0.001)
    }

    @Test
    fun `filter 90 days includes recent`() {
        val all = listOf(m(120, 82.0), m(60, 80.0), m(15, 78.0))
        val r = filterMeasurementsByRange(all, 90, now)
        assertEquals(2, r.size)
    }

    @Test
    fun `filter empty returns empty`() {
        assertEquals(0, filterMeasurementsByRange(emptyList(), 30, now).size)
    }

    // ============================================================
    // computeTrend
    // ============================================================

    @Test
    fun `trend with 2 points returns delta`() {
        val all = listOf(m(28, weight = 80.0), m(0, weight = 78.0))
        val t = computeTrend(all, { it.weightKg }, rangeDays = 30, now = now)!!
        assertEquals(80.0, t.first, 0.001)
        assertEquals(78.0, t.last, 0.001)
        assertEquals(-2.0, t.delta, 0.001)
        assertEquals(28, t.daysSpan)
        assertEquals(-0.5, t.deltaPerWeek, 0.001)  // -2 / (28/7) = -0.5
    }

    @Test
    fun `trend with 1 point returns null`() {
        val all = listOf(m(10, weight = 80.0))
        val t = computeTrend(all, { it.weightKg }, rangeDays = 30, now = now)
        assertNull(t)
    }

    @Test
    fun `trend ignores measurements outside range`() {
        val all = listOf(m(60, 82.0), m(28, 80.0), m(0, 78.0))
        val t = computeTrend(all, { it.weightKg }, rangeDays = 30, now = now)!!
        assertEquals(80.0, t.first, 0.001)  // 28 dni temu, nie 60
        assertEquals(78.0, t.last, 0.001)
    }

    @Test
    fun `trend skips null values`() {
        val all = listOf(
            m(30, weight = 80.0, bf = null),
            m(15, weight = null, bf = 18.0),
            m(0, weight = 78.0, bf = null)
        )
        val t = computeTrend(all, { it.weightKg }, rangeDays = 30, now = now)!!
        assertEquals(80.0, t.first, 0.001)
        assertEquals(78.0, t.last, 0.001)  // pomija null pomiar
    }

    @Test
    fun `trend BF separate from weight`() {
        val all = listOf(m(28, bf = 20.0), m(0, bf = 18.0))
        val t = computeTrend(all, { it.bodyFatPercent }, rangeDays = 30, now = now)!!
        assertEquals(-2.0, t.delta, 0.001)
    }

    // ============================================================
    // progressToGoal
    // ============================================================

    @Test
    fun `progress CUT 50 percent done`() {
        val pct = progressToGoal(startWeight = 82.0, current = 78.5, target = 75.0)
        assertEquals(50.0, pct, 0.1)
    }

    @Test
    fun `progress BULK 40 percent done`() {
        val pct = progressToGoal(startWeight = 70.0, current = 74.0, target = 80.0)
        assertEquals(40.0, pct, 0.1)
    }

    @Test
    fun `progress already at goal returns 100`() {
        val pct = progressToGoal(startWeight = 82.0, current = 75.0, target = 75.0)
        assertEquals(100.0, pct, 0.1)
    }

    @Test
    fun `progress not started returns 0`() {
        val pct = progressToGoal(startWeight = 82.0, current = 82.0, target = 75.0)
        assertEquals(0.0, pct, 0.1)
    }

    @Test
    fun `progress overshoot clamps to 100`() {
        val pct = progressToGoal(startWeight = 82.0, current = 73.0, target = 75.0)
        assertEquals(100.0, pct, 0.1)
    }

    @Test
    fun `progress wrong direction clamps to 0`() {
        // CUT ale przybrałeś zamiast schudnąć
        val pct = progressToGoal(startWeight = 82.0, current = 84.0, target = 75.0)
        assertEquals(0.0, pct, 0.1)
    }

    // ============================================================
    // estimatedWeeksToGoal
    // ============================================================

    @Test
    fun `eta CUT with proper trend`() {
        // chudniesz 0.5 kg/tyg, zostało 3.5 kg → 7 tyg
        val w = estimatedWeeksToGoal(78.5, 75.0, -0.5)!!
        assertEquals(7, w)
    }

    @Test
    fun `eta BULK with proper trend`() {
        // przybierasz 0.25 kg/tyg, zostało 6 kg → 24 tyg
        val w = estimatedWeeksToGoal(74.0, 80.0, 0.25)!!
        assertEquals(24, w)
    }

    @Test
    fun `eta zero remaining`() {
        val w = estimatedWeeksToGoal(75.0, 75.0, -0.5)!!
        assertEquals(0, w)
    }

    @Test
    fun `eta wrong direction returns null`() {
        // CUT ale przybierasz
        val w = estimatedWeeksToGoal(78.5, 75.0, 0.3)
        assertNull(w)
    }

    @Test
    fun `eta unreachable returns null`() {
        // 100 kg do zrzucenia przy 0.001 kg/tyg = 100000 tyg
        val w = estimatedWeeksToGoal(180.0, 80.0, -0.001)
        assertNull(w)
    }

    @Test
    fun `eta rounds up`() {
        // 3 kg do zrzucenia, 0.4 kg/tyg = 7.5 tyg → 8
        val w = estimatedWeeksToGoal(78.0, 75.0, -0.4)!!
        assertEquals(8, w)
    }

    // ============================================================
    // chartYStep
    // ============================================================

    @Test
    fun `chart step 1kg for small range`() {
        assertEquals(1.0, chartYStep(78.0, 81.0), 0.001)
    }

    @Test
    fun `chart step 2kg for medium range`() {
        assertEquals(2.0, chartYStep(75.0, 84.0), 0.001)
    }

    @Test
    fun `chart step 5kg for larger range`() {
        assertEquals(5.0, chartYStep(70.0, 90.0), 0.001)
    }

    @Test
    fun `chart step 10kg for huge range`() {
        assertEquals(10.0, chartYStep(60.0, 110.0), 0.001)
    }
}
