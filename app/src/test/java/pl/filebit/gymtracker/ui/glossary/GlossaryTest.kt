package pl.filebit.gymtracker.ui.glossary

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.19.0 — Glossary musi zawierać terminy periodyzacji + opisy są niepuste.
 */
class GlossaryTest {

    @Test
    fun `MESOCYCLE term istnieje`() {
        val entry = Glossary.get("MESOCYCLE")
        assertNotNull(entry)
        assertTrue(entry!!.first.contains("Mesocykl"))
        assertTrue(entry.second.length > 50)
    }

    @Test
    fun `INTENSIFICATION term istnieje z opisem`() {
        val e = Glossary.get("INTENSIFICATION")
        assertNotNull(e)
        assertTrue(e!!.second.contains("siły") || e.second.contains("siła"))
    }

    @Test
    fun `ACWR term zawiera formula`() {
        val e = Glossary.get("ACWR")
        assertNotNull(e)
        assertTrue("ACWR opis zawiera definicję formuły", e!!.second.contains("7d") && e.second.contains("28d"))
    }

    @Test
    fun `wszystkie 7 nowych terminów periodyzacji obecne`() {
        val keys = listOf("MESOCYCLE", "MICROCYCLE", "ACCUMULATION", "INTENSIFICATION", "PEAKING", "ACWR", "WILKS")
        keys.forEach { key ->
            assertNotNull("Brak: $key", Glossary.get(key))
        }
    }

    @Test
    fun `istniejące terminy nadal obecne (regression)`() {
        listOf("1RM", "RPE", "DELOAD", "VOLUME", "STREAK", "EPLEY").forEach { key ->
            assertNotNull("Regresja: $key zniknął", Glossary.get(key))
        }
    }
}
