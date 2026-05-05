package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType

data class DailyMacroGoal(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val breakdown: GoalBreakdown,
    /** Lista ostrzeżeń SafetyGuard (np. zbyt agresywny deficyt) — pokazujemy w UI. */
    val safetyWarnings: List<String> = emptyList(),
    /** Lista flag medycznych — pokazujemy w GoalBreakdownDialog z disclaimerem. */
    val medicalFlags: List<MedicalFlag> = emptyList(),
    /** True gdy SafetyGuard cap'nął kcal lub makro (zostały zmienione na bezpieczne). */
    val wasCapped: Boolean = false
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
    customDeficit: Int? = null,
    dietProfile: UserDietProfile? = null
): DailyMacroGoal {
    val weight = profile.bodyweightKg ?: fallbackWeightKg ?: 75.0

    // === KROK 1: TDEE (Total Daily Energy Expenditure) ===
    val tdee: Int
    val tdeeFormula: String
    if (dietProfile != null) {
        // Pełen Mifflin-St Jeor — najdokładniejszy wzór
        val maleConst = if (profile.gender == Gender.MALE) 5.0 else -161.0
        val bmr = 10.0 * weight + 6.25 * dietProfile.heightCm - 5.0 * dietProfile.ageYears + maleConst
        val activityMult = when (dietProfile.activityLevel) {
            ActivityLevel.SEDENTARY -> 1.2
            ActivityLevel.LIGHT -> 1.375
            ActivityLevel.MODERATE -> 1.55
            ActivityLevel.VERY_ACTIVE -> 1.725
            ActivityLevel.EXTREME -> 1.9
        }
        // Plus dodatkowy bonus za treningi (jeśli activity level nie obejmuje)
        val trainingBonus = profile.daysPerWeek * 30
        tdee = (bmr * activityMult + trainingBonus).toInt()
        tdeeFormula = "BMR (Mifflin) %.0f + aktywność ×%.3f + treningi %d × 30 = %d kcal".format(
            bmr, activityMult, profile.daysPerWeek, tdee
        )
    } else {
        // Fallback gdy brak UserDietProfile (przed onboardingiem diety)
        val baseMultiplier = 33.0
        val genderMod = if (profile.gender == Gender.MALE) 1.03 else 0.97
        val activityBonus = profile.daysPerWeek * 30
        tdee = (weight * baseMultiplier * genderMod + activityBonus).toInt()
        tdeeFormula = "%.0f kg × %.1f × %.2f + %d × 30 = %d kcal (uproszczone, brak danych wieku/wzrostu)".format(
            weight, baseMultiplier, genderMod, profile.daysPerWeek, tdee
        )
    }

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
    val rawProteinG = (weight * proteinPerKg).toInt()
    val rawFatG = (weight * fatPerKg).toInt()

    // === SAFETYGUARD: hard-limity ===
    val warnings = mutableListOf<String>()
    var wasCapped = false

    val kcalResult = SafetyGuard.validateKcal(finalKcal, profile, weight)
    val safeKcal = if (kcalResult is SafetyResult.Block) {
        wasCapped = true
        warnings += kcalResult.message
        kcalResult.cappedValue
    } else {
        kcalResult.warningMessage?.let { warnings += it }
        finalKcal
    }

    val proteinResult = SafetyGuard.validateProtein(rawProteinG, weight)
    val safeProteinG = if (proteinResult is SafetyResult.Block) {
        wasCapped = true
        warnings += proteinResult.message
        proteinResult.cappedValue
    } else {
        proteinResult.warningMessage?.let { warnings += it }
        rawProteinG
    }

    val fatResult = SafetyGuard.validateFat(rawFatG, weight)
    val safeFatG = if (fatResult is SafetyResult.Block) {
        wasCapped = true
        warnings += fatResult.message
        fatResult.cappedValue
    } else {
        rawFatG
    }

    // Tempo redukcji — info-only, nie cap
    val weeklyKgChange = effectiveDeficit / 1100.0
    val rateResult = SafetyGuard.validateDeficitRate(weeklyKgChange)
    rateResult.warningMessage?.let { warnings += it }

    val proteinKcal = safeProteinG * 4
    val fatKcal = safeFatG * 9
    val carbsKcal = (safeKcal - proteinKcal - fatKcal).coerceAtLeast(0)
    val carbsG = carbsKcal / 4
    val carbsCalc = "($safeKcal - $proteinKcal - $fatKcal) / 4 = ${carbsG}g"

    // Medical flags
    val medicalFlags = MedicalFlagger.analyze(profile, dietProfile, weight)

    return DailyMacroGoal(
        kcal = safeKcal,
        proteinG = safeProteinG,
        carbsG = carbsG,
        fatG = safeFatG,
        safetyWarnings = warnings,
        medicalFlags = medicalFlags,
        wasCapped = wasCapped,
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
