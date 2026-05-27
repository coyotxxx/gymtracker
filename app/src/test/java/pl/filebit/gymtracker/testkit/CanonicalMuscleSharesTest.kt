package pl.filebit.gymtracker.testkit

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.repository.muscleSharesFromCanonical

/**
 * v2.2.0 — testy parsera muscleIntensityJson z canonical.
 * P10=1.0, S7=0.7, T4=0.4 (kwadratowo: base × num/10). Po zmapowaniu na enum
 * MuscleGroup udzialy normalizowane sum-to-1.
 */
class CanonicalMuscleSharesTest {

    @Test
    fun `null or empty json returns empty map`() {
        assertEquals(emptyMap<MuscleGroup, Double>(), muscleSharesFromCanonical(null))
        assertEquals(emptyMap<MuscleGroup, Double>(), muscleSharesFromCanonical(""))
        assertEquals(emptyMap<MuscleGroup, Double>(), muscleSharesFromCanonical("{}"))
    }

    @Test
    fun `single primary muscle gets 100 percent`() {
        val json = """{"pectoralis_major":"P10"}"""
        val result = muscleSharesFromCanonical(json)
        assertEquals(1, result.size)
        assertEquals(1.0, result[MuscleGroup.CHEST]!!, 0.001)
    }

    @Test
    fun `primary plus secondary distributes proportionally`() {
        // P10 = 1.0, S7 = 0.6 * 0.7 = 0.42 → sum 1.42 → 0.704 / 0.296
        val json = """{"pectoralis_major":"P10","triceps_brachii":"S7"}"""
        val result = muscleSharesFromCanonical(json)
        assertEquals(2, result.size)
        val chest = result[MuscleGroup.CHEST]!!
        val triceps = result[MuscleGroup.TRICEPS]!!
        assertEquals(1.0, chest + triceps, 0.01)
        assertTrue("CHEST should dominate", chest > triceps)
    }

    @Test
    fun `multiple muscles map to same group are summed`() {
        // dwa mięśnie pleców (latissimus + trapezius) -> oba do BACK, sumowane
        val json = """{"latissimus_dorsi":"P10","trapezius":"S5","biceps_brachii":"S3"}"""
        val result = muscleSharesFromCanonical(json)
        // BACK dostaje sumę z 2 mięśni, BICEPS jeden
        assertTrue(result.containsKey(MuscleGroup.BACK))
        assertTrue(result.containsKey(MuscleGroup.BICEPS))
        assertTrue("BACK > BICEPS bo P + S", result[MuscleGroup.BACK]!! > result[MuscleGroup.BICEPS]!!)
        // Sum is 1.0 (normalized)
        assertEquals(1.0, result.values.sum(), 0.001)
    }

    @Test
    fun `core muscles map to CORE group`() {
        val json = """{"rectus_abdominis":"P10","obliques":"S6","transverse_abdominis":"T4"}"""
        val result = muscleSharesFromCanonical(json)
        // wszystkie -> CORE (single group)
        assertEquals(1, result.size)
        assertEquals(1.0, result[MuscleGroup.CORE]!!, 0.001)
    }

    @Test
    fun `unknown muscle names are skipped`() {
        val json = """{"unknownmuscle":"P10","pectoralis_major":"P10"}"""
        val result = muscleSharesFromCanonical(json)
        assertEquals(1, result.size)
        assertEquals(1.0, result[MuscleGroup.CHEST]!!, 0.001)
    }

    @Test
    fun `malformed json returns empty map`() {
        assertEquals(emptyMap<MuscleGroup, Double>(), muscleSharesFromCanonical("{not json"))
        assertEquals(emptyMap<MuscleGroup, Double>(), muscleSharesFromCanonical("[]"))
    }

    @Test
    fun `intensity grades P S T have descending weights`() {
        val jsonP = """{"pectoralis_major":"P10","triceps_brachii":"P10"}"""
        val jsonS = """{"pectoralis_major":"P10","triceps_brachii":"S10"}"""
        val jsonT = """{"pectoralis_major":"P10","triceps_brachii":"T10"}"""

        val tricepsP = muscleSharesFromCanonical(jsonP)[MuscleGroup.TRICEPS]!!
        val tricepsS = muscleSharesFromCanonical(jsonS)[MuscleGroup.TRICEPS]!!
        val tricepsT = muscleSharesFromCanonical(jsonT)[MuscleGroup.TRICEPS]!!

        assertTrue("P > S", tricepsP > tricepsS)
        assertTrue("S > T", tricepsS > tricepsT)
    }
}
