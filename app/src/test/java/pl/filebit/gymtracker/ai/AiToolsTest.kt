package pl.filebit.gymtracker.ai

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.22.0 — spójność rejestracji narzędzi AI.
 * Realna klasa błędu: narzędzie w TOOL_NAMES ale brak w toolsForApi (lub odwrotnie)
 * → AI dostaje schemat bez narzędzia lub handler odrzuca wywołanie.
 */
class AiToolsTest {

    @Test
    fun `TOOL_NAMES pokrywa sie ze schematem toolsForApi`() {
        val apiNames = AiTools.toolsForApi()
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            .toSet()
        assertEquals("nazwy w schemacie API == TOOL_NAMES", AiTools.TOOL_NAMES, apiNames)
    }

    @Test
    fun `narzedzia zapisu sa zarejestrowane`() {
        val writeTools = setOf("log_weight", "add_meal", "set_calorie_target", "set_diet_goal")
        assertTrue("wszystkie narzędzia zapisu w TOOL_NAMES",
            writeTools.all { it in AiTools.TOOL_NAMES })
    }
}
