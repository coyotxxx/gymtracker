package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.RecoveryLog

class RecoveryAnalyzerTest {

    private val analyzer = RecoveryAnalyzer()

    private fun log(
        sleep: Double? = null,
        sleepQ: Int? = null,
        stress: Int? = null,
        hunger: Int? = null,
        energy: Int? = null,
        soreness: Int? = null,
        difficulty: Int? = null
    ) = RecoveryLog(
        dateMs = 0,
        sleepHours = sleep,
        sleepQuality = sleepQ,
        stressLevel = stress,
        hungerLevel = hunger,
        energyLevel = energy,
        sorenessLevel = soreness,
        difficultyAdherence = difficulty
    )

    @Test
    fun `empty list returns EMPTY snapshot`() {
        val snap = analyzer.analyze(emptyList())
        assertEquals(0, snap.sampleDays)
        assertFalse(snap.hasEnoughData)
        assertNull(snap.avgSleepHours)
    }

    @Test
    fun `1 day = not enough data`() {
        val snap = analyzer.analyze(listOf(log(sleep = 8.0)))
        assertFalse(snap.hasEnoughData)
    }

    @Test
    fun `3 days = enough data`() {
        val snap = analyzer.analyze(listOf(log(sleep = 8.0), log(sleep = 7.0), log(sleep = 6.0)))
        assertTrue(snap.hasEnoughData)
        assertEquals(7.0, snap.avgSleepHours!!, 0.01)
    }

    @Test
    fun `bad sleep when avg under 6h`() {
        val snap = analyzer.analyze(listOf(log(sleep = 5.0), log(sleep = 5.5), log(sleep = 5.0)))
        assertTrue(snap.badSleep)
    }

    @Test
    fun `bad sleep when sleepQuality avg under 2_5`() {
        val snap = analyzer.analyze(listOf(log(sleepQ = 2), log(sleepQ = 2), log(sleepQ = 2)))
        assertTrue(snap.badSleep)
    }

    @Test
    fun `good sleep above 7h not flagged`() {
        val snap = analyzer.analyze(listOf(log(sleep = 7.5), log(sleep = 8.0), log(sleep = 7.5)))
        assertFalse(snap.badSleep)
    }

    @Test
    fun `high stress when avg over 4`() {
        val snap = analyzer.analyze(listOf(log(stress = 4), log(stress = 5), log(stress = 4)))
        assertTrue(snap.highStress)
    }

    @Test
    fun `high hunger when avg over 4`() {
        val snap = analyzer.analyze(listOf(log(hunger = 4), log(hunger = 5), log(hunger = 4)))
        assertTrue(snap.highHunger)
    }

    @Test
    fun `low energy when avg under 2`() {
        val snap = analyzer.analyze(listOf(log(energy = 2), log(energy = 1), log(energy = 2)))
        assertTrue(snap.lowEnergy)
    }

    @Test
    fun `high soreness + low energy = DELOAD signal`() {
        val snap = analyzer.analyze(listOf(
            log(soreness = 4, energy = 2),
            log(soreness = 5, energy = 1),
            log(soreness = 4, energy = 2)
        ))
        assertTrue(snap.highSoreness)
        assertTrue(snap.lowEnergy)
    }

    @Test
    fun `high difficulty avg over 4`() {
        val snap = analyzer.analyze(listOf(log(difficulty = 4), log(difficulty = 5), log(difficulty = 4)))
        assertTrue(snap.highDifficulty)
    }

    @Test
    fun `partial data only counts available fields`() {
        val snap = analyzer.analyze(listOf(
            log(sleep = 8.0),                    // tylko sleep
            log(stress = 5),                     // tylko stres
            log(sleep = 7.0, stress = 4)         // oba
        ))
        assertEquals(7.5, snap.avgSleepHours!!, 0.01)
        assertEquals(4.5, snap.avgStress!!, 0.01)
        // Inne pola null
        assertNull(snap.avgHunger)
        assertNull(snap.avgEnergy)
    }

    @Test
    fun `mixed signals - bad sleep + high stress + high hunger`() {
        val snap = analyzer.analyze(listOf(
            log(sleep = 5.5, stress = 4, hunger = 5),
            log(sleep = 5.0, stress = 5, hunger = 4),
            log(sleep = 5.5, stress = 4, hunger = 5)
        ))
        assertTrue(snap.badSleep)
        assertTrue(snap.highStress)
        assertTrue(snap.highHunger)
    }

    @Test
    fun `clean week = no flags`() {
        val snap = analyzer.analyze(listOf(
            log(sleep = 8.0, stress = 2, hunger = 2, energy = 4, soreness = 2, difficulty = 2),
            log(sleep = 7.5, stress = 2, hunger = 2, energy = 4, soreness = 2, difficulty = 2),
            log(sleep = 8.0, stress = 2, hunger = 2, energy = 5, soreness = 1, difficulty = 1)
        ))
        assertFalse(snap.badSleep)
        assertFalse(snap.highStress)
        assertFalse(snap.highHunger)
        assertFalse(snap.lowEnergy)
        assertFalse(snap.highSoreness)
        assertFalse(snap.highDifficulty)
    }
}
