package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.coach.CoachAction
import pl.filebit.gymtracker.data.coach.CoachActionType
import pl.filebit.gymtracker.data.coach.CoachDomain
import pl.filebit.gymtracker.data.coach.CoachPriority
import pl.filebit.gymtracker.data.coach.CoachReaction
import pl.filebit.gymtracker.data.coach.applyTrainingPauseRule

/**
 * v2.59.0 (U9+U10) — reguła „trener pyta o przyczynę, pamięta, nie nagabuje".
 * Testuje czystą funkcję `applyTrainingPauseRule` (bez DB/AI) — fundament jednego głosu.
 */
class TrainingPauseRuleTest {

    private val missedWorkout = CoachReaction(
        id = "missed_workout", domain = CoachDomain.CONSISTENCY, priority = CoachPriority.CONSISTENCY,
        title = "Opuszczony trening", message = "Wróćmy do planu.",
        actions = listOf(CoachAction(CoachActionType.START_WORKOUT, "Zacznij trening")),
        source = "test"
    )
    private val dietReaction = CoachReaction(
        id = "diet_no_training", domain = CoachDomain.DIET, priority = CoachPriority.CONSISTENCY,
        title = "Dieta bez treningu", message = "Chroń mięśnie.", source = "test"
    )

    @Test
    fun `brak nagabywania - bez zmian`() {
        val input = listOf(dietReaction)
        assertEquals(input, applyTrainingPauseRule(input, pauseActive = false, hadPause = false))
    }

    @Test
    fun `nagabywanie + brak przyczyny - zamienia na pytanie`() {
        val out = applyTrainingPauseRule(listOf(missedWorkout, dietReaction), pauseActive = false, hadPause = false)
        assertFalse("nagabywanie usunięte", out.any { it.id == "missed_workout" })
        assertTrue("pojawia się pytanie", out.any { it.id == "training_pause_ask" })
        assertTrue("dieta zostaje", out.any { it.id == "diet_no_training" })
        val q = out.first { it.id == "training_pause_ask" }
        assertEquals("Nie trenowałeś — co się stało?", q.title)
        // pytanie ma szybkie odpowiedzi z powodem w payload
        assertTrue(q.actions.any { it.type == CoachActionType.RECORD_PAUSE_REASON && it.payload == "NO_TIME" })
    }

    @Test
    fun `znana przyczyna - wycisza nagabywanie, BEZ pytania`() {
        val out = applyTrainingPauseRule(listOf(missedWorkout, dietReaction), pauseActive = true, hadPause = true)
        assertFalse("nagabywanie wyciszone", out.any { it.id == "missed_workout" })
        assertFalse("nie pyta drugi raz", out.any { it.id == "training_pause_ask" })
        assertTrue("dieta zostaje (ochrona mięśni)", out.any { it.id == "diet_no_training" })
    }

    @Test
    fun `po wygasłej przerwie - pyta wariantem wracasz`() {
        val out = applyTrainingPauseRule(listOf(missedWorkout), pauseActive = false, hadPause = true)
        val q = out.first { it.id == "training_pause_ask" }
        assertEquals("Wracasz do treningów?", q.title)
    }
}
