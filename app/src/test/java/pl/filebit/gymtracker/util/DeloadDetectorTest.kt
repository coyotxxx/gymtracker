package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeloadDetectorTest {

    @Test
    fun `light week with low RPE returns null`() {
        val r = detectDeloadNeed(avgRpe14d = 7.0, sessionsLast14d = 6, sessionsLast35d = 8)
        assertNull(r)
    }

    @Test
    fun `null RPE returns null`() {
        val r = detectDeloadNeed(avgRpe14d = null, sessionsLast14d = 5, sessionsLast35d = 10)
        assertNull(r)
    }

    @Test
    fun `high RPE plus many sessions returns HIGH severity`() {
        val r = detectDeloadNeed(avgRpe14d = 8.7, sessionsLast14d = 6, sessionsLast35d = 16)
        assertNotNull(r)
        assertEquals(DeloadSeverity.HIGH, r!!.severity)
    }

    @Test
    fun `very high RPE returns MED regardless of session count`() {
        val r = detectDeloadNeed(avgRpe14d = 9.2, sessionsLast14d = 5, sessionsLast35d = 10)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `multiple stagnations returns MED`() {
        val r = detectDeloadNeed(avgRpe14d = 7.5, sessionsLast14d = 5, sessionsLast35d = 10, stagnationCount = 4)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `long cycle moderate RPE returns LOW`() {
        val r = detectDeloadNeed(avgRpe14d = 7.8, sessionsLast14d = 6, sessionsLast35d = 19)
        assertNotNull(r)
        assertEquals(DeloadSeverity.LOW, r!!.severity)
    }

    @Test
    fun `low session count even with high RPE returns null (no reliable RPE)`() {
        val r = detectDeloadNeed(avgRpe14d = 9.5, sessionsLast14d = 1, sessionsLast35d = 5)
        assertNull(r)
    }

    @Test
    fun `2 stagnations not enough alone`() {
        val r = detectDeloadNeed(avgRpe14d = 7.5, sessionsLast14d = 5, sessionsLast35d = 10, stagnationCount = 2)
        assertNull(r)
    }

    /**
     * B1 — świeży user (3 treningi) NIE dostaje deloadu za "stagnację".
     * 3 ćwiczenia × 3 treningi z tą samą wagą to normalne wdrażanie,
     * nie stagnacja po progresji. Guard: sessionsLast35d >= 9.
     */
    @Test
    fun `B1 - fresh user 3 sessions with stagnations does NOT trigger deload`() {
        val r = detectDeloadNeed(
            avgRpe14d = 6.5, sessionsLast14d = 3, sessionsLast35d = 3, stagnationCount = 3)
        assertNull("świeży user (3 sesje) nie dostaje deloadu za stagnację", r)
    }

    @Test
    fun `B1 - stagnations DO trigger deload gdy user ma realna historie`() {
        // 9 sesji w 35 dni = ~3 tygodnie regularnego treningu — stagnacja
        // teraz znaczy realne utknięcie po progresji.
        val r = detectDeloadNeed(
            avgRpe14d = 7.0, sessionsLast14d = 4, sessionsLast35d = 9, stagnationCount = 3)
        assertNotNull("user z historią 9 sesji → stagnacja uzasadnia deload", r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    @Test
    fun `boundary RPE 8_5 with 15 sessions triggers HIGH`() {
        val r = detectDeloadNeed(avgRpe14d = 8.5, sessionsLast14d = 6, sessionsLast35d = 15)
        assertNotNull(r)
        assertEquals(DeloadSeverity.HIGH, r!!.severity)
    }

    @Test
    fun `reason mentions concrete numbers`() {
        val r = detectDeloadNeed(avgRpe14d = 8.7, sessionsLast14d = 6, sessionsLast35d = 16)!!
        assert(r.reason.contains("8.7")) { "Reason should cite RPE: ${r.reason}" }
        assert(r.reason.contains("16")) { "Reason should cite sessions: ${r.reason}" }
    }

    // --- BUG REGRESSION: jeden workout w 14d z RPE 10 (powrót po przerwie) ---

    @Test
    fun `single workout in 14d with high RPE does NOT trigger deload`() {
        // Maciejów scenariusz: 12 workoutów tyg -6 do -3, przerwa 14 dni, 1 workout tyg 0 z RPE 10
        // sessionsLast14d=1 (tylko ten powrotny), sessionsLast35d=13 (12 sprzed przerwy + 1 powrotny)
        val r = detectDeloadNeed(avgRpe14d = 10.0, sessionsLast14d = 1, sessionsLast35d = 13)
        assertNull("RPE 10 z 1 setu po przerwie NIE jest przetrenowaniem", r)
    }

    @Test
    fun `2 workouts in 14d with very high RPE still does NOT trigger`() {
        val r = detectDeloadNeed(avgRpe14d = 9.5, sessionsLast14d = 2, sessionsLast35d = 14)
        assertNull(r)
    }

    @Test
    fun `3 workouts in 14d with very high RPE DOES trigger MED`() {
        // Minimum N=3 dla zaufania średniej. 3 sesje z RPE 9.5 → faktyczne przetrenowanie
        val r = detectDeloadNeed(avgRpe14d = 9.5, sessionsLast14d = 3, sessionsLast35d = 8)
        assertNotNull(r)
        assertEquals(DeloadSeverity.MED, r!!.severity)
    }

    // --- Return-after-break detector ---

    @Test
    fun `return after long break detected`() {
        // 12 workoutów w 35d ale 0 w 14d, ostatni był 18 dni temu
        val r = detectReturnAfterBreak(
            sessionsLast14d = 0,
            sessionsLast35d = 12,
            daysSinceLastWorkout = 18
        )
        assertNotNull(r)
        assertEquals(ReturnSeverity.LONG_BREAK, r!!.severity)
    }

    @Test
    fun `return after short break detected (1 workout post-break)`() {
        // Po 12 dni przerwy wrócił 3 dni temu (1 workout w 14d, sessionsLast14d=1)
        val r = detectReturnAfterBreak(
            sessionsLast14d = 1,
            sessionsLast35d = 13,
            daysSinceLastWorkout = 3
        )
        assertNotNull(r)
        assertEquals(ReturnSeverity.SHORT_BREAK, r!!.severity)
    }

    @Test
    fun `regular training NOT classified as return`() {
        // Regularny user: 12 workoutów w 35d, 5 w 14d
        val r = detectReturnAfterBreak(
            sessionsLast14d = 5,
            sessionsLast35d = 12,
            daysSinceLastWorkout = 1
        )
        assertNull(r)
    }

    @Test
    fun `no prior training history NOT classified as return`() {
        // Nowy user: 1 workout total, brak historii
        val r = detectReturnAfterBreak(
            sessionsLast14d = 1,
            sessionsLast35d = 1,
            daysSinceLastWorkout = 3
        )
        assertNull(r)
    }

    // === v2.12.0: detectMissedWorkouts ===

    @Test
    fun `brak opuszczonych zwraca null`() {
        assertNull(detectMissedWorkouts(plannedDays = 3, missedDays = 0))
    }

    @Test
    fun `brak planu (0 zaplanowanych) zwraca null`() {
        assertNull(detectMissedWorkouts(plannedDays = 0, missedDays = 0))
    }

    @Test
    fun `jeden opuszczony to SOFT`() {
        val r = detectMissedWorkouts(plannedDays = 3, missedDays = 1, daysSinceLastWorkout = 2)
        assertNotNull(r)
        assertEquals(MissedWorkoutSeverity.SOFT, r!!.severity)
        assertEquals(1, r.missedCount)
        assertEquals(3, r.plannedCount)
    }

    @Test
    fun `dwa lub wiecej opuszczonych to FIRM`() {
        val r = detectMissedWorkouts(plannedDays = 3, missedDays = 2)
        assertNotNull(r)
        assertEquals(MissedWorkoutSeverity.FIRM, r!!.severity)
    }

    @Test
    fun `wszystkie opuszczone to FIRM z pelnym licznikiem`() {
        val r = detectMissedWorkouts(plannedDays = 3, missedDays = 3, daysSinceLastWorkout = 9)
        assertNotNull(r)
        assertEquals(MissedWorkoutSeverity.FIRM, r!!.severity)
        assertEquals(3, r.missedCount)
        assertEquals(3, r.plannedCount)
        assertEquals(9, r.daysSinceLast)
    }
}
