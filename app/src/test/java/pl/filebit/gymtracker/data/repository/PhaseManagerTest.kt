package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.util.TrendDirection
import pl.filebit.gymtracker.util.WeightTrend

class PhaseManagerTest {

    private val manager = PhaseManager()

    private fun cut(daysAgo: Int): DietPhase {
        val nowMs = System.currentTimeMillis()
        return DietPhase(
            id = 1, type = DietPhaseType.CUT,
            startDateMs = nowMs - daysAgo * 24L * 3600 * 1000,
            endDateMs = null,
            kcalAdjustment = -500,
            expectedRateKgPerWeek = -0.5
        )
    }

    private val flatTrend = WeightTrend(
        sampleCount = 10,
        avg7Days = 80.0, avg14Days = 80.0, avg28Days = 80.0,
        slopeKgPerWeek = 0.0,
        direction = TrendDirection.FLAT,
        isStagnationLikely = true,
        isFastLoss = false,
        isFastGain = false
    )

    private fun recovery(
        hunger: Double? = null,
        energy: Double? = null,
        sleep: Double? = null,
        days: Int = 5
    ) = RecoverySnapshot(
        sampleDays = days,
        avgSleepHours = sleep,
        avgSleepQuality = if (sleep != null && sleep < 6) 2.0 else 3.5,
        avgStress = 3.0,
        avgHunger = hunger,
        avgEnergy = energy,
        avgSoreness = 3.0,
        avgDifficulty = 3.0,
        badSleep = sleep != null && sleep < 6,
        highStress = false,
        highHunger = hunger != null && hunger >= 4.0,
        lowEnergy = energy != null && energy <= 2.0,
        highSoreness = false,
        highDifficulty = false
    )

    @Test
    fun `no current phase + maintain goal = NONE`() {
        val s = manager.suggest(
            currentPhase = null,
            weightGoalType = WeightGoalType.MAINTAIN,
            weightAtCutStartKg = null,
            currentWeightKg = 80.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery()
        )
        assertNull(s.proposedType)
    }

    @Test
    fun `cut 12 weeks + low adherence + high hunger = DIET_BREAK strong`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 90),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 84.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 65,
            recovery = recovery(hunger = 4.5)
        )
        assertEquals(DietPhaseType.DIET_BREAK, s.proposedType)
        assertEquals(14, s.durationDays)
        assertTrue(s.isStrong)
    }

    @Test
    fun `cut 14 weeks + bad sleep + low energy = DIET_BREAK`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 100),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 85.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 85,
            recovery = recovery(sleep = 5.5, energy = 2.0)
        )
        assertEquals(DietPhaseType.DIET_BREAK, s.proposedType)
    }

    @Test
    fun `cut 12 weeks + only one trigger = NO diet break`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 90),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 84.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5)  // tylko jeden trigger
        )
        // 1 trigger → NIE DIET_BREAK, ale może spełnić MAINTENANCE (waga -6.7%)
        assertEquals(DietPhaseType.MAINTENANCE, s.proposedType)
    }

    @Test
    fun `cut 9 weeks + waga -6 percent = MAINTENANCE`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 63),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 84.5,  // -6.1%
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery()
        )
        assertEquals(DietPhaseType.MAINTENANCE, s.proposedType)
        assertEquals(10, s.durationDays)
        assertTrue(s.isStrong)
    }

    @Test
    fun `cut 9 weeks + waga only -3 percent = NO maintenance`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 63),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 87.3,  // -3%
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery()
        )
        assertNull("Should be NONE — under 5% drop", s.proposedType)
    }

    @Test
    fun `cut + high hunger + low energy = REFEED`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 88.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5, energy = 2.0)
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals(1, s.durationDays)
        assertEquals(300, s.kcalAdjustment)
    }

    @Test
    fun `cut + heavy training day + high hunger = REFEED`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 14),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 87.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(hunger = 4.5),
            isTodayHeavyTraining = true
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
    }

    @Test
    fun `bulk goal + waga stoi = NO suggestion`() {
        val s = manager.suggest(
            currentPhase = null,
            weightGoalType = WeightGoalType.BULK,
            weightAtCutStartKg = null,
            currentWeightKg = 80.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery()
        )
        assertNull(s.proposedType)
    }

    @Test
    fun `cut 4 weeks - too early for break or maintenance`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 28),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 87.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery()
        )
        assertNull(s.proposedType)
    }

    @Test
    fun `diet_break suggestion has explanation in Polish`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 90),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 84.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 60,
            recovery = recovery(hunger = 4.5)
        )
        assertTrue(s.explanation.contains("diet break"))
        assertTrue(s.explanation.contains("leptyn"))
    }

    // === v1.17.0 — Phase-aware REFEED kcal + reguła 4 (auto refeed) ===

    private fun meso(
        phase: MesocyclePhase,
        weekInPhase: Int = 2,
        phaseLengthWeeks: Int = 3,
        status: MesocycleStatus = MesocycleStatus.ACTIVE
    ): TrainingMesocycle {
        val now = System.currentTimeMillis()
        return TrainingMesocycle(
            id = 1L,
            startDateMs = now - 14L * 24 * 3600 * 1000,
            plannedEndDateMs = now + 7L * 24 * 3600 * 1000,
            phase = phase,
            weekInPhase = weekInPhase,
            phaseLengthWeeks = phaseLengthWeeks,
            status = status
        )
    }

    @Test
    fun `REFEED w INTENSIFICATION = 400 kcal`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 88.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5, energy = 2.0),
            trainingMesocycle = meso(MesocyclePhase.INTENSIFICATION)
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals(400, s.kcalAdjustment)
        assertTrue("explanation zawiera fazę", s.explanation.contains("intensyfikacja"))
    }

    @Test
    fun `REFEED w DELOAD = 150 kcal (zredukowane)`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 88.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5, energy = 2.0),
            trainingMesocycle = meso(MesocyclePhase.DELOAD)
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals(150, s.kcalAdjustment)
        assertTrue(s.explanation.contains("deload"))
    }

    @Test
    fun `REFEED w ACCUMULATION = 300 kcal (status quo)`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 88.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5, energy = 2.0),
            trainingMesocycle = meso(MesocyclePhase.ACCUMULATION)
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals(300, s.kcalAdjustment)
    }

    @Test
    fun `REFEED bez mesocyklu = 300 kcal (backward compat)`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 90.0,
            currentWeightKg = 88.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 90,
            recovery = recovery(hunger = 4.5, energy = 2.0),
            trainingMesocycle = null
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals(300, s.kcalAdjustment)
    }

    @Test
    fun `auto REFEED w heavy day - cut 14d + heavy + bez wczesniejszego refeed`() {
        // BEZ wysokiego głodu! Reguła 4 (auto) odpala bez sygnałów recovery.
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 14),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 86.5,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(),  // brak triggerów
            isTodayHeavyTraining = true,
            trainingMesocycle = meso(MesocyclePhase.INTENSIFICATION),
            daysSinceLastRefeed = null  // brak wczesniejszego refeed
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals("auto_refeed_heavy_day", s.reason)
        assertEquals(400, s.kcalAdjustment)  // INTENSIFICATION
    }

    @Test
    fun `auto REFEED NIE odpala gdy ostatni REFEED 3 dni temu`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 85.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(),
            isTodayHeavyTraining = true,
            trainingMesocycle = meso(MesocyclePhase.ACCUMULATION),
            daysSinceLastRefeed = 3  // za niedawno
        )
        assertNull("REFEED nie powinien się odpalić — 3 dni < 7 dni", s.proposedType)
    }

    @Test
    fun `auto REFEED odpala gdy ostatni REFEED 8 dni temu`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 85.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(),
            isTodayHeavyTraining = true,
            trainingMesocycle = meso(MesocyclePhase.ACCUMULATION),
            daysSinceLastRefeed = 8
        )
        assertEquals(DietPhaseType.REFEED_DAY, s.proposedType)
        assertEquals("auto_refeed_heavy_day", s.reason)
    }

    @Test
    fun `auto REFEED NIE odpala bez heavy training`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 30),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 85.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(),
            isTodayHeavyTraining = false,  // nie heavy
            trainingMesocycle = meso(MesocyclePhase.INTENSIFICATION),
            daysSinceLastRefeed = 10
        )
        assertNull(s.proposedType)
    }

    @Test
    fun `auto REFEED NIE odpala dla cutu krótszego niż 14 dni`() {
        val s = manager.suggest(
            currentPhase = cut(daysAgo = 7),
            weightGoalType = WeightGoalType.CUT,
            weightAtCutStartKg = 88.0,
            currentWeightKg = 87.0,
            weightTrend = flatTrend,
            adherenceKcalPct = 95,
            recovery = recovery(),
            isTodayHeavyTraining = true,
            trainingMesocycle = meso(MesocyclePhase.ACCUMULATION),
            daysSinceLastRefeed = 20
        )
        assertNull("cut 7 dni < 14 — za wcześnie na auto refeed", s.proposedType)
    }
}
