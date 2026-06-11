package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile

/**
 * v1.27.0 — testy computeDailyGoal, regresja bugu A.
 *
 * Bug A: TDEE liczył treningi PODWÓJNIE — raz w mnożniku aktywności
 * (ActivityLevel: MODERATE = "3-5 treningów"), drugi raz jako bonus
 * `daysPerWeek × 30`. Zawyżało TDEE → zawyżony cel kaloryczny.
 */
class DietGoalsTest {

    private fun goalFor(daysPerWeek: Int = 4) = computeDailyGoal(
        profile = UserProfile(
            bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = daysPerWeek),
        dietProfile = UserDietProfile(
            ageYears = 30, heightCm = 180,
            activityLevel = ActivityLevel.MODERATE, goalType = DietGoalType.MAINTAIN),
        latestMeasuredWeightKg = 80.0
    )

    @Test
    fun `TDEE = BMR razy mnoznik aktywnosci, bez bonusu za treningi`() {
        // BMR (Mifflin) = 10×80 + 6.25×180 − 5×30 + 5 = 1780
        // TDEE = 1780 × 1.55 (MODERATE) = 2759 — BEZ dawnego +daysPerWeek×30
        val tdee = goalFor().breakdown.tdeeKcal
        assertEquals("TDEE bez podwójnego liczenia treningów", 2759, tdee)
    }

    @Test
    fun `daysPerWeek NIE wplywa na TDEE (treningi sa juz w activityLevel)`() {
        // dawniej 2×/tydz dawało +60 kcal, 6×/tydz +180 kcal — różnica 120 kcal.
        // Po naprawie TDEE zależy tylko od activityLevel, nie od daysPerWeek.
        assertEquals("TDEE identyczne dla 2 i 6 treningów/tydz",
            goalFor(daysPerWeek = 2).breakdown.tdeeKcal,
            goalFor(daysPerWeek = 6).breakdown.tdeeKcal)
    }

    // === v2.15.0 (P1-1): twardy cap tempa redukcji do 1.5 kg/tydz ===

    private fun cutGoal(customDeficit: Int) = computeDailyGoal(
        profile = UserProfile(bodyweightKg = 80.0, gender = Gender.MALE, daysPerWeek = 4),
        dietProfile = UserDietProfile(
            ageYears = 30, heightCm = 180,
            activityLevel = ActivityLevel.MODERATE, goalType = DietGoalType.FAT_LOSS),
        customDeficit = customDeficit,
        latestMeasuredWeightKg = 80.0
    )

    @Test
    fun `agresywny deficyt jest ograniczony do 1_5 kg na tydzien`() {
        val g = cutGoal(-2000)  // -2000/1100 ≈ -1.8 kg/tydz → cap do -1650 (-1.5)
        assertTrue("warning o ograniczeniu tempa redukcji",
            g.safetyWarnings.any { it.contains("ograniczone do bezpiecznych 1.5") })
    }

    @Test
    fun `umiarkowany deficyt bez ograniczenia tempa`() {
        val g = cutGoal(-500)  // -0.45 kg/tydz — bezpieczne
        assertFalse("brak warningu o tempie przy łagodnym deficycie",
            g.safetyWarnings.any { it.contains("ograniczone do bezpiecznych 1.5") })
    }
}
