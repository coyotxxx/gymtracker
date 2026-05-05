package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType

data class DailyMacroGoal(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val breakdown: GoalBreakdown
)

/**
 * Pełen breakdown obliczeń żeby user wiedział SKĄD się biorą liczby.
 * Wyświetlamy w GoalBreakdownDialog (Diet → klik na hero kcal).
 */
data class GoalBreakdown(
    val weightKg: Double,
    val genderLabel: String,
    val tdeeKcal: Int,                 // Total Daily Energy Expenditure
    val tdeeFormulaText: String,       // np. "78 × 33 × 1.03 + 4 × 30 = 2771"
    val deficitOrSurplus: Int,         // -500 (CUT), 0 (MAINTAIN), +300 (BULK), itd
    val deficitLabel: String,          // np. "Deficyt 500 kcal (klasyczna redukcja, ~0.5 kg/tydz)"
    val isManualOverride: Boolean,     // true gdy user ręcznie ustawił kcal
    val proteinPerKg: Double,          // np. 2.2
    val fatPerKg: Double,              // np. 0.8
    val carbsCalculation: String       // np. "(2271 - 624 - 624) / 4 = 256g"
)

/**
 * Wylicza dzienne cele z bazą TDEE i adjustmentem zależnym od celu.
 *
 * @param profile UserProfile (waga, cel, płeć, dni treningowe)
 * @param fallbackWeightKg waga z BodyMeasurement gdy profile.bodyweightKg = null
 * @param manualKcalOverride gdy user ręcznie zmienił kcal — zwracamy to + osobny breakdown
 * @param customDeficit gdy user wybrał inny niż domyślny deficyt/nadwyżkę
 */
fun computeDailyGoal(
    profile: UserProfile,
    fallbackWeightKg: Double? = null,
    manualKcalOverride: Int? = null,
    customDeficit: Int? = null
): DailyMacroGoal {
    val weight = profile.bodyweightKg ?: fallbackWeightKg ?: 75.0

    // === KROK 1: TDEE (Total Daily Energy Expenditure) ===
    // Uproszczony Mifflin (bez wzrostu/wieku — używamy multiplier per kg).
    // Dla CUT/MAINTAIN/BULK te multipliery są BAZOWE — adjustment osobno.
    val baseMultiplier = 33.0  // średnio aktywny człowiek, bazowy ratio
    val genderMod = if (profile.gender == Gender.MALE) 1.03 else 0.97
    val activityBonus = profile.daysPerWeek * 30
    val tdee = (weight * baseMultiplier * genderMod + activityBonus).toInt()
    val tdeeFormula = "%.0f kg × %.1f × %.2f + %d × 30 = %d kcal".format(
        weight, baseMultiplier, genderMod, profile.daysPerWeek, tdee
    )

    // === KROK 2: Adjustment per cel ===
    val defaultDeficit = when (profile.weightGoalType) {
        WeightGoalType.CUT -> -500          // klasyczna redukcja (~0.5 kg/tydz)
        WeightGoalType.BULK -> 300           // umiarkowana nadwyżka (~0.3 kg/tydz)
        WeightGoalType.MAINTAIN -> 0
        WeightGoalType.NONE -> 0
    }
    val effectiveDeficit = customDeficit ?: defaultDeficit
    val deficitLabel = when {
        effectiveDeficit == 0 -> "Brak (utrzymanie wagi)"
        effectiveDeficit <= -750 -> "Agresywny deficyt %d kcal (~%s kg/tydz, ryzyko utraty masy mięśniowej)".format(
            effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0)
        )
        effectiveDeficit <= -500 -> "Klasyczna redukcja %d kcal (~%s kg/tydz)".format(
            effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0)
        )
        effectiveDeficit < 0 -> "Łagodny deficyt %d kcal (~%s kg/tydz, ochrona masy mięśniowej)".format(
            effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0)
        )
        effectiveDeficit <= 300 -> "Łagodna nadwyżka +$effectiveDeficit kcal (lean bulk, ~%s kg/tydz)".format(
            "%.2f".format(effectiveDeficit / 1100.0)
        )
        else -> "Nadwyżka +$effectiveDeficit kcal (~%s kg/tydz, większy zysk masy)".format(
            "%.2f".format(effectiveDeficit / 1100.0)
        )
    }

    // === KROK 3: Total kcal ===
    val rawKcal = tdee + effectiveDeficit
    val finalKcal = manualKcalOverride ?: rawKcal

    // === KROK 4: Makro per cel ===
    // Strategia: białko najwyższy priorytet (chroni masę), tłuszcz min 0.6 g/kg
    // dla zdrowia hormonalnego, węgle = reszta. Wartości oparte o Helms / RP / ISSN.
    val (proteinPerKg, fatPerKg) = when (profile.weightGoalType) {
        WeightGoalType.CUT -> 2.2 to 0.8       // wysokie białko, niskie tłuszcze, oszczędne węgle
        WeightGoalType.BULK -> 1.8 to 1.0      // niższe białko (mniej potrzebne na nadwyżce), więcej węgli
        WeightGoalType.MAINTAIN -> 2.0 to 1.0  // balans
        WeightGoalType.NONE -> 2.0 to 1.0
    }
    val proteinG = (weight * proteinPerKg).toInt()
    val fatG = (weight * fatPerKg).toInt()
    val proteinKcal = proteinG * 4
    val fatKcal = fatG * 9
    val carbsKcal = (finalKcal - proteinKcal - fatKcal).coerceAtLeast(0)
    val carbsG = carbsKcal / 4
    val carbsCalc = "($finalKcal - $proteinKcal - $fatKcal) / 4 = ${carbsG}g"

    return DailyMacroGoal(
        kcal = finalKcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        breakdown = GoalBreakdown(
            weightKg = weight,
            genderLabel = if (profile.gender == Gender.MALE) "mężczyzna (×1.03)" else "kobieta (×0.97)",
            tdeeKcal = tdee,
            tdeeFormulaText = tdeeFormula,
            deficitOrSurplus = effectiveDeficit,
            deficitLabel = deficitLabel,
            isManualOverride = manualKcalOverride != null,
            proteinPerKg = proteinPerKg,
            fatPerKg = fatPerKg,
            carbsCalculation = carbsCalc
        )
    )
}
