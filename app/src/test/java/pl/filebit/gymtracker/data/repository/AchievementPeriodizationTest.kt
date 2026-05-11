package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.18.0 — testy 4 nowych odznak PERIODIZATION.
 *
 * AchievementDefinitions.all() to pure function — daje deterministyczny output
 * dla danego input.
 */
class AchievementPeriodizationTest {

    private fun definitions(
        cycles: Long = 0,
        deloads: Long = 0,
        phases: Long = 0
    ): List<AchievementDef> = AchievementDefinitions.all(
        workoutsCount = 100,
        totalVolume = 50000,
        streakBestWeeks = 5,
        distinctExercises = 20,
        musclesTrained = 10,
        customExercises = 0,
        prCount = 5,
        bodyMeasurementsCount = 10,
        achievedGoalsCount = 2,
        bodyweightDelta = -3.0,
        weightGoalType = "MAINTAIN",
        chestDelta = 0.0,
        armDelta = 0.0,
        thighDelta = 0.0,
        waistDrop = 0.0,
        bodyFatDrop = 0.0,
        benchMaxKg = 100.0,
        squatMaxKg = 140.0,
        deadliftMaxKg = 180.0,
        ohpMaxKg = 60.0,
        bodyweightKg = 80.0,
        completedCycles = cycles,
        deloadsExecuted = deloads,
        phasesCompleted = phases
    )

    @Test
    fun `bez cykli — odznaki PERIODIZATION na 0 progress`() {
        val defs = definitions()
        val periodization = defs.filter { it.category == AchievementCategory.PERIODIZATION }
        assertEquals("4 odznaki PERIODIZATION", 4, periodization.size)
        assertTrue("wszystkie na 0", periodization.all { it.currentValue == 0L })
    }

    @Test
    fun `1 cykl ukończony — cycle_complete_1 unlocked`() {
        val defs = definitions(cycles = 1)
        val def = defs.first { it.code == "cycle_complete_1" }
        assertEquals(1L, def.targetValue)
        assertEquals(1L, def.currentValue)
        assertTrue("currentValue >= targetValue", def.currentValue >= def.targetValue)
    }

    @Test
    fun `5 cykli — cycle_complete_5 + cycle_complete_1 unlocked`() {
        val defs = definitions(cycles = 5)
        val c1 = defs.first { it.code == "cycle_complete_1" }
        val c5 = defs.first { it.code == "cycle_complete_5" }
        assertTrue(c1.currentValue >= c1.targetValue)
        assertTrue(c5.currentValue >= c5.targetValue)
    }

    @Test
    fun `5 deloadów — deload_master_5 unlocked`() {
        val defs = definitions(deloads = 5)
        val def = defs.first { it.code == "deload_master_5" }
        assertEquals(5L, def.targetValue)
        assertEquals(5L, def.currentValue)
        assertTrue(def.currentValue >= def.targetValue)
    }

    @Test
    fun `4 phases distinct — phases_all_4 unlocked`() {
        val defs = definitions(phases = 4)
        val def = defs.first { it.code == "phases_all_4" }
        assertEquals(4L, def.targetValue)
        assertEquals(4L, def.currentValue)
    }

    @Test
    fun `3 phases — phases_all_4 still locked`() {
        val defs = definitions(phases = 3)
        val def = defs.first { it.code == "phases_all_4" }
        assertTrue("3 < 4 — jeszcze nie unlocked", def.currentValue < def.targetValue)
    }

    @Test
    fun `tier hierarchy — bronze→silver→gold`() {
        val defs = definitions(cycles = 5, deloads = 5, phases = 4)
        val cycle1 = defs.first { it.code == "cycle_complete_1" }
        val cycle5 = defs.first { it.code == "cycle_complete_5" }
        val deload = defs.first { it.code == "deload_master_5" }
        val phases = defs.first { it.code == "phases_all_4" }

        assertEquals(AchievementLevel.BRONZE, cycle1.level)
        assertEquals(AchievementLevel.SILVER, cycle5.level)
        assertEquals(AchievementLevel.SILVER, deload.level)
        assertEquals(AchievementLevel.GOLD, phases.level)
    }
}
