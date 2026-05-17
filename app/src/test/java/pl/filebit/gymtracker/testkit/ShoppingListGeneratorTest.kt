package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.ShoppingListGenerator

/**
 * Test ShoppingListGenerator — lista zakupów MUSI zawierać wyłącznie produkty
 * z posiłków w zadanym zakresie dat. Wpisy spoza okna (np. stary plan sprzed
 * tygodnia) NIE mogą trafić na świeżą listę — to było źródło zgłoszenia usera
 * „widzę produkty których nie mam w planie".
 */
class ShoppingListGeneratorTest : TestHarness() {

    private fun generator() = ShoppingListGenerator(
        mealDao = db.mealEntryDao(),
        productDao = db.foodProductDao(),
        shoppingDao = db.shoppingListDao()
    )

    private val dayMs = 24 * 3600 * 1000L

    @Test
    fun `lista zawiera tylko posilki z zakresu — wpisy spoza okna pomijane`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = now - (now % dayMs)
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        val riceId = db.foodProductDao().upsert(FoodProduct(
            name = "Ryż", category = FoodCategory.CARBS,
            kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3))

        // W zakresie [dziś, dziś+1): posiłek dzisiejszy
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today + 8 * 3600 * 1000L, mealType = MealType.LUNCH,
            productId = chickenId, grams = 200.0))
        // POZA zakresem: 10 dni temu — stary plan, NIE powinien się pojawić
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today - 10 * dayMs, mealType = MealType.DINNER,
            productId = riceId, grams = 500.0))

        val listId = generator().generate(today, today + dayMs, "Test 1 dzień")
        val items = db.shoppingListDao().getItems(listId)
        val names = items.map { it.productName }.toSet()

        assertTrue("Kurczak (dzisiejszy posiłek) jest na liście", "Kurczak" in names)
        assertEquals("Ryż ze starego planu sprzed 10 dni NIE jest na liście",
            false, "Ryż" in names)
        assertEquals("lista ma dokładnie 1 pozycję — brak przecieku spoza zakresu",
            1, items.size)
    }

    @Test
    fun `ten sam produkt z kilku posilkow jest sumowany`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = now - (now % dayMs)
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today + 8 * 3600 * 1000L, mealType = MealType.LUNCH,
            productId = chickenId, grams = 200.0))
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today + 18 * 3600 * 1000L, mealType = MealType.DINNER,
            productId = chickenId, grams = 150.0))

        val listId = generator().generate(today, today + dayMs, "Test suma")
        val items = db.shoppingListDao().getItems(listId)

        assertEquals("jedna pozycja (oba posiłki zsumowane)", 1, items.size)
        // 350 g × 1.10 margines = 385 → PROTEIN zaokrągla w górę do 100 g = 400 g
        assertEquals("200+150 g + 10% + zaokrąglenie do 100 g = 400 g",
            400.0, items.first().grams, 0.01)
    }

    @Test
    fun `pusty zakres daje pusta liste`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = now - (now % dayMs)
        val listId = generator().generate(today, today + dayMs, "Test pusty")
        assertEquals("brak posiłków → brak pozycji", 0, db.shoppingListDao().getItems(listId).size)
    }
}
