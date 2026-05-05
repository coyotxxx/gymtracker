package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.AdherenceSummary

/**
 * Baseline tests dla CalorieAdjustmentEngine (v0.89.50).
 * MUSZĄ przechodzić PRZED jakąkolwiek modyfikacją silnika w v0.90+.
 */
class CalorieAdjustmentEngineTest {

    private fun profile(goal: WeightGoalType, kcal: Int = 2400): UserProfile = UserProfile(
        id = 1,
        displayName = "Test",
        gender = Gender.MALE,
        bodyweightKg = 80.0,
        targetWeightKg = 75.0,
        weightGoalType = goal,
        goal = TrainingGoal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE,
        daysPerWeek = 4
    )

    private fun trend(
        slope: Double? = 0.0,
        avg14: Double? = 80.0,
        samples: Int = 5,
        isStagnation: Boolean = false,
        isFastLoss: Boolean = false,
        isFastGain: Boolean = false,
        direction: TrendDirection = TrendDirection.STABLE
    ) = WeightTrend(
        sampleCount = samples,
        avg7Days = avg14,
        avg14Days = avg14,
        avg28Days = avg14,
        direction = direction,
        slopeKgPerWeek = slope,
        isStagnationLikely = isStagnation,
        isFastLoss = isFastLoss,
        isFastGain = isFastGain
    )

    private fun adherence(
        kcal: Int = 90,
        protein: Int = 85,
        days: Int = 14,
        workoutsDone: Int = 4,
        workoutsPlanned: Int = 4
    ) = AdherenceSummary(
        sampleDays = days,
        avgKcalPct = kcal,
        avgProteinPct = protein,
        workoutsDone = workoutsDone,
        workoutsPlanned = workoutsPlanned
    )

    @Test
    fun `insufficient data returns NEEDS_MORE_DATA`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2400,
            weightTrend = trend(direction = TrendDirection.INSUFFICIENT_DATA, samples = 1),
            adherence14d = adherence(days = 3),
            adherence7d = adherence(days = 3)
        )
        assertEquals(AdjustmentAction.NEEDS_MORE_DATA, decision.action)
        assertEquals(0, decision.kcalDeltaProposed)
    }

    @Test
    fun `cut + stagnation + high adherence + workouts done → DECREASE_KCAL -150`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2400,
            weightTrend = trend(slope = 0.0, isStagnation = true, direction = TrendDirection.STABLE),
            adherence14d = adherence(kcal = 95, protein = 90),
            adherence7d = adherence(kcal = 95, protein = 90)
        )
        assertEquals(AdjustmentAction.DECREASE_KCAL, decision.action)
        assertEquals(-150, decision.kcalDeltaProposed)
        assertEquals(2250, decision.newKcal)
    }

    @Test
    fun `cut + low adherence → SIMPLIFY_PLAN, NIE DECREASE`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2400,
            weightTrend = trend(slope = 0.0, isStagnation = true, direction = TrendDirection.STABLE),
            adherence14d = adherence(kcal = 60, protein = 50),
            adherence7d = adherence(kcal = 60, protein = 50)
        )
        assertEquals(AdjustmentAction.SIMPLIFY_PLAN, decision.action)
        assertEquals(0, decision.kcalDeltaProposed)
        assertTrue(decision.warnings.isNotEmpty())
    }

    @Test
    fun `cut + fast loss (>1_5 kg per week) → INCREASE_KCAL +150 (chronimy mięśnie)`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2200,
            weightTrend = trend(slope = -1.8, isFastLoss = true, direction = TrendDirection.FALLING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.INCREASE_KCAL, decision.action)
        assertEquals(+150, decision.kcalDeltaProposed)
    }

    @Test
    fun `cut + stagnation + low workouts → HOLD (najpierw treningi)`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2400,
            weightTrend = trend(slope = 0.0, isStagnation = true, direction = TrendDirection.STABLE),
            adherence14d = adherence(kcal = 95, protein = 90, workoutsDone = 2, workoutsPlanned = 4),
            adherence7d = adherence(workoutsDone = 1, workoutsPlanned = 2)
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
        assertTrue(decision.warnings.any { it.contains("trening", ignoreCase = true) })
    }

    @Test
    fun `cut + falling slowly → HOLD (plan działa)`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2200,
            weightTrend = trend(slope = -0.4, direction = TrendDirection.FALLING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
    }

    @Test
    fun `bulk + stagnation + high adherence + workouts → INCREASE_KCAL +150`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.BULK),
            currentKcal = 3000,
            weightTrend = trend(slope = 0.0, isStagnation = true, direction = TrendDirection.STABLE),
            adherence14d = adherence(kcal = 95, protein = 90),
            adherence7d = adherence(kcal = 95, protein = 90)
        )
        assertEquals(AdjustmentAction.INCREASE_KCAL, decision.action)
        assertEquals(+150, decision.kcalDeltaProposed)
    }

    @Test
    fun `bulk + low workouts → HOLD (bulk bez treningów = tłuszcz)`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.BULK),
            currentKcal = 3000,
            weightTrend = trend(slope = 0.0, isStagnation = true, direction = TrendDirection.STABLE),
            adherence14d = adherence(workoutsDone = 1, workoutsPlanned = 4),
            adherence7d = adherence(workoutsDone = 1, workoutsPlanned = 4)
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
    }

    @Test
    fun `bulk + fast gain (over 0_5 kg week) → DECREASE_KCAL`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.BULK),
            currentKcal = 3200,
            weightTrend = trend(slope = 0.7, isFastGain = true, direction = TrendDirection.RISING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.DECREASE_KCAL, decision.action)
    }

    @Test
    fun `bulk + rising slowly → HOLD (lean bulk OK)`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.BULK),
            currentKcal = 3000,
            weightTrend = trend(slope = 0.3, direction = TrendDirection.RISING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
    }

    @Test
    fun `maintain + drift down → INCREASE_KCAL`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.MAINTAIN),
            currentKcal = 2500,
            weightTrend = trend(slope = -1.6, isFastLoss = true, direction = TrendDirection.FALLING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.INCREASE_KCAL, decision.action)
    }

    @Test
    fun `maintain + stable → HOLD`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.MAINTAIN),
            currentKcal = 2500,
            weightTrend = trend(slope = 0.05, direction = TrendDirection.STABLE),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
    }

    @Test
    fun `none goal → HOLD always`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.NONE),
            currentKcal = 2400,
            weightTrend = trend(),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertEquals(AdjustmentAction.HOLD, decision.action)
        assertEquals(0, decision.kcalDeltaProposed)
    }

    @Test
    fun `decision always produces explanation and reason`() {
        val decision = CalorieAdjustmentEngine.analyze(
            profile = profile(WeightGoalType.CUT),
            currentKcal = 2400,
            weightTrend = trend(slope = -0.5, direction = TrendDirection.FALLING),
            adherence14d = adherence(),
            adherence7d = adherence()
        )
        assertTrue(decision.explanation.isNotBlank())
        assertTrue(decision.reason.isNotBlank())
    }
}
