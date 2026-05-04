package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType

data class DailyMacroGoal(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

/**
 * Heurystyczne wyliczenie dziennego celu kcal+makro na bazie UserProfile.
 *
 * Wzór (uproszczony — pełen Mifflin-St Jeor wymagałby wieku i wzrostu):
 * - Bazowy multiplier: 30 kcal/kg dla CUT, 33 MAINTAIN, 38 BULK
 * - Korekta płci: M +3%, K −3%
 * - Korekta aktywności: +(daysPerWeek × 30 kcal) bo dni treningowe = wyższe TDEE
 *
 * Makro split:
 * - Białko: 2.0 g/kg masy ciała (typowe dla siły/hipertrofii)
 * - Tłuszcz: 1.0 g/kg
 * - Węgle: reszta (kcal − protein × 4 − fat × 9) / 4
 *
 * Dla 78 kg, mężczyzna, MAINTAIN, 4 treningi/tyg:
 *   78 × 33 × 1.03 + 4 × 30 = 2651 + 120 = 2771 kcal
 *   protein = 156g, fat = 78g, carbs = (2771 − 624 − 702) / 4 = 361g
 */
fun computeDailyGoal(profile: UserProfile, fallbackWeightKg: Double? = null): DailyMacroGoal {
    val weight = profile.bodyweightKg ?: fallbackWeightKg ?: 75.0
    val baseMultiplier = when (profile.weightGoalType) {
        WeightGoalType.CUT -> 30.0
        WeightGoalType.BULK -> 38.0
        WeightGoalType.MAINTAIN -> 33.0
        WeightGoalType.NONE -> 33.0
    }
    val genderMod = if (profile.gender == Gender.MALE) 1.03 else 0.97
    val activityBonus = profile.daysPerWeek * 30
    val kcal = (weight * baseMultiplier * genderMod + activityBonus).toInt()

    val proteinG = (weight * 2.0).toInt()
    val fatG = (weight * 1.0).toInt()
    val proteinKcal = proteinG * 4
    val fatKcal = fatG * 9
    val carbsKcal = (kcal - proteinKcal - fatKcal).coerceAtLeast(0)
    val carbsG = carbsKcal / 4

    return DailyMacroGoal(kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG)
}
