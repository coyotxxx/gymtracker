package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationCalculatorTest {

    private val calc = HydrationCalculator()

    @Test
    fun `base 80kg = 2400 ml`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 100.0, usesCreatine = false)
        assertEquals(2400, goal.baseMl)
        assertEquals(2400, goal.totalMl)
    }

    @Test
    fun `with training adds 500ml`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = true, proteinGramsToday = 100.0, usesCreatine = false)
        assertEquals(500, goal.workoutBonusMl)
        assertEquals(2900, goal.totalMl)
    }

    @Test
    fun `high protein over 2g per kg adds 300ml`() {
        // 80kg × 2g = 160g, więc 200g = 2.5g/kg → bonus
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 200.0, usesCreatine = false)
        assertEquals(300, goal.proteinBonusMl)
        assertEquals(2700, goal.totalMl)
    }

    @Test
    fun `protein at 2g per kg exactly = no bonus (must be over)`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 160.0, usesCreatine = false)
        assertEquals(0, goal.proteinBonusMl)
    }

    @Test
    fun `creatine adds 500ml`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 100.0, usesCreatine = true)
        assertEquals(500, goal.creatineBonusMl)
        assertEquals(2900, goal.totalMl)
    }

    @Test
    fun `temperature over 25 adds 500ml`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 100.0, usesCreatine = false, temperatureC = 30.0)
        assertEquals(500, goal.temperatureBonusMl)
        assertEquals(2900, goal.totalMl)
    }

    @Test
    fun `temperature at 25 = no bonus (must be over)`() {
        val goal = calc.computeTarget(weightKg = 80.0, hadTrainingToday = false, proteinGramsToday = 100.0, usesCreatine = false, temperatureC = 25.0)
        assertEquals(0, goal.temperatureBonusMl)
    }

    @Test
    fun `everything stacked = max bonuses`() {
        val goal = calc.computeTarget(
            weightKg = 80.0,
            hadTrainingToday = true,
            proteinGramsToday = 200.0,
            usesCreatine = true,
            temperatureC = 30.0
        )
        // Base 2400 + 500 + 300 + 500 + 500 = 4200
        assertEquals(4200, goal.totalMl)
    }

    @Test
    fun `lighter person = lower base`() {
        val goal = calc.computeTarget(weightKg = 60.0, hadTrainingToday = false, proteinGramsToday = 100.0, usesCreatine = false)
        assertEquals(1800, goal.baseMl)
        assertEquals(1800, goal.totalMl)
    }

    @Test
    fun `adherence pct calculation`() {
        assertEquals(50, calc.adherencePct(consumedMl = 1200, goalMl = 2400))
        assertEquals(100, calc.adherencePct(consumedMl = 2400, goalMl = 2400))
        assertEquals(125, calc.adherencePct(consumedMl = 3000, goalMl = 2400))
        assertEquals(0, calc.adherencePct(consumedMl = 1000, goalMl = 0))
    }

    @Test
    fun `explanation contains all active components`() {
        val goal = calc.computeTarget(
            weightKg = 80.0,
            hadTrainingToday = true,
            proteinGramsToday = 200.0,
            usesCreatine = true,
            temperatureC = 30.0
        )
        assertTrue(goal.explanation.contains("Baza"))
        assertTrue(goal.explanation.contains("trening"))
        assertTrue(goal.explanation.contains("białko"))
        assertTrue(goal.explanation.contains("kreatyna"))
        assertTrue(goal.explanation.contains("temp"))
    }
}
