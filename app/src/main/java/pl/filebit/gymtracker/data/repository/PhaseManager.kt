package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.util.WeightTrend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sugestia od PhaseManager — analogicznie do AdjustmentDecision.
 */
data class PhaseSuggestion(
    val proposedType: DietPhaseType?,
    val durationDays: Int,
    val kcalAdjustment: Int,
    val reason: String,
    val explanation: String,
    val isStrong: Boolean // true → silnik mocno sugeruje, false → opcjonalna
) {
    companion object {
        val NONE = PhaseSuggestion(
            proposedType = null,
            durationDays = 0,
            kcalAdjustment = 0,
            reason = "no_action",
            explanation = "",
            isStrong = false
        )
    }
}

/**
 * Silnik długofalowej strategii diety.
 *
 * Reguły (priorytetowe, pierwsza pasująca wygrywa):
 *
 * 1. **DIET_BREAK** (silne):
 *    aktywna CUT >12 tyg + (adherence ↓ ≤70 LUB hunger high LUB sleep poor) →
 *    sugeruj 14-dniowy break (kcal=TDEE)
 *
 * 2. **MAINTENANCE phase** (silne):
 *    aktywna CUT >8 tyg + waga spadła ≥5% od startu cutu →
 *    sugeruj 7-14 dni maintenance
 *
 * 3. **REFEED_DAY** (średnie):
 *    aktywna CUT + wysoki głód + niska energia + (dziś dzień nóg LUB dziś ciężki trening) →
 *    sugeruj 1 dzień +300 kcal głównie z węgli
 *
 * Jeśli nic nie pasuje → PhaseSuggestion.NONE.
 */
@Singleton
class PhaseManager @Inject constructor() {

    fun suggest(
        currentPhase: DietPhase?,
        weightGoalType: WeightGoalType,
        weightAtCutStartKg: Double?,
        currentWeightKg: Double?,
        weightTrend: WeightTrend,
        adherenceKcalPct: Int,
        recovery: RecoverySnapshot,
        isTodayHeavyTraining: Boolean = false
    ): PhaseSuggestion {
        val now = System.currentTimeMillis()
        val cutDurationDays = if (currentPhase?.type == DietPhaseType.CUT) {
            currentPhase.durationDays()
        } else if (weightGoalType == WeightGoalType.CUT) {
            // Brak DietPhase, ale user ma WeightGoalType.CUT — przyjmij od ostatnich 14 dni jako proxy
            // (faktyczny start nieznany). Zwracamy 0 — reguły nie zadziałają bez DietPhase.
            0
        } else 0

        // === Reguła 1: DIET_BREAK po >12 tyg cut ===
        if (cutDurationDays >= 84) { // 12 tyg
            val triggers = mutableListOf<String>()
            if (adherenceKcalPct in 1..70) triggers += "adherence niska ($adherenceKcalPct%)"
            if (recovery.highHunger) triggers += "głód wysoki"
            if (recovery.badSleep) triggers += "sen zły"
            if (recovery.lowEnergy) triggers += "energia niska"
            if (triggers.size >= 2) {
                return PhaseSuggestion(
                    proposedType = DietPhaseType.DIET_BREAK,
                    durationDays = 14,
                    kcalAdjustment = 0,
                    reason = "long_cut_diet_break",
                    explanation = "Cut trwa ${cutDurationDays} dni (>12 tyg). Sygnały: ${triggers.joinToString(", ")}. " +
                        "Proponuję 14-dniowy diet break (kcal = TDEE) — odbuduje leptynę, hormony tarczycy, motywację. " +
                        "Bez breaku ryzyko wypalenia i utraty mięśni.",
                    isStrong = true
                )
            }
        }

        // === Reguła 2: MAINTENANCE phase po >8 tyg cut + waga ≥5% niżej ===
        if (cutDurationDays >= 56 && weightAtCutStartKg != null && currentWeightKg != null) {
            val percentDrop = (weightAtCutStartKg - currentWeightKg) / weightAtCutStartKg * 100
            if (percentDrop >= 5.0) {
                return PhaseSuggestion(
                    proposedType = DietPhaseType.MAINTENANCE,
                    durationDays = 10,
                    kcalAdjustment = 0,
                    reason = "long_cut_maintenance",
                    explanation = "Cut trwa $cutDurationDays dni (>8 tyg), waga spadła z %.1f kg do %.1f kg (-%.1f%%). ".format(
                        weightAtCutStartKg, currentWeightKg, percentDrop
                    ) + "Proponuję 10-dniową fazę maintenance (kcal = TDEE) — pozwoli ciału odpocząć, odbuduje hormony tarczycy. " +
                        "Po zakończeniu wracamy do redukcji.",
                    isStrong = true
                )
            }
        }

        // === Reguła 3: REFEED_DAY (jeden dzień +300 kcal) ===
        if (weightGoalType == WeightGoalType.CUT && recovery.hasEnoughData) {
            val needsRefeed = recovery.highHunger && recovery.lowEnergy
            if (needsRefeed || (recovery.highHunger && isTodayHeavyTraining)) {
                return PhaseSuggestion(
                    proposedType = DietPhaseType.REFEED_DAY,
                    durationDays = 1,
                    kcalAdjustment = 300,
                    reason = "cut_refeed_signal",
                    explanation = buildString {
                        append("Sygnały: głód wysoki")
                        if (recovery.lowEnergy) append(" + energia niska")
                        if (isTodayHeavyTraining) append(" + ciężki trening dziś")
                        append(". Proponuję 1 dzień refeed (+300 kcal, głównie węgle: ryż/owsianka/owoce). ")
                        append("Odbuduje glikogen, leptynę. Jutro wracamy do bazowych kcal.")
                    },
                    isStrong = false
                )
            }
        }

        return PhaseSuggestion.NONE
    }
}
