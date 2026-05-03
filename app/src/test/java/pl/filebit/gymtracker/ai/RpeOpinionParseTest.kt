package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test parsera regex w RpeOpinionService.parseSuggestion() — pure logic,
 * bez DI. Sprawdza popularne formaty odpowiedzi AI.
 */
class RpeOpinionParseTest {

    @Test
    fun `parses standard format with x`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("⚠ Sugeruję inaczej: 80 kg × 6")
        assertEquals(80.0, kg!!, 0.01)
        assertEquals(6, reps)
    }

    @Test
    fun `parses lowercase x without spaces`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("Sugeruję 75kg×8 ze względu na trend")
        assertEquals(75.0, kg!!, 0.01)
        assertEquals(8, reps)
    }

    @Test
    fun `parses decimal weight with comma`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("Sugeruję inaczej: 82,5 kg x 5")
        assertEquals(82.5, kg!!, 0.01)
        assertEquals(5, reps)
    }

    @Test
    fun `parses decimal weight with dot`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("⚠ Sugeruję 67.5kg × 10")
        assertEquals(67.5, kg!!, 0.01)
        assertEquals(10, reps)
    }

    @Test
    fun `returns nulls when AI agrees`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("✓ Zgadzam się z sugestią algorytmu, idź dalej.")
        assertNull(kg)
        assertNull(reps)
    }

    @Test
    fun `returns nulls when no numbers present`() {
        val (kg, reps) = RpeOpinionService.parseSuggestion("Spróbuj utrzymać wagę i zwiększyć powtórzenia.")
        assertNull(kg)
        assertNull(reps)
    }
}
