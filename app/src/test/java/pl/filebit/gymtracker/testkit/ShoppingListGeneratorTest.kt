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
 * Test ShoppingListGenerator — v1.29.5: lista zakupów = AKTUALNY plan × N dni.
 * Bierze najnowszy zaplanowany dzień i mnoży jego składniki; stare plany
 * z minionych dni NIE mieszają się do wyniku.
 */
class ShoppingListGeneratorTest : TestHarness() {

    private fun generator() = ShoppingListGenerator(
        mealDao = db.mealEntryDao(),
        productDao = db.foodProductDao(),
        shoppingDao = db.shoppingListDao()
    )

    private val dayMs = 24 * 3600 * 1000L

    @Test
    fun `lista to aktualny plan dnia razy liczba dni`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = now - (now % dayMs)
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        // Aktualny plan: 200 g kurczaka dziś
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today + 8 * 3600 * 1000L, mealType = MealType.LUNCH,
            productId = chickenId, grams = 200.0))

        val listId = generator().generate(3, "Zakupy na 3 dni")
        val items = db.shoppingListDao().getItems(listId)

        assertEquals("jedna pozycja", 1, items.size)
        // 200 g/dzień × 3 dni = 600, × 1.10 margines = 660, PROTEIN → 100 g = 700
        assertEquals("200 g × 3 dni + margines + zaokrąglenie = 700 g",
            700.0, items.first().grams, 0.01)
    }

    @Test
    fun `lista bierze tylko najnowszy plan — stare dni pomijane`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = now - (now % dayMs)
        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Kurczak", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6))
        val riceId = db.foodProductDao().upsert(FoodProduct(
            name = "Ryż", category = FoodCategory.CARBS,
            kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3))
        // Aktualny plan (dziś): kurczak
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today + 8 * 3600 * 1000L, mealType = MealType.LUNCH,
            productId = chickenId, grams = 200.0))
        // Stary plan sprzed 10 dni: ryż — NIE powinien trafić na listę
        db.mealEntryDao().upsert(MealEntry(
            dateMs = today - 10 * dayMs, mealType = MealType.DINNER,
            productId = riceId, grams = 500.0))

        val listId = generator().generate(1, "Zakupy na 1 dzień")
        val names = db.shoppingListDao().getItems(listId).map { it.productName }.toSet()

        assertTrue("Kurczak z aktualnego planu jest na liście", "Kurczak" in names)
        assertEquals("Ryż ze starego planu sprzed 10 dni NIE jest na liście",
            false, "Ryż" in names)
    }

    @Test
    fun `produkt z kilku posilkow jednego dnia jest sumowany`() = runBlocking {
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
            productId = chickenId, grams = 100.0))

        val listId = generator().generate(1, "Zakupy na 1 dzień")
        val items = db.shoppingListDao().getItems(listId)

        assertEquals("jedna pozycja (200+100 zsumowane)", 1, items.size)
        // 300 g × 1 dzień × 1.10 = 330 → PROTEIN zaokrągla do 100 g = 400
        assertEquals(400.0, items.first().grams, 0.01)
    }

    @Test
    fun `pusty plan daje pusta liste`() = runBlocking {
        val listId = generator().generate(3, "Zakupy na 3 dni")
        assertEquals(0, db.shoppingListDao().getItems(listId).size)
    }
}
