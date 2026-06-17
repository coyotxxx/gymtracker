package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.RecoveryLog
import pl.filebit.gymtracker.data.repository.NeatAnalyzer
import pl.filebit.gymtracker.data.repository.RecoveryAnalyzer

/**
 * v2.63.0 — MATRYCA analizatorów (RecoveryAnalyzer + NeatAnalyzer). Te aggregatory zasilają
 * override regeneracji i NEAT w silniku diety — JEŚLI flagi liczą się źle, silnik podejmuje
 * złe decyzje. Test sprawdza, że flagi odpalają DOKŁADNIE na swoich progach i średnie są w
 * zakresie wejść (bez wyjątku/NaN).
 */
class AnalyzersMatrixTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L
    private val recovery = RecoveryAnalyzer()
    private val neat = NeatAnalyzer()

    private fun logs(sleep: Double, q: Int, stress: Int, hunger: Int, energy: Int, sore: Int, diff: Int) =
        (0 until 7).map { i ->
            RecoveryLog(dateMs = now - i * day, sleepHours = sleep, sleepQuality = q,
                stressLevel = stress, hungerLevel = hunger, energyLevel = energy,
                sorenessLevel = sore, difficultyAdherence = diff)
        }

    @Test
    fun `matryca RecoveryAnalyzer - flagi dokladnie na progach`() {
        val violations = mutableListOf<String>()
        var count = 0
        for (sleep in listOf(4.0, 5.5, 6.0, 6.5, 8.0))
        for (q in listOf(1, 2, 3, 4))
        for (stress in listOf(2, 3, 4, 5))
        for (hunger in listOf(2, 3, 4, 5))
        for (energy in listOf(1, 2, 3, 4))
        for (sore in listOf(3, 4))
        for (diff in listOf(3, 4)) {
            count++
            val s = recovery.analyze(logs(sleep, q, stress, hunger, energy, sore, diff))
            val ctx = "sleep=$sleep q=$q stress=$stress hunger=$hunger energy=$energy sore=$sore diff=$diff"
            // progi (z dokumentacji RecoverySnapshot)
            if (s.badSleep != (sleep < 6.0 || q < 2.5)) violations += "badSleep zły: $ctx → ${s.badSleep}"
            if (s.highStress != (stress >= 4)) violations += "highStress zły: $ctx → ${s.highStress}"
            if (s.highHunger != (hunger >= 4)) violations += "highHunger zły: $ctx → ${s.highHunger}"
            if (s.lowEnergy != (energy <= 2)) violations += "lowEnergy zły: $ctx → ${s.lowEnergy}"
            if (s.highSoreness != (sore >= 4)) violations += "highSoreness zły: $ctx → ${s.highSoreness}"
            if (s.highDifficulty != (diff >= 4)) violations += "highDifficulty zły: $ctx → ${s.highDifficulty}"
            // średnie = wartość (wszystkie logi takie same)
            if (s.avgSleepHours != null && kotlin.math.abs(s.avgSleepHours!! - sleep) > 0.001)
                violations += "avgSleep zły: $ctx → ${s.avgSleepHours}"
            if (!s.hasEnoughData) violations += "7 logów to za mało danych?! $ctx"
        }
        println("=== RECOVERY: $count kombinacji, naruszeń: ${violations.size} ===")
        violations.take(30).forEach { println("  $it") }
        assertTrue("Recovery naruszenia (${violations.size}):\n" + violations.take(20).joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `matryca NeatAnalyzer - spadek krokow i below baseline`() {
        val violations = mutableListOf<String>()
        var count = 0
        // wzorce: stałe, 30% spadek (ostatnie 14d), 50% spadek, wzrost
        val patterns = mapOf(
            "flat"  to (List(30) { 10000 }),
            "drop30" to (List(16) { 10000 } + List(14) { 6500 }),  // ostatnie 14 niżej
            "drop50" to (List(16) { 10000 } + List(14) { 4500 }),
            "rise"  to (List(16) { 6000 } + List(14) { 11000 }),
            "few"   to (List(8) { 9000 })  // za mało danych
        )
        for ((pn, steps) in patterns) for (baseline in listOf(0, 5000, 8000, 12000)) {
            count++
            val s = neat.analyze(steps, baseline)
            val ctx = "pattern=$pn baseline=$baseline avg14=${s.avg14dSteps} avg30=${s.avg30dSteps}"
            // significantStepsDrop iff hasEnoughData && avg14 <= 0.7*avg30
            val expectDrop = s.hasEnoughData && s.avg30dSteps > 0 && s.avg14dSteps <= 0.7 * s.avg30dSteps
            if (s.significantStepsDrop != expectDrop) violations += "stepsDrop zły: $ctx → ${s.significantStepsDrop} (oczek. $expectDrop)"
            val expectBelow = s.hasEnoughData && baseline > 0 && s.avg14dSteps <= 0.7 * baseline
            if (s.belowBaseline != expectBelow) violations += "belowBaseline zły: $ctx → ${s.belowBaseline} (oczek. $expectBelow)"
            if (s.avg14dSteps < 0 || s.avg30dSteps < 0) violations += "ujemne kroki: $ctx"
        }
        println("=== NEAT: $count kombinacji, naruszeń: ${violations.size} ===")
        violations.take(30).forEach { println("  $it") }
        assertTrue("NEAT naruszenia (${violations.size}):\n" + violations.take(20).joinToString("\n"), violations.isEmpty())
    }
}
