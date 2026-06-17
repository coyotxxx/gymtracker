package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import pl.filebit.gymtracker.data.repository.RecoverySnapshot
import pl.filebit.gymtracker.util.AdjustmentAction
import pl.filebit.gymtracker.util.CalorieAdjustmentEngine
import pl.filebit.gymtracker.util.TrendDirection
import pl.filebit.gymtracker.util.WeightTrend

/**
 * v2.62.0 (prośba Macieja: „różne cele, setki testów") — MATRYCA silnika korekt kcal.
 * Krzyżuje WSZYSTKIE cele × trendy wagi × adherencję × treningi × regenerację (tysiące
 * kombinacji) i sprawdza NIEZMIENNIKI, które muszą zachodzić ZAWSZE — niezależnie od danych:
 *
 *  1. brak wyjątku (silnik nie wywala się na żadnej kombinacji),
 *  2. reason NIGDY pusty (każda decyzja ma uzasadnienie),
 *  3. spójność: newKcal == currentKcal + delta; |delta| ≤ 400,
 *  4. kierunek zgodny z akcją (INCREASE→+, DECREASE→−, HOLD/SIMPLIFY→0),
 *  5. ZŁE DECYZJE: na redukcji przy zbyt szybkim spadku NIE wolno ciąć kcal;
 *     na masie przy zbyt szybkim przyroście NIE wolno dodawać kcal.
 *
 * Cel: złapać każdą złą/niespójną decyzję na całej przestrzeni danych.
 */
class CalorieEngineMatrixTest {

    private val CUR = 2400

    private fun trend(slope: Double, dir: TrendDirection,
                      stag: Boolean = false, fastLoss: Boolean = false,
                      fastGain: Boolean = false, earlyPlateau: Boolean = false) = WeightTrend(
        sampleCount = 8, avg7Days = 84.0, avg14Days = 84.3, avg28Days = 85.0,
        slopeKgPerWeek = slope, direction = dir,
        isStagnationLikely = stag, isFastLoss = fastLoss, isFastGain = fastGain,
        isEarlyPlateau = earlyPlateau, daysWithoutProgress = if (earlyPlateau) 9 else 0
    )

    private val trends = mapOf(
        "fastLoss"     to trend(-2.0, TrendDirection.FALLING, fastLoss = true),
        "slowLoss"     to trend(-0.5, TrendDirection.FALLING),
        "tinyLoss"     to trend(-0.12, TrendDirection.FALLING, earlyPlateau = true),
        "flat"         to trend(0.0, TrendDirection.FLAT, stag = true),
        "slowGain"     to trend(0.3, TrendDirection.RISING),
        "fastGain"     to trend(0.9, TrendDirection.RISING, fastGain = true),
        "noData"       to WeightTrend.NO_DATA
    )

    private fun adher(kcal: Int, prot: Int = 90, plan: Int = 0, done: Int = 0) = AdherenceSummary(
        sampleDays = 14, avgKcalPct = kcal, avgProteinPct = prot,
        avgScore = 80, highAdherenceDays = 10, workoutsPlanned = plan, workoutsDone = done
    )

    private val recos = mapOf(
        "none"        to RecoverySnapshot.EMPTY,
        "badSleep"    to RecoverySnapshot.EMPTY.copy(sampleDays = 7, avgSleepHours = 5.0, avgStress = 4.5, badSleep = true, highStress = true),
        "highHunger"  to RecoverySnapshot.EMPTY.copy(sampleDays = 7, avgHunger = 4.7, highHunger = true),
        "highSore"    to RecoverySnapshot.EMPTY.copy(sampleDays = 7, avgSoreness = 4.5, avgEnergy = 2.0, highSoreness = true),
        "highDiff"    to RecoverySnapshot.EMPTY.copy(sampleDays = 7, avgDifficulty = 4.6, highDifficulty = true)
    )

    @Test
    fun `matryca silnika - niezmienniki na wszystkich celach i danych`() {
        val goals: List<DietGoalType?> = DietGoalType.values().toList() + listOf(null)
        val kcalLevels = listOf(45, 60, 80, 100, 115, 130, 150)
        val workouts = listOf(0 to 0, 6 to 2, 6 to 6)   // (planned, done)
        val realWk = listOf<Int?>(null, 0, 6)
        val weightGoals = listOf(WeightGoalType.CUT, WeightGoalType.BULK, WeightGoalType.MAINTAIN, WeightGoalType.NONE)

        var count = 0
        val violations = mutableListOf<String>()
        val reasonCount = HashMap<String, Int>()

        for (goal in goals)
        for ((tn, t) in trends)
        for (kc in kcalLevels)
        for ((plan, done) in workouts)
        for (rw in realWk)
        for ((rn, rec) in recos)
        for (wg in weightGoals) {
            count++
            val profile = UserProfile(weightGoalType = wg, bodyweightKg = 84.0)
            val ad = adher(kc, plan = plan, done = done)
            val d = try {
                CalorieAdjustmentEngine.analyze(
                    profile = profile, currentKcal = CUR, weightTrend = t,
                    adherence14d = ad, adherence7d = ad, recovery = rec,
                    dietGoal = goal, realWorkouts14d = rw, hasTrainingPlan = (plan > 0)
                )
            } catch (e: Throwable) {
                violations += "WYJĄTEK goal=$goal trend=$tn kcal=$kc rec=$rn wg=$wg: ${e.message}"
                continue
            }
            val ctx = "goal=$goal/wg=$wg trend=$tn kcal=$kc wk=$plan/$done rw=$rw rec=$rn → ${d.action}[${d.reason}]Δ${d.kcalDeltaProposed}"
            reasonCount[d.reason] = (reasonCount[d.reason] ?: 0) + 1

            // 2. reason nigdy pusty
            if (d.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
            // 3. spójność
            if (d.newKcal != CUR + d.kcalDeltaProposed) violations += "NIESPOJNY_KCAL: $ctx (newKcal=${d.newKcal})"
            if (kotlin.math.abs(d.kcalDeltaProposed) > 400) violations += "DELTA_ZA_DUZA: $ctx"
            if (d.newKcal !in (CUR - 400)..(CUR + 400)) violations += "KCAL_POZA_ZAKRESEM: $ctx"
            // 4. kierunek zgodny z akcją
            when (d.action) {
                AdjustmentAction.INCREASE_KCAL -> if (d.kcalDeltaProposed <= 0) violations += "INCREASE_BEZ_PLUS: $ctx"
                AdjustmentAction.DECREASE_KCAL -> if (d.kcalDeltaProposed >= 0) violations += "DECREASE_BEZ_MINUS: $ctx"
                AdjustmentAction.REFEED_DAY -> if (d.kcalDeltaProposed <= 0) violations += "REFEED_BEZ_PLUS: $ctx"
                AdjustmentAction.HOLD, AdjustmentAction.SIMPLIFY_PLAN, AdjustmentAction.NEEDS_MORE_DATA ->
                    if (d.kcalDeltaProposed != 0) violations += "HOLD_Z_DELTA: $ctx"
                else -> {}
            }
            // 5. ZŁE DECYZJE — bezpieczeństwo
            val strat = goal?.let { mapStrategy(it) } ?: wg.name
            if (strat == "CUT" && t.isFastLoss && d.action == AdjustmentAction.DECREASE_KCAL)
                violations += "CIECIE_PRZY_SZYBKIEJ_UTRACIE: $ctx"
            if (strat == "BULK" && t.isFastGain && d.action == AdjustmentAction.INCREASE_KCAL)
                violations += "DODAWANIE_PRZY_SZYBKIM_PRZYROSCIE: $ctx"
        }

        println("=== MATRYCA: $count kombinacji ===")
        reasonCount.entries.sortedByDescending { it.value }.forEach { println("  ${it.key}: ${it.value}") }
        if (violations.isNotEmpty()) {
            println("\n=== NARUSZENIA (${violations.size}) ===")
            violations.take(40).forEach { println("  $it") }
        }
        assertTrue("Naruszenia niezmienników: ${violations.size}\n" + violations.take(25).joinToString("\n"),
            violations.isEmpty())
    }

    private fun mapStrategy(g: DietGoalType): String = when (g) {
        DietGoalType.FAT_LOSS, DietGoalType.EVENT_PREP -> "CUT"
        DietGoalType.MUSCLE_GAIN -> "BULK"
        else -> "MAINTAIN"
    }
}
