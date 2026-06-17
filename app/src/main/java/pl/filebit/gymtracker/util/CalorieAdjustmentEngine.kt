package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import pl.filebit.gymtracker.data.repository.NeatSnapshot
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
/** Kierunek strategii kalorycznej — 8 typów [DietGoalType] mapuje się na 3. */
enum class CalorieStrategy { CUT, BULK, MAINTAIN }

/**
 * Mapuje cel diety (8 typów [DietGoalType]) na strategię korekty kalorii.
 * v1.27.0: wcześniej silnik korekt znał tylko WeightGoalType (4 wartości),
 * więc RECOMP/STRENGTH/ENDURANCE/HEALTH/EVENT_PREP były ignorowane —
 * user z celem rekompozycji nie dostawał właściwych korekt.
 */
fun DietGoalType.toCalorieStrategy(): CalorieStrategy = when (this) {
    DietGoalType.FAT_LOSS, DietGoalType.EVENT_PREP -> CalorieStrategy.CUT
    DietGoalType.MUSCLE_GAIN -> CalorieStrategy.BULK
    // RECOMP: waga ma STAĆ (mięśnie ↑, tłuszcz ↓) — monitorujemy jak
    // utrzymanie, żeby silnik nie panikował "stagnacja" widząc stałą wagę
    // (dla recompu to cel, nie problem). STRENGTH/HEALTH/ENDURANCE też
    // celują w stabilną wagę.
    DietGoalType.RECOMP, DietGoalType.MAINTAIN, DietGoalType.STRENGTH,
    DietGoalType.HEALTH, DietGoalType.ENDURANCE -> CalorieStrategy.MAINTAIN
}

object CalorieAdjustmentEngine {

    fun analyze(
        profile: UserProfile,
        currentKcal: Int,
        weightTrend: WeightTrend,
        adherence14d: AdherenceSummary,
        adherence7d: AdherenceSummary,
        recovery: RecoverySnapshot = RecoverySnapshot.EMPTY,
        hydrationAdherencePct: Int = 100,
        neat: NeatSnapshot = NeatSnapshot.EMPTY,
        /** v1.24.23: liczba dni od początku obecnego CUT (z dietProfile.onboardingCompletedAt).
         *  ≥56 dni + brak ostatniego refeed → propose diet break (ISSN/Helms). */
        cutDurationDays: Int = 0,
        /** v1.24.23: dni od ostatniego REFEED_DAY/diet break. Domyślnie 999 (brak). */
        daysSinceLastRefeed: Int = 999,
        /** v1.27.0: cel diety (8 typów). Gdy podany — wyznacza strategię korekt.
         *  Gdy null — fallback do `profile.weightGoalType` (cel wagowy treningu). */
        dietGoal: DietGoalType? = null,
        /** U7 (v2.57.0): PEWNE źródło faktycznego treningu — liczba ukończonych
         *  `Workout` w ostatnich 14 dniach (z WorkoutDao). Gdy podane razem z
         *  `hasTrainingPlan`, zastępuje zawodny sygnał z adherence (TrainingDaySummary
         *  tworzony leniwie). null = brak danych → fallback do adherence (testy). */
        realWorkouts14d: Int? = null,
        /** U7 (v2.57.0): czy istnieje aktywny plan treningowy (≥1 dzień/tydz). Bez planu
         *  nie wnioskujemy „nie trenujesz" (mógł świadomie nie planować treningu). */
        hasTrainingPlan: Boolean = false
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

        // v1.27.0: strategia korekt z celu DIETY (8 typów). Brak dietProfile →
        // fallback do WeightGoalType (cel wagowy z onboardingu treningu).
        val strategy: CalorieStrategy? = dietGoal?.toCalorieStrategy()
            ?: when (profile.weightGoalType) {
                WeightGoalType.CUT -> CalorieStrategy.CUT
                WeightGoalType.BULK -> CalorieStrategy.BULK
                WeightGoalType.MAINTAIN -> CalorieStrategy.MAINTAIN
                WeightGoalType.NONE -> null
            }

        // === Recovery override — przed cięciem kcal sprawdzamy regenerację ===
        if (recovery.hasEnoughData && strategy == CalorieStrategy.CUT) {
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
            // NEAT drop: jeśli waga stoi a kroki spadły >30% → NIE TNIJ kcal, najpierw kroki
            if (neat.hasEnoughData && neat.significantStepsDrop && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    action = AdjustmentAction.HOLD,
                    kcalDeltaProposed = 0,
                    newKcal = currentKcal,
                    reason = "cut_neat_drop",
                    explanation = "Waga stoi, ale Twoje kroki spadły z ${neat.avg30dSteps}/dzień (30d) do ${neat.avg14dSteps}/dzień (14d) — ${100 - neat.avg14dSteps * 100 / neat.avg30dSteps}% mniej. To NEAT spadł, nie metabolizm. Wróć do kroków, nie tnij jedzenia.",
                    confidence = Confidence.HIGH,
                    warnings = listOf("Spadek kroków powoduje stagnację — najpierw popraw NEAT, dopiero potem kcal.")
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
        // U7 (unified coach): dietetyk widzi FAKTYCZNY trening. „Nie trenuje" = MA plan, ale go
        // nie realizuje (<2 ukończone treningi w 14 dni). Bez planu nie wnioskujemy
        // (trainingActive=true). Wtedy w redukcji chronimy mięśnie zamiast nagabywać „idź ćwiczyć".
        // Źródło PEWNE: realWorkouts14d z Workout (gdy podane). Fallback: adherence (TrainingDaySummary
        // tworzony leniwie, bywa pusty) — tylko gdy realWorkouts14d == null (np. testy jednostkowe).
        val trainingActive = when {
            realWorkouts14d != null && hasTrainingPlan -> realWorkouts14d >= 2
            realWorkouts14d != null && !hasTrainingPlan -> true // bez planu nie wnioskujemy
            else -> adherence14d.workoutsPlanned == 0 || adherence14d.workoutsDone >= 2
        }

        return when (strategy) {
            CalorieStrategy.CUT -> analyzeCut(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence, workoutsCompletionPct, trainingActive, realWorkouts14d, cutDurationDays, daysSinceLastRefeed)
            CalorieStrategy.BULK -> analyzeBulk(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence, workoutsCompletionPct)
            CalorieStrategy.MAINTAIN -> analyzeMaintain(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence)
            null -> AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "no_goal_set",
                explanation = "Brak ustalonego celu — silnik nie podejmuje korekt.",
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
        workoutsPct: Int,
        trainingActive: Boolean = true,
        realWorkouts14d: Int? = null,
        cutDurationDays: Int = 0,
        daysSinceLastRefeed: Int = 999
    ): AdjustmentDecision {

        // v1.24.23 fix #1: Diet break automatyczny po 8 tyg CUT.
        // ISSN/Helms/RP: długi cut (>8 tyg) bez refeed/diet break = utrata
        // mięśni, spadek leptyny, metabolic adaptation. Refeed na 7-14 dni
        // do maintenance kcal odbudowuje hormony, zmniejsza zmęczenie psychiczne.
        //
        // Warunki proposal:
        // - cutDurationDays ≥ 56 (8 tyg)
        // - daysSinceLastRefeed ≥ 21 (od ostatniego break minęły 3 tyg)
        // - adherence wysokie (user trzymał plan — break ma sens, gdy plan działał)
        // - waga spadła (jeśli stoi/rośnie, najpierw inny adjustment)
        val cutWeeks = cutDurationDays / 7
        val weightDropped = trend.direction == TrendDirection.FALLING ||
            (trend.avg14Days != null && trend.avg28Days != null && trend.avg14Days < trend.avg28Days)
        if (cutDurationDays >= 56 &&
            daysSinceLastRefeed >= 21 &&
            highAdherence &&
            weightDropped &&
            !trend.isFastLoss) {
            val maintenanceKcal = currentKcal + 500  // przybliżenie: deficyt ~500 → +500 = maintenance
            return AdjustmentDecision(
                action = AdjustmentAction.REFEED_DAY,
                kcalDeltaProposed = +500,
                newKcal = maintenanceKcal,
                reason = "cut_diet_break_8w",
                explanation = "Już $cutWeeks tyg na redukcji — czas na 'diet break' (7-14 dni). " +
                    "Wróć do maintenance (~$maintenanceKcal kcal), żeby odbudować leptynę, " +
                    "zatrzymać metabolic adaptation i odpocząć psychicznie. " +
                    "Badania (Helms, ISSN): cykliczne diet breaks zachowują mięśnie i przyspieszają długoterminową redukcję.",
                confidence = Confidence.HIGH,
                warnings = listOf("Po 7-14 dniach maintenance wracaj do tych samych kcal co teraz — silnik wykryje nowy stan i ponownie zaproponuje korekty.")
            )
        }

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

        // U7 (unified coach): NIE TRENUJESZ w redukcji → chroń mięśnie, nie nagabuj „idź ćwiczyć".
        // Dietetyk reaguje na fakt braku treningu: bez bodźca siłowego deficyt zżera mięśnie.
        if (!trainingActive) {
            val slope = trend.slopeKgPerWeek
            if (slope != null && slope <= -0.4) {
                return AdjustmentDecision(
                    action = AdjustmentAction.INCREASE_KCAL,
                    kcalDeltaProposed = +150,
                    newKcal = currentKcal + 150,
                    reason = "cut_no_training_protect_muscle",
                    explanation = "Nie trenujesz (${realWorkouts14d ?: adherence.workoutsDone} treningów w 14 dni), a chudniesz ${"%.2f".format(-slope)} kg/tydz. " +
                        "Bez treningu siłowego taki deficyt zżera mięśnie — łagodzę go o +150 kcal i trzymaj wysokie białko (≥2 g/kg masy). " +
                        "Jeśli znajdziesz nawet 2× 20 min w tygodniu, ochronisz formę i przyspieszysz.",
                    confidence = Confidence.HIGH,
                    warnings = listOf("Redukcja bez treningu siłowego = ryzyko utraty mięśni. Białko + minimalny ruch to ochrona.")
                )
            }
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "cut_no_training_hold",
                explanation = "Nie trenujesz, więc nie obniżam dalej kalorii — bez treningu cięcie zżera mięśnie. " +
                    "Utrzymaj wysokie białko (≥2 g/kg) i codzienne kroki. Gdy wrócisz do treningu, dostosujemy tempo redukcji.",
                confidence = Confidence.MEDIUM
            )
        }

        // 3. Stagnacja 14-dni + adherence wysokie + treningi wykonane → obniż
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

        // 3b. v1.24.20: WCZESNY plateau 7-dni + high adherence → lekka korekta lub
        // wait-and-see. Sygnał Macieja: 'po 7 dniach bez efektów' powinno reagować
        // wcześniej niż 14-dniowy stagnationLikely.
        if (trend.isEarlyPlateau && !trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                action = AdjustmentAction.DECREASE_KCAL,
                kcalDeltaProposed = -100,
                newKcal = currentKcal - 100,
                reason = "cut_early_plateau_7d",
                explanation = "Waga w ostatnich 7 dniach praktycznie stoi (${trend.daysWithoutProgress} dni bez zmiany ≥0.2 kg). " +
                    "Zgodność z dietą ${adherence.avgKcalPct}%, treningi ${adherence.workoutsDone}/${adherence.workoutsPlanned} — wszystko OK, ale plan się 'zatyka'. " +
                    "Mała korekta −100 kcal teraz, żeby nie czekać 2 tygodnie. Jeśli waga ruszy w ciągu 4-5 dni — wracamy do poprzedniej.",
                confidence = Confidence.MEDIUM,
                warnings = listOf("Wczesny sygnał — jeśli to tylko retencja wody (sól/stres), waga ruszy sama bez korekty.")
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

        // 6. v2.62.0 (sweep): PRZEJADASZ cel — kcal wyraźnie powyżej planu (>115%) i waga NIE spada
        //    (rośnie lub stoi). Problem to SPOŻYCIE, nie cel — NIE obniżam targetu (i tak go
        //    przekraczasz), tylko mówię wprost. Wcześniej wpadało w cichy default_hold (brak reakcji).
        if (trend.direction != TrendDirection.FALLING && adherence.avgKcalPct > 115) {
            val rising = trend.direction == TrendDirection.RISING
            return AdjustmentDecision(
                action = AdjustmentAction.HOLD,
                kcalDeltaProposed = 0,
                newKcal = currentKcal,
                reason = "cut_overeating",
                explanation = "Jesz średnio ${adherence.avgKcalPct}% celu kalorii (powyżej planu), a waga " +
                    "${if (rising) "rośnie" else "stoi"} — na redukcji nie ma wtedy deficytu. " +
                    "Problem to spożycie, nie cel: NIE obniżam targetu ($currentKcal kcal), bo i tak go przekraczasz. " +
                    "Wróćmy do trzymania kalorii — to ruszy wagę.",
                confidence = Confidence.HIGH,
                warnings = listOf("Spożycie powyżej celu = brak deficytu mimo 'redukcji'.")
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
