package pl.filebit.gymtracker.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.filebit.gymtracker.ai.AiAlternative
import pl.filebit.gymtracker.ai.AiMealRecipe
import pl.filebit.gymtracker.ai.AiRecipeIngredient
import pl.filebit.gymtracker.ai.CookingDevice
import pl.filebit.gymtracker.ai.DayPlanRecipes
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
            character = PlanStyle.FIT_BOWL,
            modifiers = setOf(PlanStyle.MEAL_PREP, PlanStyle.QUICK),
            slotStyles = mapOf(
                MealType.BREAKFAST to MealStyle.SMOOTHIE,
                MealType.DINNER to MealStyle.FISH
            ),
            devices = setOf(CookingDevice.COSORI_TURBO_TOWER, CookingDevice.THERMOMIX_TM5),
            freeText = "bez nabiału na śniadanie, więcej warzyw"
        )
        prefs().saveMealStylePreferences(original)

        // świeża instancja — czyta z SharedPreferences (jak po restarcie okna)
        val loaded = prefs().loadMealStylePreferences()
        assertEquals("charakter dań zachowany", original.character, loaded.character)
        assertEquals("modyfikatory zachowane", original.modifiers, loaded.modifiers)
        assertEquals("urządzenia zachowane", original.devices, loaded.devices)
        assertEquals("preferencje per posiłek zachowane",
            original.slotStyles, loaded.slotStyles)
        assertEquals("uwagi do AI zachowane", original.freeText, loaded.freeText)
    }

    @Test
    fun `bez zapisanego wyboru zwraca domyslne (CLASSIC)`() {
        val loaded = prefs().loadMealStylePreferences()
        assertEquals(PlanStyle.CLASSIC, loaded.character)
        assertTrue("brak modyfikatorów", loaded.modifiers.isEmpty())
        assertTrue("brak preferencji per posiłek", loaded.slotStyles.isEmpty())
        assertEquals("domyślnie patelnia/piekarnik",
            setOf(CookingDevice.PAN_OVEN), loaded.devices)
        assertEquals("", loaded.freeText)
    }

    @Test
    fun `nadpisanie wyboru — nowy zastepuje stary`() {
        val p = prefs()
        p.saveMealStylePreferences(MealStylePreferences(character = PlanStyle.CLASSIC))
        p.saveMealStylePreferences(MealStylePreferences(character = PlanStyle.FIT_BOWL))
        assertEquals("ostatni zapis wygrywa",
            PlanStyle.FIT_BOWL, prefs().loadMealStylePreferences().character)
    }

    // === v1.27.5: opcja "Generuj z ulubionych" + snapshot przepisów planu ===

    @Test
    fun `preferFavorites zapisuje sie i wraca po odczycie`() {
        prefs().saveMealStylePreferences(MealStylePreferences(preferFavorites = true))
        assertTrue("wybór 'generuj z ulubionych' zachowany",
            prefs().loadMealStylePreferences().preferFavorites)
    }

    @Test
    fun `domyslnie preferFavorites jest wylaczone`() {
        assertFalse("opcja ulubionych domyślnie OFF (opt-in)",
            prefs().loadMealStylePreferences().preferFavorites)
    }

    @Test
    fun `snapshot przepisow planu przezywa zapis i odczyt`() {
        val recipe = AiMealRecipe(
            name = "Owsianka z twarogiem",
            ingredients = listOf(AiRecipeIngredient("Płatki owsiane", 60)),
            instructions = "1. Zalej gorącym mlekiem.",
            kcal = 480, proteinG = 35, carbsG = 60, fatG = 8,
            alternatives = listOf(
                AiAlternative(
                    name = "Jajecznica na maśle",
                    ingredients = listOf(AiRecipeIngredient("Jajka", 150)),
                    kcal = 470, proteinG = 32, carbsG = 45, fatG = 18
                )
            )
        )
        val snap = DayPlanRecipes(
            recipesByType = mapOf("BREAKFAST" to recipe),
            alternativesByType = mapOf("BREAKFAST" to recipe.alternatives)
        )
        prefs().saveLastPlanRecipes(snap)

        // świeża instancja — jak po restarcie aplikacji
        val loaded = prefs().loadLastPlanRecipes()
        assertEquals("przepis slotu zachowany",
            "Owsianka z twarogiem", loaded.recipesByType["BREAKFAST"]?.name)
        assertEquals("instrukcje zachowane",
            "1. Zalej gorącym mlekiem.", loaded.recipesByType["BREAKFAST"]?.instructions)
        assertEquals("alternatywa zachowana",
            1, loaded.alternativesByType["BREAKFAST"]?.size)
        assertEquals("nazwa alternatywy zachowana",
            "Jajecznica na maśle", loaded.alternativesByType["BREAKFAST"]?.first()?.name)
    }

    @Test
    fun `bez zapisanego snapshotu zwraca pusty DayPlanRecipes`() {
        val loaded = prefs().loadLastPlanRecipes()
        assertTrue("brak przepisów", loaded.recipesByType.isEmpty())
        assertTrue("brak alternatyw", loaded.alternativesByType.isEmpty())
    }
}
