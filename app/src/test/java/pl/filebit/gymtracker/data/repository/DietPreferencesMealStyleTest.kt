package pl.filebit.gymtracker.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.filebit.gymtracker.ai.MealStyle
import pl.filebit.gymtracker.ai.MealStylePreferences
import pl.filebit.gymtracker.ai.PlanStyle
import pl.filebit.gymtracker.data.entity.MealType

/**
 * v1.27.1 — persystencja preferencji generowania planu.
 *
 * User narzekał: "jak wybierzemy coś to powinno zostać, a nie po każdym
 * wejściu ustawiać od nowa". Okno generowania planu trzymało styl/
 * preferencje per posiłek/uwagi tylko w `remember` (ginęło przy zamknięciu).
 * Test sprawdza że DietPreferences zapisuje i odtwarza wybór.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class DietPreferencesMealStyleTest {

    private fun prefs() =
        DietPreferences(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `zapisany wybor stylu wraca po ponownym odczycie`() {
        val original = MealStylePreferences(
            globalStyle = PlanStyle.MEAL_PREP,
            slotStyles = mapOf(
                MealType.BREAKFAST to MealStyle.SMOOTHIE,
                MealType.DINNER to MealStyle.FISH
            ),
            freeText = "bez nabiału na śniadanie, więcej warzyw"
        )
        prefs().saveMealStylePreferences(original)

        // świeża instancja — czyta z SharedPreferences (jak po restarcie okna)
        val loaded = prefs().loadMealStylePreferences()
        assertEquals("styl globalny zachowany", original.globalStyle, loaded.globalStyle)
        assertEquals("preferencje per posiłek zachowane",
            original.slotStyles, loaded.slotStyles)
        assertEquals("uwagi do AI zachowane", original.freeText, loaded.freeText)
    }

    @Test
    fun `bez zapisanego wyboru zwraca domyslne (CLASSIC)`() {
        val loaded = prefs().loadMealStylePreferences()
        assertEquals(PlanStyle.CLASSIC, loaded.globalStyle)
        assertTrue("brak preferencji per posiłek", loaded.slotStyles.isEmpty())
        assertEquals("", loaded.freeText)
    }

    @Test
    fun `nadpisanie wyboru — nowy zastepuje stary`() {
        val p = prefs()
        p.saveMealStylePreferences(MealStylePreferences(globalStyle = PlanStyle.QUICK))
        p.saveMealStylePreferences(MealStylePreferences(globalStyle = PlanStyle.FIT_BOWL))
        assertEquals("ostatni zapis wygrywa",
            PlanStyle.FIT_BOWL, prefs().loadMealStylePreferences().globalStyle)
    }
}
