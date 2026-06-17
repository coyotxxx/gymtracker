package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.DietPhaseType
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.repository.PhaseManager
import pl.filebit.gymtracker.data.repository.RecoverySnapshot
import pl.filebit.gymtracker.util.TrendDirection
import pl.filebit.gymtracker.util.WeightTrend

/**
 * v2.63.0 („ciągnij dalej") — MATRYCA PhaseManager (sugestie faz diety: diet break / maintenance
 * / refeed). Krzyżuje czas trwania cutu × cel × spadek wagi × adherencję × regenerację × trening
 * i sprawdza NIEZMIENNIKI: diet break TYLKO po ≥12 tyg, maintenance TYLKO po ≥8 tyg i ≥5% spadku,
 * refeed TYLKO na CUT, kcalAdjustment sensowny (break/maint=0, refeed≥0), reason≠pusty, brak wyjątku.
 */
class PhaseManagerMatrixTest {

    private val pm = PhaseManager()
    private val day = 24L * 3600 * 1000
    private val now = System.currentTimeMillis()
    private val trend = WeightTrend(
        sampleCount = 8, avg7Days = 82.0, avg14Days = 82.3, avg28Days = 83.0,
        slopeKgPerWeek = -0.4, direction = TrendDirection.FALLING,
        isStagnationLikely = false, isFastLoss = false, isFastGain = false
    )

    private fun reco(hunger: Boolean, energy: Boolean, sleep: Boolean) = RecoverySnapshot.EMPTY.copy(
        sampleDays = 7,
        avgHunger = if (hunger) 4.5 else 2.0, highHunger = hunger,
        avgEnergy = if (energy) 1.5 else 3.5, lowEnergy = energy,
        avgSleepHours = if (sleep) 5.0 else 7.5, badSleep = sleep
    )

    @Test
    fun `matryca PhaseManager - niezmienniki faz diety`() {
        val violations = mutableListOf<String>()
        var count = 0

        val cutDurations = listOf(-1, 10, 20, 56, 84, 120) // -1 = brak fazy CUT (null)
        val goals = listOf(WeightGoalType.CUT, WeightGoalType.BULK, WeightGoalType.MAINTAIN)
        val weights = listOf(85.0 to 85.0, 85.0 to 82.0, 85.0 to 80.0, 85.0 to 78.0) // (start, current) → różny % spadku
        val adher = listOf(30, 60, 100)
        val recos = listOf(reco(false, false, false), reco(true, true, false), reco(true, false, false), reco(true, true, true))
        val heavy = listOf(false, true)
        val sinceRefeed = listOf<Int?>(null, 3, 10)

        for (cd in cutDurations)
        for (g in goals)
        for ((wStart, wCur) in weights)
        for (a in adher)
        for (r in recos)
        for (h in heavy)
        for (sr in sinceRefeed) {
            count++
            val phase = if (cd >= 0) DietPhase(type = DietPhaseType.CUT, startDateMs = now - cd.toLong() * day) else null
            val s = try {
                pm.suggest(
                    currentPhase = phase, weightGoalType = g,
                    weightAtCutStartKg = wStart, currentWeightKg = wCur,
                    weightTrend = trend, adherenceKcalPct = a, recovery = r,
                    isTodayHeavyTraining = h, trainingMesocycle = null, daysSinceLastRefeed = sr
                )
            } catch (e: Throwable) {
                violations += "WYJĄTEK cd=$cd g=$g w=$wStart/$wCur a=$a h=$h sr=$sr: ${e.message}"; continue
            }
            val cutDays = if (cd >= 0) cd else 0
            val pctDrop = (wStart - wCur) / wStart * 100
            val ctx = "cd=$cd g=$g w=$wStart→$wCur(${"%.1f".format(pctDrop)}%) a=$a h=$h sr=$sr → ${s.proposedType}[${s.reason}]"

            if (s.proposedType == null) continue
            if (s.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
            if (s.durationDays <= 0) violations += "DURATION_NIEDODATNI: $ctx"

            when (s.proposedType) {
                DietPhaseType.DIET_BREAK -> {
                    if (cutDays < 84) violations += "DIET_BREAK_ZA_WCZESNIE: $ctx (cut=$cutDays<84)"
                    if (s.kcalAdjustment != 0) violations += "DIET_BREAK_Z_KCAL: $ctx"
                }
                DietPhaseType.MAINTENANCE -> {
                    if (cutDays < 56) violations += "MAINTENANCE_ZA_WCZESNIE: $ctx"
                    if (pctDrop < 5.0) violations += "MAINTENANCE_BEZ_5PCT: $ctx"
                    if (s.kcalAdjustment != 0) violations += "MAINTENANCE_Z_KCAL: $ctx"
                }
                DietPhaseType.REFEED_DAY -> {
                    if (g != WeightGoalType.CUT) violations += "REFEED_POZA_CUT: $ctx"
                    if (s.kcalAdjustment < 0) violations += "REFEED_UJEMNY_KCAL: $ctx"
                }
                else -> {}
            }
        }

        println("=== PHASEMANAGER: $count kombinacji, naruszeń: ${violations.size} ===")
        violations.take(40).forEach { println("  $it") }
        assertTrue("Naruszenia (${violations.size}):\n" + violations.take(25).joinToString("\n"), violations.isEmpty())
    }
}
