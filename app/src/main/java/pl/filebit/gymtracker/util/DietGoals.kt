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
    dietProfile: UserDietProfile? = null,
    /** Średni dzienny dodatek kcal z cardio (z TrainingDaySummary.cardioMinutes × 10 kcal/min). */
    avgDailyCardioKcal: Int = 0,
    /**
     * Najświeższa zmierzona waga (z BodyMeasurement) — ma PIERWSZEŃSTWO przed
     * profile.bodyweightKg które bywa stare (wprowadzone raz przy onboardingu).
     * Naprawa bug'a v1.0.17 — TDEE liczony dla aktualnej wagi.
     */
    latestMeasuredWeightKg: Double? = null
): DailyMacroGoal {
    val weight = latestMeasuredWeightKg ?: profile.bodyweightKg ?: fallbackWeightKg ?: 75.0

    // === KROK 1: TDEE (Total Daily Energy Expenditure) ===
    val tdee: Int
    val tdeeFormula: String
    // v1.24.23 fix #2: BMR estimate dla SafetyGuard floor (kcal nigdy < BMR).
    var bmrEstimate: Int? = null
    if (dietProfile != null) {
        // Pełen Mifflin-St Jeor — najdokładniejszy wzór
        val maleConst = if (profile.gender == Gender.MALE) 5.0 else -161.0
        val bmr = 10.0 * weight + 6.25 * dietProfile.heightCm - 5.0 * dietProfile.ageYears + maleConst
        bmrEstimate = bmr.toInt()
        val activityMult = when (dietProfile.activityLevel) {
            ActivityLevel.SEDENTARY -> 1.2
            ActivityLevel.LIGHT -> 1.375
            ActivityLevel.MODERATE -> 1.55
            ActivityLevel.VERY_ACTIVE -> 1.725
            ActivityLevel.EXTREME -> 1.9
        }
        // v1.27.0: mnożnik aktywności (Mifflin-St Jeor) JUŻ obejmuje treningi
        // siłowe — definicja ActivityLevel: MODERATE = "3-5 treningów",
        // VERY_ACTIVE = "6-7 treningów". Dawny bonus `dni × 30` był PODWÓJNYM
        // liczeniem (zawyżał TDEE o ~90-150 kcal → zawyżony cel kaloryczny).
        // Cardio liczone osobno z realnych danych trackera (opcjonalne).
        tdee = (bmr * activityMult + avgDailyCardioKcal).toInt()
        tdeeFormula = if (avgDailyCardioKcal > 0)
            "BMR (Mifflin) %.0f × aktywność %.3f + cardio %d kcal/dzień = %d kcal".format(
                bmr, activityMult, avgDailyCardioKcal, tdee
            )
        else
            "BMR (Mifflin) %.0f × aktywność %.3f = %d kcal".format(
                bmr, activityMult, tdee
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
    // v1.24.19: system SAM wylicza deficyt/surplus dla WSZYSTKICH 8 typów
    // z DietGoalType (jeśli dietProfile istnieje). Filozofia Macieja:
    // "system sam wylicza odpowiednią kaloryczność do każdego typu".
    //
    // 1 kg ≈ 7700 kcal → tempo (pace × 7700 / 7) dotyczy tylko typów wagowych
    // (FAT_LOSS, MUSCLE_GAIN). RECOMP/STRENGTH itp. mają stałe adjustmenty
    // bazujące na zasadach z literatury treningowej (Helms, ISSN, RP).
    //
    // Fallback gdy brak dietProfile: UserProfile.weightGoalType (CUT/BULK).
    val autoDeficitFromDiet: Int? = dietProfile?.let { dp ->
        // v1.24.24 CRITICAL fix: abs(pace) — DietOnboarding zapisywał pace negative
        // dla CUT (-0.5), nowa konwencja v1.24.19 oczekuje positive (kierunek z goalType).
        // Bez abs() Maciej dostawał +550 surplus zamiast -550 deficyt (cel 2949 vs oczekiwane 2029).
        val paceKcal = (kotlin.math.abs(dp.paceKgPerWeek) * 7700.0 / 7.0).toInt()
        when (dp.goalType) {
            // Wagowe cele: deficyt/surplus z paceKgPerWeek
            pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS -> -paceKcal
            pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN -> paceKcal
            // Rekompozycja: mały deficyt (~-300 kcal). Cel: trzymanie wagi
            // bez wzrostu tkanki tłuszczowej, jednoczesny wzrost mięśni.
            // Najczęściej dla advanced lifterów lub powracających po przerwie.
            pl.filebit.gymtracker.data.entity.DietGoalType.RECOMP -> -300
            // Utrzymanie: zero adjustment
            pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN -> 0
            // v1.24.23 fix #5: Siła = maintenance (zamiast +200).
            // Powerlifters/strength athletes (ISSN, RP) zazwyczaj jedzą AT MAINTENANCE
            // z dużą ilością węgli dla CNS recovery — surplus nie pomaga siłą,
            // a dodaje niepotrzebny tłuszcz. Wzrost siły = NEURAL adaptation,
            // nie hipertrofia. Dla budowania masy + siły jest MUSCLE_GAIN.
            pl.filebit.gymtracker.data.entity.DietGoalType.STRENGTH -> 0
            // v1.24.23 fix #4: Wytrzymałość = +100 kcal (lekki surplus).
            // ISSN: endurance athletes potrzebują dodatkowych kcal dla regeneracji
            // długich sesji. Plus w makro forsujemy min 5 g/kg węgli — kluczowe
            // dla glikogenu mięśniowego.
            pl.filebit.gymtracker.data.entity.DietGoalType.ENDURANCE -> 100
            // Zdrowie: utrzymanie, focus na jakość (nie kcal).
            pl.filebit.gymtracker.data.entity.DietGoalType.HEALTH -> 0
            // Event prep: agresywny deficyt z paceKgPerWeek (przygotowanie
            // do zawodów/sesji zdjęciowej — szybciej niż FAT_LOSS).
            pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP -> -paceKcal
        }
    }
    val defaultDeficit = autoDeficitFromDiet ?: when (profile.weightGoalType) {
        WeightGoalType.CUT -> -500          // klasyczna redukcja (~0.5 kg/tydz)
        WeightGoalType.BULK -> 300           // umiarkowana nadwyżka (~0.3 kg/tydz)
        WeightGoalType.MAINTAIN -> 0
        WeightGoalType.NONE -> 0
    }
    // v1.24.18: customDeficit user-override; jeśli null, używamy auto-deficytu
    // wyliczonego z paceKgPerWeek (lub fallback default).
    // v2.15.0 (P1-1): twardy CAP tempa redukcji — dietetyk nie pozwala na deficyt
    // implikujący >1.5 kg/tydz (ochrona mięśni + metabolizmu). 1.5 kg/tydz × 1100 = 1650 kcal.
    val effectiveDeficitRaw = customDeficit ?: defaultDeficit
    val maxSafeDeficit = -(SafetyGuard.MAX_LOSS_KG_PER_WEEK * 1100).toInt()  // -1650
    val deficitCapped = effectiveDeficitRaw < maxSafeDeficit
    val effectiveDeficit = if (deficitCapped) maxSafeDeficit else effectiveDeficitRaw
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
    // v1.24.19: makro per DietGoalType (8 typów, szczegółowe), fallback do
    // UserProfile.weightGoalType (4 typy). Białko najwyższy priorytet
    // (chroni masę mięśniową), tłuszcz min 0.6 g/kg dla zdrowia hormonalnego,
    // węgle = reszta. Wartości oparte o Helms / RP / ISSN.
    val (proteinPerKg, fatPerKg) = dietProfile?.let { dp ->
        when (dp.goalType) {
            // Wysokie białko (oszczędza masę w deficycie/przy reżimie)
            pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS -> 2.2 to 0.8
            pl.filebit.gymtracker.data.entity.DietGoalType.EVENT_PREP -> 2.4 to 0.7  // jeszcze wyższe białko, niższe tłuszcze
            pl.filebit.gymtracker.data.entity.DietGoalType.RECOMP -> 2.2 to 0.9      // wysokie białko (kluczowe dla recomp)
            // Średnie/niższe białko (na nadwyżce/utrzymaniu nie potrzeba tak dużo)
            pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN -> 1.8 to 1.0
            pl.filebit.gymtracker.data.entity.DietGoalType.STRENGTH -> 2.0 to 1.0   // siła + lean bulk
            // Balans
            pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN -> 2.0 to 1.0
            pl.filebit.gymtracker.data.entity.DietGoalType.HEALTH -> 1.6 to 1.0      // mniej restrykcyjne (jakość > ilość)
            // Wytrzymałość: niższe białko, więcej węgli
            pl.filebit.gymtracker.data.entity.DietGoalType.ENDURANCE -> 1.6 to 0.9
        }
    } ?: when (profile.weightGoalType) {
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

    // v2.15.0 (P1-1): tempo redukcji ograniczone do bezpiecznych 1.5 kg/tydz.
    if (deficitCapped) {
        wasCapped = true
        warnings += "Tempo redukcji ograniczone do bezpiecznych 1.5 kg/tydz (deficyt %d → %d kcal). "
            .format(effectiveDeficitRaw, effectiveDeficit) +
            "Szybsza utrata to ryzyko mięśni i spowolnienia metabolizmu."
    }

    val kcalResult = SafetyGuard.validateKcal(finalKcal, profile, weight, bmrEstimate)
    val safeKcal = if (kcalResult is SafetyResult.Block) {
        wasCapped = true
        warnings += kcalResult.message
        kcalResult.cappedValue
    } else {
        kcalResult.warningMessage?.let { warnings += it }
        finalKcal
    }

    val proteinResult = SafetyGuard.validateProtein(rawProteinG, weight, profile.daysPerWeek)
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

    // v1.24.23 fix #4: dla ENDURANCE wymuszamy min 5 g/kg węgli (ISSN).
    // Strategia: jeśli "reszta" daje mniej niż 5g/kg, podnoszę carbs i zmniejszam
    // tłuszcz (bo białko jest zafiksowane wysoko dla ochrony masy).
    val isEndurance = dietProfile?.goalType ==
        pl.filebit.gymtracker.data.entity.DietGoalType.ENDURANCE
    val proteinKcal = safeProteinG * 4
    val (finalFatG, carbsG, carbsCalc) = if (isEndurance) {
        val minCarbsG = (weight * 5.0).toInt()  // 5 g/kg węgli minimum
        val minCarbsKcal = minCarbsG * 4
        val remainingKcal = safeKcal - proteinKcal - minCarbsKcal
        val maxFatGFromKcal = (remainingKcal / 9).coerceAtLeast(0)
        // Jeśli wymuszone carbs zostawiają zbyt mało dla tłuszczu (<min 0.6 g/kg),
        // dodaj tłuszcz minimum, nawet kosztem carbs.
        val fatMin = (weight * 0.6).toInt()
        if (maxFatGFromKcal < fatMin) {
            // Krytyczne: tłuszcz minimum, carbs = reszta
            val carbsKcal = (safeKcal - proteinKcal - fatMin * 9).coerceAtLeast(0)
            val c = carbsKcal / 4
            Triple(fatMin, c, "ENDURANCE (carbs=reszta gdy tłuszcz<min): " +
                "($safeKcal − $proteinKcal − ${fatMin * 9}) / 4 = ${c}g")
        } else {
            Triple(maxFatGFromKcal, minCarbsG,
                "ENDURANCE (carbs ≥5g/kg=${minCarbsG}g, tłuszcz=reszta): ${maxFatGFromKcal}g tłuszcz")
        }
    } else {
        val fatKcal = safeFatG * 9
        val carbsKcal = (safeKcal - proteinKcal - fatKcal).coerceAtLeast(0)
        val c = carbsKcal / 4
        Triple(safeFatG, c, "($safeKcal - $proteinKcal - $fatKcal) / 4 = ${c}g")
    }

    // Medical flags
    val medicalFlags = MedicalFlagger.analyze(profile, dietProfile, weight)

    return DailyMacroGoal(
        kcal = safeKcal,
        proteinG = safeProteinG,
        carbsG = carbsG,
        fatG = finalFatG,
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
            // v1.24.23: dla ENDURANCE tłuszcz mógł być przeliczony (carbs floor wymusza),
            // pokażmy faktyczną wartość per kg dla breakdown.
            fatPerKg = if (isEndurance && weight > 0) finalFatG / weight else fatPerKg,
            carbsCalculation = carbsCalc
        )
    )
}
