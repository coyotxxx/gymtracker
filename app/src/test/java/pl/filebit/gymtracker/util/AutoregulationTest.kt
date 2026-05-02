package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoregulationTest {

    @Test
    fun `RPE under 7 with hypertrophy delta adds 1_25 kg`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 6.33, delta = 1.25)
        assertEquals(61.25, r.weightKg, 0.001)
        assertEquals(8, r.reps)
    }

    @Test
    fun `RPE 7 to 9 holds weight and adds 1 rep`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 7.67, delta = 1.25)
        assertEquals(60.0, r.weightKg, 0.001)
        assertEquals(9, r.reps)
    }

    @Test
    fun `RPE 9 to 9_5 holds without rep increase`() {
        val r = computeProgression(refWeight = 80.0, refReps = 6, avgRpe = 9.33, delta = 2.5)
        assertEquals(80.0, r.weightKg, 0.001)
        assertEquals(6, r.reps)
    }

    @Test
    fun `RPE 10 triggers deload minus 5 percent`() {
        // 60 * 0.95 = 57.0 (już zaokrąglone do 0.25)
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 10.0, delta = 1.25)
        assertEquals(57.0, r.weightKg, 0.001)
        assertEquals(8, r.reps)
        assert(r.rationale.contains("deload")) { "Should mention deload: ${r.rationale}" }
    }

    @Test
    fun `RPE 9_8 still triggers deload`() {
        // 80 * 0.95 = 76.0
        val r = computeProgression(refWeight = 80.0, refReps = 6, avgRpe = 9.8, delta = 2.5)
        assertEquals(76.0, r.weightKg, 0.001)
        assertEquals(6, r.reps)
    }

    @Test
    fun `RPE exactly 9_5 still holds (boundary)`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 9.5, delta = 1.25)
        assertEquals(60.0, r.weightKg, 0.001)
        assertEquals(8, r.reps)
    }

    @Test
    fun `null RPE applies blind delta`() {
        val r = computeProgression(refWeight = 50.0, refReps = 8, avgRpe = null, delta = 1.25)
        assertEquals(51.25, r.weightKg, 0.001)
        assertEquals(8, r.reps)
    }

    @Test
    fun `STRENGTH delta is 2_5`() {
        val r = computeProgression(refWeight = 80.0, refReps = 5, avgRpe = 6.67, delta = 2.5)
        assertEquals(82.5, r.weightKg, 0.001)
        assertEquals(5, r.reps)
    }

    @Test
    fun `bodyweight RPE 8 holds weight zero plus 1 rep`() {
        val r = computeProgression(refWeight = 0.0, refReps = 15, avgRpe = 8.0, delta = 1.25)
        assertEquals(0.0, r.weightKg, 0.001)
        assertEquals(16, r.reps)
    }

    @Test
    fun `boundary RPE exactly 7_0 still triggers plus weight`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 7.0, delta = 1.25)
        assertEquals(61.25, r.weightKg, 0.001)
        assertEquals(8, r.reps)
    }

    @Test
    fun `boundary RPE exactly 9_0 holds weight plus 1 rep`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 9.0, delta = 1.25)
        assertEquals(60.0, r.weightKg, 0.001)
        assertEquals(9, r.reps)
    }

    @Test
    fun `weight rounds to 0_25 kg precision`() {
        // 60.1 + 1.25 = 61.35 → round to 0.25 = 61.25
        val r = computeProgression(refWeight = 60.1, refReps = 8, avgRpe = null, delta = 1.25)
        assertEquals(61.25, r.weightKg, 0.001)
    }

    @Test
    fun `rationale mentions RPE when present`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = 7.5, delta = 1.25)
        assert(r.rationale.contains("RPE")) { "Rationale should mention RPE: ${r.rationale}" }
    }

    @Test
    fun `rationale mentions no RPE when null`() {
        val r = computeProgression(refWeight = 60.0, refReps = 8, avgRpe = null, delta = 1.25)
        assert(r.rationale.contains("Brak RPE")) { "Rationale should mention 'Brak RPE': ${r.rationale}" }
    }
}
