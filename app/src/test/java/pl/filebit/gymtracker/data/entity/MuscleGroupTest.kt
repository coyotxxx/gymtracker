package pl.filebit.gymtracker.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.19.0 — MuscleGroup.displayName() centralizuje labele PL (wcześniej duplikowane).
 */
class MuscleGroupTest {

    @Test
    fun `wszystkie grupy mają niepusty label PL`() {
        MuscleGroup.entries.forEach { mg ->
            val label = mg.displayName()
            assertTrue("MuscleGroup.$mg.displayName() musi być niepusty", label.isNotBlank())
        }
    }

    @Test
    fun `kluczowe grupy mapują się prawidłowo`() {
        assertEquals("Klatka", MuscleGroup.CHEST.displayName())
        assertEquals("Plecy", MuscleGroup.BACK.displayName())
        assertEquals("Barki", MuscleGroup.SHOULDERS.displayName())
        assertEquals("Czworogłowe", MuscleGroup.QUADS.displayName())
        assertEquals("Cardio", MuscleGroup.CARDIO.displayName())
    }

    @Test
    fun `wszystkie 12 grup ma unikalne labele`() {
        val labels = MuscleGroup.entries.map { it.displayName() }
        assertEquals("Każdy MuscleGroup powinien mieć unikalny label", labels.size, labels.toSet().size)
    }
}
