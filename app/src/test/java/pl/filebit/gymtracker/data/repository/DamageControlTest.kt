package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageControlTest {

    private val dc = DamageControl()

    @Test
    fun `unplanned kebab + 2 slots remaining redistributes`() {
        // Cel 2400, zjedzono 1500 (1 śniadanie 800 + kebab 700), zostały 2 sloty (obiad+kolacja)
        // budgetLeft = 2400 - 1500 = 900, /2 = 450 kcal/slot
        val r = dc.recommend(
            unplannedKcal = 700,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1500,
            remainingSlots = 2
        )
        assertEquals(450, r.newKcalPerRemainingSlot)
        assertFalse(r.isExtremeOvereating)
    }

    @Test
    fun `pozostale sloty NIGDY ponizej safety floor`() {
        // Cel 2400, zjedzono 2400 (catastrofa), zostały 2 sloty
        // budgetLeft = 0, /2 = 0
        // safety floor = 2400 × 0.85 = 2040, /2 = 1020 — to dużo
        // Test sprawdza że floor zwycięża
        val r = dc.recommend(
            unplannedKcal = 1500,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 2400,
            remainingSlots = 2
        )
        // safety floor / remaining = 2040 / 2 = 1020
        assertEquals(1020, r.newKcalPerRemainingSlot)
    }

    @Test
    fun `extreme overeating flagged when budget left under negative half goal`() {
        // Cel 2400, zjedzono 4000 → budgetLeft = -1600, half = -1200, -1600 < -1200 → extreme
        val r = dc.recommend(
            unplannedKcal = 2000,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 4000,
            remainingSlots = 1
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
            remainingSlots = 0
        )
        assertEquals(0, r.newKcalPerRemainingSlot)
        assertTrue(r.message.contains("Spokojnie") || r.message.contains("Przekroczenie"))
    }

    @Test
    fun `slight overeating - just redistribute calmly`() {
        // 2400 cel, zjedzono 1900, zostały 1 slot
        // budgetLeft 500, safety floor 2040 / 1 = 2040 — floor too high?
        // 500 < 2040 → returned 2040
        val r = dc.recommend(
            unplannedKcal = 200,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1900,
            remainingSlots = 1
        )
        assertFalse(r.isExtremeOvereating)
        // Zaokrąglane do safety floor
        assertEquals(2040, r.newKcalPerRemainingSlot)
    }

    @Test
    fun `safety floor is 85 percent of daily`() {
        val r = dc.recommend(
            unplannedKcal = 0,
            dailyGoalKcal = 2000,
            alreadyConsumedKcalIncludingUnplanned = 1000,
            remainingSlots = 2
        )
        assertEquals(1700, r.safetyFloorKcal) // 2000 × 0.85
    }

    @Test
    fun `result contains all snapshot fields`() {
        val r = dc.recommend(
            unplannedKcal = 700,
            dailyGoalKcal = 2400,
            alreadyConsumedKcalIncludingUnplanned = 1500,
            remainingSlots = 2
        )
        assertEquals(700, r.unplannedKcal)
        assertEquals(2400, r.originalDailyGoal)
        assertEquals(1500, r.alreadyConsumedKcal)
        assertEquals(2, r.remainingSlots)
    }
}
