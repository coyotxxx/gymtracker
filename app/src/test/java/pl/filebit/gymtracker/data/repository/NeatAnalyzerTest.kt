package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeatAnalyzerTest {

    private val analyzer = NeatAnalyzer()

    @Test
    fun `empty data returns EMPTY snapshot with baseline`() {
        val snap = analyzer.analyze(emptyList(), baselineStepsPerDay = 7000)
        assertEquals(7000, snap.baselineStepsPerDay)
        assertEquals(0, snap.avg14dSteps)
        assertFalse(snap.hasEnoughData)
        assertFalse(snap.significantStepsDrop)
    }

    @Test
    fun `under 5 days = not enough data`() {
        val snap = analyzer.analyze(listOf(8000, 7500, 7000), baselineStepsPerDay = 7000)
        assertFalse(snap.hasEnoughData)
    }

    @Test
    fun `consistent 8000 steps - no drop`() {
        val days30 = List(30) { 8000 }
        val snap = analyzer.analyze(days30, baselineStepsPerDay = 7000)
        assertTrue(snap.hasEnoughData)
        assertEquals(8000, snap.avg14dSteps)
        assertEquals(8000, snap.avg30dSteps)
        assertFalse(snap.significantStepsDrop)
    }

    @Test
    fun `30 percent drop in last 14 days = significantStepsDrop`() {
        // 14 ostatnich dni: 5000, 16 dni wcześniej: 9000
        // avg30 = (5000*14 + 9000*16) / 30 = 7133, avg14 = 5000
        // 5000 <= 7133 * 0.7 = 4993.1 → fałszywe
        // Test z większym spadkiem: 14×4500, 16×9000 → avg30 = 6900, avg14 = 4500
        // 4500 <= 6900*0.7 = 4830 → TRUE
        val recent = List(14) { 4500 } + List(16) { 9000 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 7000)
        assertTrue("Should detect significant drop", snap.significantStepsDrop)
    }

    @Test
    fun `slight drop (10 percent) does NOT trigger`() {
        val recent = List(14) { 7200 } + List(16) { 8000 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 7000)
        assertFalse(snap.significantStepsDrop)
    }

    @Test
    fun `belowBaseline when avg14 below 0_7 of baseline`() {
        val recent = List(14) { 4500 } + List(16) { 5000 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 7000)
        assertTrue(snap.belowBaseline) // 4500 <= 7000*0.7 = 4900
    }

    @Test
    fun `above baseline = no flag`() {
        val recent = List(14) { 8000 } + List(16) { 8500 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 7000)
        assertFalse(snap.belowBaseline)
    }

    @Test
    fun `avg14d takes first 14 entries (most recent if sorted DESC)`() {
        // recent30dSteps[0..13] = 6000, [14..29] = 10000
        val recent = List(14) { 6000 } + List(16) { 10000 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 7000)
        assertEquals(6000, snap.avg14dSteps)
    }

    @Test
    fun `zero baseline does not trigger belowBaseline`() {
        val recent = List(20) { 3000 }
        val snap = analyzer.analyze(recent, baselineStepsPerDay = 0)
        assertFalse(snap.belowBaseline)
    }
}
