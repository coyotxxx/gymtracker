package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageControlTest {

    private val dc = DamageControl()

    @Test
    fun `unplanned kebab + 2 slots remaining redistributes`() {
        // Cel 2400 (4 sloty po 600 kcal), zjedzono 1500, zostały 2 sloty
        // budgetLeft = 900, /2 = 450 kcal/slot
        // normalPerSlot = 600, safetyFloorPerSlot = 600 × 0.7 = 420
        // max(450, 420) = 450
        val r = dc.recommend(
            unplannedKcal = 700,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1500,
            remainingSlots = 2,
            totalSlotsToday = 4
        )
        assertEquals(450, r.newKcalPerRemainingSlot)
        assertFalse(r.isExtremeOvereating)
    }

    @Test
    fun `pozostale sloty NIGDY ponizej safety floor (70 pct normy)`() {
        // Cel 2400 (4 sloty × 600), zjedzono 2400, zostały 2 sloty
        // budgetLeft = 0, /2 = 0
        // safety floor per slot = 600 × 0.7 = 420
        val r = dc.recommend(
            unplannedKcal = 1500,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 2400,
            remainingSlots = 2,
            totalSlotsToday = 4
        )
        assertEquals(420, r.newKcalPerRemainingSlot)
    }

    @Test
    fun `extreme overeating flagged when budget left under negative half goal`() {
        val r = dc.recommend(
            unplannedKcal = 2000,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 4000,
            remainingSlots = 1,
            totalSlotsToday = 4
        )
        assertTrue(r.isExtremeOvereating)
        assertTrue(r.message.contains("Spokojnie") || r.message.contains("NIE głoduj"))
    }

    @Test
    fun `no remaining slots produces 0 per slot but message gracefull`() {
        val r = dc.recommend(
            unplannedKcal = 500,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 2900,
            remainingSlots = 0,
            totalSlotsToday = 4
        )
        assertEquals(0, r.newKcalPerRemainingSlot)
        assertTrue(r.message.contains("Spokojnie") || r.message.contains("Przekroczenie"))
    }

    @Test
    fun `slight overeating - just redistribute calmly`() {
        // 2400 cel (4 sloty × 600), zjedzono 1900, zostały 1 slot
        // budgetLeft = 500, /1 = 500
        // safety floor = 420, max(500, 420) = 500
        val r = dc.recommend(
            unplannedKcal = 200,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1900,
            remainingSlots = 1,
            totalSlotsToday = 4
        )
        assertFalse(r.isExtremeOvereating)
        assertEquals(500, r.newKcalPerRemainingSlot)
    }

    @Test
    fun `safety floor is 85 percent of daily total`() {
        val r = dc.recommend(
            unplannedKcal = 0,
            dailyGoalKcal = 2000,
            alreadyConsumedKcalIncludingUnplanned = 1000,
            remainingSlots = 2,
            totalSlotsToday = 3
        )
        assertEquals(1700, r.safetyFloorKcal) // 2000 × 0.85
    }

    @Test
    fun `result contains all snapshot fields`() {
        val r = dc.recommend(
            unplannedKcal = 700,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1500,
            remainingSlots = 2,
            totalSlotsToday = 4
        )
        assertEquals(700, r.unplannedKcal)
        assertEquals(2400, r.originalDailyGoal)
        assertEquals(1500, r.alreadyConsumedKcal)
        assertEquals(2, r.remainingSlots)
    }

    @Test
    fun `3 slot config has higher safety floor per slot`() {
        // 3 sloty po 800, safety floor per slot = 560
        // Zjedzono 2400, zostały 1 slot, budgetLeft = 0
        val r = dc.recommend(
            unplannedKcal = 1000,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 2400,
            remainingSlots = 1,
            totalSlotsToday = 3
        )
        assertEquals(560, r.newKcalPerRemainingSlot) // 800 × 0.7
    }
}
