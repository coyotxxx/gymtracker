package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBudgetCalculatorTest {

    private val calc = WeeklyBudgetCalculator()

    @Test
    fun `weekly target = daily × 7`() {
        val s = calc.compute(daysLoggedKcalSum = 0, dailyGoalKcal = 2000, daysLogged = 0, daysRemainingInWeek = 7)
        assertEquals(14000, s.weeklyTargetKcal)
    }

    @Test
    fun `bank balance positive when undereaten`() {
        // 4 dni × 1800 kcal = 7200, target 14000, balance +6800
        val s = calc.compute(daysLoggedKcalSum = 7200, dailyGoalKcal = 2000, daysLogged = 4, daysRemainingInWeek = 3)
        assertEquals(6800, s.bankBalanceKcal)
    }

    @Test
    fun `bank balance negative when overeaten`() {
        // 4 dni × 2400 kcal = 9600, target 14000, balance +4400 (still under)
        // 4 dni × 2800 kcal = 11200, target 14000, balance +2800 (still under)
        // 4 dni × 3500 kcal = 14000, exactly at target
        // 4 dni × 4000 kcal = 16000, balance -2000
        val s = calc.compute(daysLoggedKcalSum = 16000, dailyGoalKcal = 2000, daysLogged = 4, daysRemainingInWeek = 3)
        assertEquals(-2000, s.bankBalanceKcal)
    }

    @Test
    fun `safety floor is 85 percent of daily`() {
        val s = calc.compute(daysLoggedKcalSum = 0, dailyGoalKcal = 2000, daysLogged = 0, daysRemainingInWeek = 7)
        assertEquals(1700, s.safetyFloorKcal)
    }

    @Test
    fun `suggested daily never below safety floor`() {
        // Massive overeating: target 14000, ate 18000 (impossible to recoup safely)
        val s = calc.compute(daysLoggedKcalSum = 18000, dailyGoalKcal = 2000, daysLogged = 4, daysRemainingInWeek = 3)
        // Balance: -4000 / 3 days = -1333 (would be way below safety)
        // Safety floor = 1700
        assertEquals(1700, s.suggestedRemainingDailyKcal)
    }

    @Test
    fun `suggested daily redistributes leftover`() {
        // target 14000, ate 8000 in 4 days, 6000 left to spread over 3 days = 2000 each
        val s = calc.compute(daysLoggedKcalSum = 8000, dailyGoalKcal = 2000, daysLogged = 4, daysRemainingInWeek = 3)
        assertEquals(2000, s.suggestedRemainingDailyKcal)
    }

    @Test
    fun `suggested daily can be higher than dailyGoal when bank balance positive`() {
        // target 14000, ate 6000 in 3 days (avg 2000), 8000 left to spread over 4 days = 2000 each
        // ate only 5000 in 3 days (avg ~1667), 9000 left over 4 days = 2250 each
        val s = calc.compute(daysLoggedKcalSum = 5000, dailyGoalKcal = 2000, daysLogged = 3, daysRemainingInWeek = 4)
        assertEquals(2250, s.suggestedRemainingDailyKcal)
    }

    @Test
    fun `extreme overeating produces dont nadrabiamy message`() {
        val s = calc.compute(daysLoggedKcalSum = 25000, dailyGoalKcal = 2000, daysLogged = 5, daysRemainingInWeek = 2)
        assertTrue(s.message.contains("Spokojnie") || s.message.contains("nie nadrabiamy"))
    }

    @Test
    fun `under 2 days logged returns waiting message`() {
        val s = calc.compute(daysLoggedKcalSum = 1500, dailyGoalKcal = 2000, daysLogged = 1, daysRemainingInWeek = 6)
        assertTrue(s.message.contains("za mało dni"))
    }

    @Test
    fun `adherence 100 percent when on target`() {
        val s = calc.compute(daysLoggedKcalSum = 14000, dailyGoalKcal = 2000, daysLogged = 7, daysRemainingInWeek = 0)
        assertEquals(100, s.weeklyAdherencePct)
    }

    @Test
    fun `daysRemaining zero returns dailyGoal as suggested (no division by zero)`() {
        val s = calc.compute(daysLoggedKcalSum = 14000, dailyGoalKcal = 2000, daysLogged = 7, daysRemainingInWeek = 0)
        assertEquals(2000, s.suggestedRemainingDailyKcal)
    }
}
