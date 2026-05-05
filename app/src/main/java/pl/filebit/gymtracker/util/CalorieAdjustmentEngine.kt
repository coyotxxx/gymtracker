package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import pl.filebit.gymtracker.data.repository.RecoverySnapshot

enum class AdjustmentAction {
    HOLD,                   // zostaw jak jest — plan działa
    DECREASE_KCAL,          // obniż kcal (np. waga stoi w CUT)
    INCREASE_KCAL,          // dodaj kcal (np. waga nie rośnie w BULK)
    DELOAD,                 // tydzień regeneracji
    SIMPLIFY_PLAN,          // niska adherence — uprość plan zamiast zmieniać kcal
    REFEED_DAY,             // 1 dzień +200-400 kcal głównie z węgli (regeneracja)
    NEEDS_MORE_DATA         // za mało wagi/dni do decyzji
}

data class AdjustmentDecision(
    val action: AdjustmentAction,
    val kcalDeltaProposed: Int,         // np. -150, +200, 0
    val newKcal: Int,                   // currentKcal + delta (po cap przez SafetyGuard)
    val reason: String,                 // dla loga
    val explanation: String,            // dla usera (po ludzku)
    val warnings: List<String> = emptyList(),
    val confidence: Confidence
)

enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * Silnik regułowy automatycznej korekty kcal/makro.
 *
 * Filozofia: "nie zgaduj → mierz → analizuj trend → koryguj małymi krokami → wyjaśnij".
 *
 * Inputs (musi być real, nie z LLM):
 * - WeightTrend (z TrendAnalyzer)
 * - AdherenceSummary 7d i 14d (z AdherenceCalculator)
 * - UserProfile (cel CUT/MAINTAIN/BULK)
 * - currentKcal (current goal)
 *
 * Reguły z spec usera:
 * - CUT + waga stoi 14d + adherence wysokie (≥85%) → -150 kcal
 * - CUT + waga stoi + adherence niskie (<70%) → SIMPLIFY (NIE obniżaj)
 * - CUT + waga spada >1.5 kg/tydz → +150 kcal
 * - BULK + waga nie rośnie 2-3 tyg → +150 kcal
 * - BULK + waga rośnie szybko + pas rośnie → -150 kcal
 * - RECOMP + stała waga + pas spada → HOLD (plan działa)
 *
 * Każda decyzja MA wyjaśnienie po ludzku — to klucz spec usera.
 */
object CalorieAdjustmentEngine {

    fun analyze(
        profile: UserProfile,
        currentKcal: Int,
        weightTrend: WeightTrend,
        adherence14d: AdherenceSummary,
        adherence7d: AdherenceSummary,
        recovery: RecoverySnapshot = RecoverySnapshot.EMPTY,
        hydrationAdherencePct: Int = 100
    ): AdjustmentDecision {

        // === Brak danych → poczekaj ===
        if (!weightTrend.hasEnoughData || adherence14d.sampleDays < 7) {
            return AdjustmentDecision(
                action = AdjustmentAction.NEEDS_MORE_DATA,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "insufficient_data",
                explanation = "Potrzeba minimum 7 dni z zalogowanymi posiłkami i co najmniej 3 pomiarów wagi w 14 dniach. " +
                    "Aktualnie: ${adherence14d.sampleDays} dni z dietą, ${weightTrend.sampleCount} pomiarów wagi. " +
                    "Loguj dalej — silnik włączy się automatycznie.",
                confidence = Confidence.LOW
            )
        }

        // === Recovery override — przed cięciem kcal sprawdzamy regenerację ===
        if (recovery.hasEnoughData && profile.weightGoalType == WeightGoalType.CUT) {
            // Bardzo zła regeneracja przy cut → zatrzymaj cięcia
            if (recovery.badSleep && recovery.highStress) {
                return AdjustmentDecision(
                    action = AdjustmentAction.HOLD,
                    kcalDeltaProposed = 0,
                    newKcal = currentKcal,
                    reason = "cut_recovery_poor_sleep_stress",
                    explanation = "Średnia z 7 dni: sen %.1fh + stres %.1f/5 — regeneracja zła. NIE tnę kcal. Najpierw popraw sen i obniż stres.".format(
                        recovery.avgSleepHours ?: 0.0, recovery.avgStress ?: 0.0
                    ),
                    confidence = Confidence.HIGH,
                    warnings = listOf("Zła regeneracja blokuje korekty diety. Sen/stres są fundamentem.")
                )
            }
            // Wysoki głód + waga stoi → REFEED zamiast DECREASE
            if (recovery.highHunger && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    action = AdjustmentAction.REFEED_DAY,
                    kcalDeltaProposed = +300,
                    newKcal = currentKcal + 300,
                    reason = "cut_high_hunger_refeed",
                    explanation = "Średnia 7d: głód %.1f/5. Waga stoi — refeed day +300 kcal (głównie węgle) odbuduje leptynę. Następne dni: wracamy do bazowych kcal.".format(recovery.avgHunger ?: 0.0),
                    confidence = Confidence.HIGH
                )
            }
            // Soreness wysoki + niska energia → DELOAD
            if (recovery.highSoreness && recovery.lowEnergy) {
                return AdjustmentDecision(
                    action = AdjustmentAction.DELOAD,
                    kcalDeltaProposed = 0,
                    newKcal = currentKcal,
                    reason = "cut_recovery_deload",
                    explanation = "Średnia 7d: soreness %.1f/5 + energia %.1f/5 — sygnał DELOAD. Tydzień lżejszy/odpoczynek.".format(
                        recovery.avgSoreness ?: 0.0, recovery.avgEnergy ?: 0.0
                    ),
                    confidence = Confidence.HIGH
                )
            }
            // Wysoka trudność trzymania planu → SIMPLIFY (zamiast cięcia)
            if (recovery.highDifficulty) {
                return AdjustmentDecision(
                    action = AdjustmentAction.SIMPLIFY_PLAN,
                    kcalDeltaProposed = 0,
                    newKcal = currentKcal,
                    reason = "cut_high_difficulty",
                    explanation = "Średnia 7d: trudność trzymania planu %.1f/5. NIE tnę kcal — uprośćmy plan: prostsze posiłki, mniej składników, krótsze gotowanie.".format(recovery.avgDifficulty ?: 0.0),
                    confidence = Confidence.HIGH
                )
            }
            // Hydration adherence niski + waga stoi → blokada cięć (zatrzymanie wody maskuje progres)
            if (hydrationAdherencePct < 60 && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    action = AdjustmentAction.HOLD,
                    kcalDeltaProposed = 0,
                    newKcal = currentKcal,
                    reason = "cut_low_hydration",
                    explanation = "Adherence wody w 14d: ${hydrationAdherencePct}%. Niskie nawodnienie zatrzymuje wodę i maskuje spadek tłuszczu. NIE tnę — popraw nawodnienie.",
                    confidence = Confidence.MEDIUM,
                    warnings = listOf("Niskie nawodnienie może mieć większy wpływ na pomiary niż dieta.")
                )
            }
        }

        val highAdherence = adherence14d.avgKcalPct in 85..115 && adherence14d.avgProteinPct >= 80
        val lowAdherence = adherence14d.avgKcalPct < 70 || adherence14d.avgProteinPct < 60
        val workoutsCompletionPct = if (adherence14d.workoutsPlanned > 0) {
            adherence14d.workoutsDone * 100 / adherence14d.workoutsPlanned
        } else 100

        return when (profile.weightGoalType) {
            WeightGoalType.CUT -> analyzeCut(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence, workoutsCompletionPct)
            WeightGoalType.BULK -> analyzeBulk(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence, workoutsCompletionPct)
            WeightGoalType.MAINTAIN -> analyzeMaintain(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence)
            WeightGoalType.NONE -> AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "no_goal_set",
                explanation = "Brak ustalonego celu wagi — silnik nie podejmuje korekt.",
                confidence = Confidence.LOW
            )
        }
    }

    private fun analyzeCut(
        currentKcal: Int,
        trend: WeightTrend,
        adherence: AdherenceSummary,
        highAdherence: Boolean,
        lowAdherence: Boolean,
        workoutsPct: Int
    ): AdjustmentDecision {

        // 1. Spadek za szybki — chronimy mięśnie
        if (trend.isFastLoss) {
            return AdjustmentDecision(
                action = AdjustmentAction.INCREASE_KCAL,
                kcalDeltaProposed = +150,
                newKcal = currentKcal + 150,
                reason = "cut_fast_loss",
                explanation = "Tracisz wagę za szybko (${"%.1f".format(-trend.slopeKgPerWeek!!)} kg/tydz). " +
                    "Powyżej 1.5 kg/tydz to ryzyko utraty masy mięśniowej + spadku metabolizmu. " +
                    "Dodaję +150 kcal.",
                confidence = Confidence.HIGH
            )
        }

        // 2. Niska adherence → uprość plan (NIE obniżaj kcal)
        if (lowAdherence) {
            return AdjustmentDecision(
                action = AdjustmentAction.SIMPLIFY_PLAN,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "cut_low_adherence",
                explanation = "Średnia zgodność z planem to ${adherence.avgKcalPct}% kcal, ${adherence.avgProteinPct}% białka. " +
                    "Najpierw poprawmy plan żeby był bardziej wykonalny — NIE obniżam kcal. " +
                    "Sugestia: prostsze posiłki, mniej składników, łatwiejsze do przygotowania.",
                confidence = Confidence.HIGH,
                warnings = listOf("Niska zgodność z planem — sprawdź czy posiłki nie są za skomplikowane.")
            )
        }

        // 3. Stagnacja + adherence wysokie + treningi wykonane → obniż
        if (trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                action = AdjustmentAction.DECREASE_KCAL,
                kcalDeltaProposed = -150,
                newKcal = currentKcal - 150,
                reason = "cut_stagnation_high_adherence",
                explanation = "Średnia waga z 14 dni nie spada (${"%.1f".format(trend.avg14Days ?: 0.0)} kg). " +
                    "Zgodność z dietą: ${adherence.avgKcalPct}%, treningi: ${adherence.workoutsDone}/${adherence.workoutsPlanned}. " +
                    "Wszystko wykonujesz dobrze, ale waga stoi — czas na małą korektę. Obniżam o 150 kcal.",
                confidence = Confidence.HIGH
            )
        }

        // 4. Stagnacja + adherence wysokie + treningi NIE — najpierw treningi
        if (trend.isStagnationLikely && highAdherence && workoutsPct < 80) {
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "cut_stagnation_low_workouts",
                explanation = "Waga stoi, dieta OK, ale wykonano tylko $workoutsPct% planowanych treningów. " +
                    "Najpierw zwiększ frekwencję treningów — NIE obniżam kcal.",
                confidence = Confidence.MEDIUM,
                warnings = listOf("Niska frekwencja treningów — dieta nie zadziała sama.")
            )
        }

        // 5. Spada powoli — plan działa
        if (trend.direction == TrendDirection.FALLING && !trend.isFastLoss) {
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "cut_progressing",
                explanation = "Tracisz wagę w tempie ${"%.2f".format(-trend.slopeKgPerWeek!!)} kg/tydz — idealnie. " +
                    "Plan działa, kontynuuj.",
                confidence = Confidence.HIGH
            )
        }

        // Default
        return AdjustmentDecision(
            action = AdjustmentAction.HOLD,
            kcalDeltaProposed = 0,
            newKcal = currentKcal,
            reason = "cut_default_hold",
            explanation = "Trzymaj plan. Waga porusza się ale nie ma jednoznacznego sygnału do korekty. Sprawdzimy za tydzień.",
            confidence = Confidence.MEDIUM
        )
    }

    private fun analyzeBulk(
        currentKcal: Int,
        trend: WeightTrend,
        adherence: AdherenceSummary,
        highAdherence: Boolean,
        lowAdherence: Boolean,
        workoutsPct: Int
    ): AdjustmentDecision {

        // 1. Brak frekwencji treningów — nie zwiększaj kcal
        if (workoutsPct < 70) {
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "bulk_low_workouts",
                explanation = "Wykonano tylko $workoutsPct% planowanych treningów. " +
                    "Bez treningów dodatkowe kcal idą w tłuszcz, nie mięśnie. NIE zwiększam.",
                confidence = Confidence.HIGH,
                warnings = listOf("Niska frekwencja treningów — bulk bez treningów = przyrost tłuszczu.")
            )
        }

        // 2. Przyrost za szybki + niska adherence białka — pas rośnie nieproporcjonalnie
        if (trend.isFastGain) {
            return AdjustmentDecision(
                action = AdjustmentAction.DECREASE_KCAL,
                kcalDeltaProposed = -150,
                newKcal = currentKcal - 150,
                reason = "bulk_fast_gain",
                explanation = "Przyrost wagi ${"%.1f".format(trend.slopeKgPerWeek!!)} kg/tydz — większość to tłuszcz. " +
                    "Zalecane lean bulk = 0.3 kg/tydz. Obniżam o 150 kcal.",
                confidence = Confidence.HIGH
            )
        }

        // 3. Stagnacja przy treningach + adherence — dodaj
        if (trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                action = AdjustmentAction.INCREASE_KCAL,
                kcalDeltaProposed = +150,
                newKcal = currentKcal + 150,
                reason = "bulk_stagnation",
                explanation = "Waga nie rośnie przez 14 dni mimo dobrej diety i treningów. " +
                    "Dodaję +150 kcal żeby wymusić anaboliczną nadwyżkę.",
                confidence = Confidence.HIGH
            )
        }

        // 4. Rośnie powoli — plan działa
        if (trend.direction == TrendDirection.RISING && !trend.isFastGain) {
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "bulk_progressing",
                explanation = "Waga rośnie w tempie ${"%.2f".format(trend.slopeKgPerWeek!!)} kg/tydz — lean bulk idealny. Kontynuuj.",
                confidence = Confidence.HIGH
            )
        }

        // 5. Niska adherence
        if (lowAdherence) {
            return AdjustmentDecision(
                action = AdjustmentAction.SIMPLIFY_PLAN,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "bulk_low_adherence",
                explanation = "Zgodność z dietą: ${adherence.avgKcalPct}% kcal, ${adherence.avgProteinPct}% białka. " +
                    "Nie jesz ile zaplanowane — zanim zwiększymy kcal, popraw wykonalność planu.",
                confidence = Confidence.HIGH
            )
        }

        return AdjustmentDecision(
            action = AdjustmentAction.HOLD,
            kcalDeltaProposed = 0,
            newKcal = currentKcal,
            reason = "bulk_default_hold",
            explanation = "Trzymaj plan, sprawdzimy za tydzień.",
            confidence = Confidence.MEDIUM
        )
    }

    private fun analyzeMaintain(
        currentKcal: Int,
        trend: WeightTrend,
        adherence: AdherenceSummary,
        highAdherence: Boolean,
        lowAdherence: Boolean
    ): AdjustmentDecision {
        // Maintain: cel stała waga
        if (trend.isFastLoss || trend.isFastGain) {
            val delta = if (trend.isFastLoss) +100 else -100
            return AdjustmentDecision(
                action = if (delta > 0) AdjustmentAction.INCREASE_KCAL else AdjustmentAction.DECREASE_KCAL,
                kcalDeltaProposed = delta,
                newKcal = currentKcal + delta,
                reason = "maintain_drift",
                explanation = "Waga się ${if (trend.isFastLoss) "obniża" else "podnosi"} szybciej niż chcesz przy MAINTAIN. " +
                    "Korekta ${if (delta > 0) "+" else ""}$delta kcal.",
                confidence = Confidence.MEDIUM
            )
        }

        return AdjustmentDecision(
            action = AdjustmentAction.HOLD,
            kcalDeltaProposed = 0,
            newKcal = currentKcal,
            reason = "maintain_stable",
            explanation = "Waga stabilna w pobliżu celu — plan działa.",
            confidence = Confidence.HIGH
        )
    }
}
