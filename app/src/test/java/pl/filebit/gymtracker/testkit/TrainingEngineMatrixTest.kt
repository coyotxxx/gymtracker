package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.util.DeloadSeverity
import pl.filebit.gymtracker.util.WorkoutPainSnapshot
import pl.filebit.gymtracker.util.detectActiveInjury
import pl.filebit.gymtracker.util.detectDeloadNeed
import pl.filebit.gymtracker.util.detectMissedWorkouts
import pl.filebit.gymtracker.util.detectReturnAfterBreak

/**
 * v2.62.0 (Maciej: „matryca silnika treningowego") — MATRYCA detektorów treningu.
 * Krzyżuje pełne zakresy wejść 4 czystych detektorów (deload / powrót po przerwie / kontuzja /
 * opuszczony trening) i sprawdza NIEZMIENNIKI + ZŁE DECYZJE, które nie mogą się zdarzyć:
 *
 *  - brak wyjątku na żadnej kombinacji,
 *  - każda zwrócona rekomendacja ma niepusty `reason`,
 *  - DELOAD nie odpala dla ŚWIEŻEGO usera (mało sesji, brak realnej historii) — guard B1,
 *  - DELOAD severity HIGH wymaga realnie dużego wolumenu (≥15 sesji/35dni),
 *  - POWRÓT PO PRZERWIE nie odpala dla AKTYWNIE trenującego (>2 sesje/14dni),
 *  - OPUSZCZONY TRENING zwraca spójne liczby (missed≤planned), null gdy brak planu/braków,
 *  - KONTUZJA tylko gdy realnie zanotowano ból.
 */
class TrainingEngineMatrixTest {

    @Test
    fun `matryca detektorow treningu - niezmienniki i zle decyzje`() {
        val violations = mutableListOf<String>()
        var count = 0

        // === 1. detectDeloadNeed ===
        val rpes = listOf<Double?>(null, 6.0, 7.5, 8.5, 9.0, 10.0)
        val s14 = listOf(0, 1, 2, 3, 6, 10)
        val s35 = listOf(0, 3, 8, 9, 15, 18, 25)
        val stags = listOf(0, 1, 3, 5)
        val goals = listOf(null, WeightGoalType.CUT, WeightGoalType.BULK, WeightGoalType.MAINTAIN)
        for (rpe in rpes) for (a in s14) for (b in s35) for (st in stags) for (g in goals) {
            if (a > b) continue // sesje 14d nie mogą > 35d
            count++
            val r = try { detectDeloadNeed(rpe, a, b, st, g) }
                catch (e: Throwable) { violations += "DELOAD WYJĄTEK rpe=$rpe s14=$a s35=$b st=$st g=$g: ${e.message}"; continue }
            val ctx = "deload rpe=$rpe s14=$a s35=$b stag=$st goal=$g → ${r?.severity}"
            if (r != null) {
                if (r.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
                // ŚWIEŻY user: <3 sesje/14d i <3 stagnacji → deloadu nie powinno być
                if (a < 3 && st < 3) violations += "DELOAD_DLA_SWIEZEGO: $ctx"
                // stagnacja-deload wymaga realnej historii (≥9 sesji/35d)
                if (r.severity == DeloadSeverity.MED && a < 3 && st >= 3 && b < 9)
                    violations += "STAGNACJA_BEZ_HISTORII: $ctx"
                // HIGH wymaga dużego wolumenu
                if (r.severity == DeloadSeverity.HIGH && b < 15) violations += "HIGH_BEZ_WOLUMENU: $ctx"
            }
        }

        // === 2. detectReturnAfterBreak ===
        val days = listOf<Int?>(null, 0, 3, 7, 14, 30, 60)
        val s70 = listOf<Int?>(null, 0, 6, 10)
        for (a in s14) for (b in s35) for (d in days) for (c70 in s70) {
            if (a > b) continue
            count++
            val r = try { detectReturnAfterBreak(a, b, d, c70) }
                catch (e: Throwable) { violations += "RETURN WYJĄTEK s14=$a s35=$b d=$d s70=$c70: ${e.message}"; continue }
            val ctx = "return s14=$a s35=$b days=$d s70=$c70 → ${r?.severity}"
            if (r != null) {
                if (r.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
                // aktywnie trenujący (>2 sesje/14d) NIE jest „po przerwie"
                if (a > 2) violations += "POWROT_DLA_AKTYWNEGO: $ctx"
                if (r.breakDays < 0) violations += "UJEMNA_PRZERWA: $ctx"
            }
        }

        // === 3. detectMissedWorkouts ===
        for (planned in listOf(0, 1, 3, 5)) for (missed in listOf(0, 1, 2, 3, 5)) for (d in days) {
            count++
            val r = try { detectMissedWorkouts(planned, missed, d) }
                catch (e: Throwable) { violations += "MISSED WYJĄTEK p=$planned m=$missed d=$d: ${e.message}"; continue }
            val ctx = "missed planned=$planned missed=$missed days=$d → ${r?.severity}"
            if (planned <= 0 || missed <= 0) {
                if (r != null) violations += "MISSED_BEZ_PLANU: $ctx"
            } else if (r != null) {
                if (r.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
                if (r.missedCount != missed || r.plannedCount != planned) violations += "MISSED_NIESPOJNY: $ctx"
            }
        }

        // === 4. detectActiveInjury ===
        val painSets = listOf(
            emptyList(),
            listOf(WorkoutPainSnapshot(2, null, 4)),                       // brak bólu
            listOf(WorkoutPainSnapshot(2, "", 4)),                          // pusty ból
            listOf(WorkoutPainSnapshot(1, "kolano", 3)),                    // 1× ból
            listOf(WorkoutPainSnapshot(1, "bark", 2), WorkoutPainSnapshot(5, "bark", 2)), // 2× + niskie wellbeing
            listOf(WorkoutPainSnapshot(3, "plecy", null))                  // ból bez wellbeing
        )
        for (set in painSets) {
            count++
            val r = try { detectActiveInjury(set) }
                catch (e: Throwable) { violations += "INJURY WYJĄTEK ${set.size}: ${e.message}"; continue }
            val hasPain = set.any { !it.painArea.isNullOrBlank() }
            val ctx = "injury set=${set.map { it.painArea }} → ${r?.severity}"
            if (!hasPain && r != null) violations += "KONTUZJA_BEZ_BOLU: $ctx"
            if (hasPain && r == null) violations += "BOL_BEZ_REAKCJI: $ctx"
            if (r != null && r.reason.isBlank()) violations += "PUSTY_REASON: $ctx"
        }

        println("=== MATRYCA TRENINGU: $count kombinacji, naruszeń: ${violations.size} ===")
        violations.take(40).forEach { println("  $it") }
        assertTrue("Naruszenia (${violations.size}):\n" + violations.take(25).joinToString("\n"),
            violations.isEmpty())
    }
}
