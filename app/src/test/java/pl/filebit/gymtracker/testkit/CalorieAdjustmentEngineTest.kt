package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.AdherenceSummary
import pl.filebit.gymtracker.data.repository.NeatSnapshot
import pl.filebit.gymtracker.data.repository.RecoverySnapshot
import pl.filebit.gymtracker.util.AdjustmentAction
import pl.filebit.gymtracker.util.CalorieAdjustmentEngine
import pl.filebit.gymtracker.util.TrendDirection
import pl.filebit.gymtracker.util.WeightTrend

/**
 * v1.27 — FAZA 2.4c — testy CalorieAdjustmentEngine (silnik korekt kcal).
 *
 * `AutoAdjustmentService.analyzeNow()` to cienki wrapper: zbiera dane przez
 * analyzery i deleguje DECYZJĘ do `CalorieAdjustmentEngine.analyze()`.
 * Test pokrywa bramki silnika bezpośrednio — czysta funkcja, bez grafu AI.
 *
 * Uwaga: `analyzeNow()` jako orkiestrator (13 zależności + AI explainer)
 * nie jest pokryty E2E — to świadomy wybór, sedno logiki jest tutaj.
 */
class CalorieAdjustmentEngineTest {

    private fun trend(
        direction: TrendDirection = TrendDirection.FALLING,
        stagnation: Boolean = false,
        fastLoss: Boolean = false,
        sampleCount: Int = 6
    ) = WeightTrend(
        sampleCount = sampleCount, avg7Days = 80.0, avg14Days = 80.5, avg28Days = 81.0,
        slopeKgPerWeek = -0.3, direction = direction,
        isStagnationLikely = stagnation, isFastLoss = fastLoss, isFastGain = false
    )

    private fun adherence(sampleDays: Int = 14, kcalPct: Int = 100, proteinPct: Int = 90) =
        AdherenceSummary(
            sampleDays = sampleDays, avgKcalPct = kcalPct, avgProteinPct = proteinPct,
            avgScore = 80, highAdherenceDays = sampleDays
        )

    private val cutProfile = UserProfile(weightGoalType = WeightGoalType.CUT, bodyweightKg = 80.0)

    private fun report(name: String, decision: pl.filebit.gymtracker.util.AdjustmentDecision) {
        TraceReport("engine-$name")
            .section("DECYZJA SILNIKA")
            .verdict("action", decision.action.name, decision.reason)
            .kv("kcal", "${decision.kcalDeltaProposed} → ${decision.newKcal}")
            .kv("confidence", decision.confidence.name)
            .emit()
    }

    @Test
    fun `brak danych wagi - NEEDS_MORE_DATA`() {
        val d = CalorieAdjustmentEngine.analyze(
            profile = cutProfile, currentKcal = 2400,
            weightTrend = WeightTrend.NO_DATA,
            adherence14d = adherence(), adherence7d = adherence()
        )
        report("no-data", d)
        assertEquals(AdjustmentAction.NEEDS_MORE_DATA, d.action)
    }

    @Test
    fun `za malo dni adherence - NEEDS_MORE_DATA`() {
        val d = CalorieAdjustmentEngine.analyze(
            profile = cutProfile, currentKcal = 2400,
            weightTrend = trend(),
            adherence14d = adherence(sampleDays = 5), adherence7d = adherence(sampleDays = 5)
        )
        report("low-sample", d)
        assertEquals("5 dni < 7 wymaganych → czekaj",
            AdjustmentAction.NEEDS_MORE_DATA, d.action)
    }

    @Test
    fun `CUT zla regeneracja - sen i stres blokuja ciecie`() {
        val recovery = RecoverySnapshot.EMPTY.copy(
            sampleDays = 7, avgSleepHours = 5.0, avgStress = 4.5,
            badSleep = true, highStress = true
        )
        val d = CalorieAdjustmentEngine.analyze(
            profile = cutProfile, currentKcal = 2400,
            weightTrend = trend(stagnation = true),
            adherence14d = adherence(), adherence7d = adherence(),
            recovery = recovery
        )
        report("cut-bad-recovery", d)
        assertEquals("zły sen + stres przy CUT → HOLD",
            AdjustmentAction.HOLD, d.action)
        assertEquals("zerowa korekta kcal", 0, d.kcalDeltaProposed)
    }

    @Test
    fun `CUT wysoki glod i stagnacja - REFEED_DAY`() {
        val recovery = RecoverySnapshot.EMPTY.copy(
            sampleDays = 7, avgHunger = 4.5, highHunger = true
        )
        val d = CalorieAdjustmentEngine.analyze(
            profile = cutProfile, currentKcal = 2400,
            weightTrend = trend(stagnation = true),
            adherence14d = adherence(), adherence7d = adherence(),
            recovery = recovery
        )
        report("cut-refeed", d)
        assertEquals("głód + waga stoi → refeed zamiast cięcia",
            AdjustmentAction.REFEED_DAY, d.action)
        assertEquals("refeed +300 kcal", 300, d.kcalDeltaProposed)
    }

    @Test
    fun `brak celu wagi - silnik nie podejmuje korekt`() {
        val d = CalorieAdjustmentEngine.analyze(
            profile = UserProfile(weightGoalType = WeightGoalType.NONE, bodyweightKg = 80.0),
            currentKcal = 2400,
            weightTrend = trend(),
            adherence14d = adherence(), adherence7d = adherence(),
            recovery = RecoverySnapshot.EMPTY, neat = NeatSnapshot.EMPTY
        )
        report("no-goal", d)
        assertEquals(AdjustmentAction.HOLD, d.action)
        assertEquals("no_goal_set", d.reason)
    }
}
