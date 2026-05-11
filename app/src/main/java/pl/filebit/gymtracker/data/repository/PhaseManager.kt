package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
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
 *    sugeruj 1 dzień +300/+400/+150 kcal (zależnie od fazy treningowej) głównie z węgli
 *
 * 4. **AUTO_REFEED_HEAVY_DAY** (v1.17.0, średnie):
 *    aktywna CUT ≥ 14 dni + dziś heavy training + ≥7 dni od ostatniego REFEED →
 *    proaktywnie sugeruj REFEED przed wypaleniem (nie czeka na sygnały głodu)
 *
 * Jeśli nic nie pasuje → PhaseSuggestion.NONE.
 *
 * v1.17.0: dodano `trainingMesocycle` — sync z fazą treningu (INTENSIFICATION zwiększa REFEED,
 * DELOAD redukuje). Dodano `daysSinceLastRefeed` dla reguły 4.
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
        isTodayHeavyTraining: Boolean = false,
        trainingMesocycle: TrainingMesocycle? = null,
        daysSinceLastRefeed: Int? = null
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

        // === Reguła 3: REFEED_DAY na sygnał (jeden dzień, kcal zależne od fazy treningu) ===
        if (weightGoalType == WeightGoalType.CUT && recovery.hasEnoughData) {
            val needsRefeed = recovery.highHunger && recovery.lowEnergy
            if (needsRefeed || (recovery.highHunger && isTodayHeavyTraining)) {
                val (kcalBonus, phaseNote) = refeedKcalForPhase(trainingMesocycle)
                return PhaseSuggestion(
                    proposedType = DietPhaseType.REFEED_DAY,
                    durationDays = 1,
                    kcalAdjustment = kcalBonus,
                    reason = "cut_refeed_signal",
                    explanation = buildString {
                        append("Sygnały: głód wysoki")
                        if (recovery.lowEnergy) append(" + energia niska")
                        if (isTodayHeavyTraining) append(" + ciężki trening dziś")
                        if (phaseNote.isNotEmpty()) append(" (").append(phaseNote).append(")")
                        append(". Proponuję 1 dzień refeed (+$kcalBonus kcal, głównie węgle: ryż/owsianka/owoce). ")
                        append("Odbuduje glikogen, leptynę. Jutro wracamy do bazowych kcal.")
                    },
                    isStrong = false
                )
            }
        }

        // === Reguła 4 (v1.17.0): AUTO_REFEED w heavy day po długim cucie ===
        // Proaktywny refeed bez czekania na sygnały głodu — gdy CUT ≥ 14d, dziś heavy + ≥7d od ostatniego refeed.
        if (weightGoalType == WeightGoalType.CUT
            && cutDurationDays >= 14
            && isTodayHeavyTraining
            && (daysSinceLastRefeed == null || daysSinceLastRefeed >= 7)
        ) {
            val (kcalBonus, phaseNote) = refeedKcalForPhase(trainingMesocycle)
            val daysText = daysSinceLastRefeed?.let { "$it dni od ostatniego refeed" } ?: "brak wcześniejszego refeed"
            return PhaseSuggestion(
                proposedType = DietPhaseType.REFEED_DAY,
                durationDays = 1,
                kcalAdjustment = kcalBonus,
                reason = "auto_refeed_heavy_day",
                explanation = buildString {
                    append("Dziś ciężki trening, cut trwa $cutDurationDays dni, $daysText.")
                    if (phaseNote.isNotEmpty()) append(" Faza treningu: $phaseNote.")
                    append(" Proponuję proaktywny refeed (+$kcalBonus kcal z węgli) — odbuduje glikogen przed kolejnym tygodniem treningu. ")
                    append("Bez sygnałów wypalenia, profilaktycznie.")
                },
                isStrong = false
            )
        }

        return PhaseSuggestion.NONE
    }

    /**
     * Phase-aware kcal bonus dla REFEED_DAY:
     *  - INTENSIFICATION → +400 (więcej węgli przed ciężkim trening)
     *  - ACCUMULATION    → +300 (status quo — średnia objętość)
     *  - DELOAD          → +150 (lżejszy, redukowane potrzeby)
     *  - PEAKING         → +400 (pełny glikogen do maksów)
     *  - RECOVERY        → +100 (minimum, regeneracja)
     *  - brak mesocyklu  → +300 (backward compat)
     *
     * Zwraca (kcal, opisFazy).
     */
    private fun refeedKcalForPhase(mesocycle: TrainingMesocycle?): Pair<Int, String> {
        if (mesocycle == null || !mesocycle.isActive) return 300 to ""
        return when (mesocycle.phase) {
            MesocyclePhase.INTENSIFICATION -> 400 to "intensyfikacja tyg ${mesocycle.weekInPhase}/${mesocycle.phaseLengthWeeks}"
            MesocyclePhase.ACCUMULATION -> 300 to "akumulacja tyg ${mesocycle.weekInPhase}/${mesocycle.phaseLengthWeeks}"
            MesocyclePhase.DELOAD -> 150 to "deload"
            MesocyclePhase.PEAKING -> 400 to "peaking tyg ${mesocycle.weekInPhase}/${mesocycle.phaseLengthWeeks}"
            MesocyclePhase.RECOVERY -> 100 to "recovery"
        }
    }
}
