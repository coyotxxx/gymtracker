package pl.filebit.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test pure function `IntentNavigationParser.parseInitialNavigation(...)` —
 * naprawia lukę K4 z audytu 2026-05-10 (push z workerów otwierały Home
 * zamiast docelowego ekranu).
 *
 * Wzorzec testowy: pure function bez Android (Bundle/Intent) — testowalne
 * w JVM bez Robolectric/Mockito.
 */
class IntentNavigationParserTest {

    @Test
    fun `nic z extras - zwraca null (pozostaje na Home)`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = false,
            quickAction = null,
            openDiet = false
        )
        assertNull(route)
    }

    @Test
    fun `EXTRA_OPEN_AI_TRAINER bez quickAction - otworzyc AI Trener nowa konwersacja`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = null,
            openDiet = false
        )
        assertEquals("ai/trainer/0", route)
    }

    @Test
    fun `EXTRA_OPEN_AI_TRAINER + quickAction blank - otworzyc bez auto action`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = "",
            openDiet = false
        )
        assertEquals("ai/trainer/0", route)
    }

    @Test
    fun `EXTRA_OPEN_AI_TRAINER + quickAction DELOAD - otworzyc z auto=DELOAD`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = "DELOAD",
            openDiet = false
        )
        assertEquals("ai/trainer/0?auto=DELOAD", route)
    }

    @Test
    fun `EXTRA_OPEN_AI_TRAINER + quickAction PAIN_RECOVERY - otworzyc z auto=PAIN_RECOVERY`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = "PAIN_RECOVERY",
            openDiet = false
        )
        assertEquals("ai/trainer/0?auto=PAIN_RECOVERY", route)
    }

    @Test
    fun `EXTRA_OPEN_AI_TRAINER + quickAction TODAY - otworzyc z auto=TODAY`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = "TODAY",
            openDiet = false
        )
        assertEquals("ai/trainer/0?auto=TODAY", route)
    }

    @Test
    fun `EXTRA_OPEN_DIET - otworzyc Diete`() {
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = false,
            quickAction = null,
            openDiet = true
        )
        assertEquals("diet", route)
    }

    @Test
    fun `priority AI_TRAINER over DIET - obie flagi true - AI wygrywa`() {
        // Edge case: jeśli oba ustawione (niepowinno się zdarzyć w praktyce),
        // AI Trener ma priorytet bo ProactiveAiCheckWorker odpala częściej.
        val route = IntentNavigationParser.parseInitialNavigation(
            openAiTrainer = true,
            quickAction = "DELOAD",
            openDiet = true
        )
        assertEquals("ai/trainer/0?auto=DELOAD", route)
    }
}
