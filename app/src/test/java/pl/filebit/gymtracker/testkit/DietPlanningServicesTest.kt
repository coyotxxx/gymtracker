package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.MealPrepPlanner
import pl.filebit.gymtracker.data.repository.ShoppingListGenerator

/**
 * v1.27 — FAZA 2.4b — dietowe serwisy planujące (ShoppingList + MealPrep).
 *
 * Oba agregują MealEntry z zakresu dat po productId. Test buduje 2 dni
 * posiłków (kurczak/ryż/brokuł × 2) i sprawdza:
 *  - ShoppingListGenerator: lista pozycji = produkty, gramy z marginesem.
 *  - MealPrepPlanner: plan z krokami przygotowania.
 */
class DietPlanningServicesTest : TestHarness() {

    private val day = 86_400_000L

    /** Wstawia 3 produkty + 2 dni posiłków. Zwraca (fromMs, toMs). */
    private suspend fun seedTwoDaysOfMeals(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val dayA = now - 2 * day
        val dayB = now - 1 * day

        val chickenId = db.foodProductDao().upsert(FoodProduct(
            name = "Pierś z kurczaka", category = FoodCategory.PROTEIN,
            kcalPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6
        ))
        val riceId = db.foodProductDao().upsert(FoodProduct(
            name = "Ryż biały", category = FoodCategory.CARBS,
            kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3
        ))
        val broccoliId = db.foodProductDao().upsert(FoodProduct(
            name = "Brokuł", category = FoodCategory.VEGETABLE,
            kcalPer100g = 34.0, proteinPer100g = 2.8, carbsPer100g = 7.0, fatPer100g = 0.4
        ))

        for (d in listOf(dayA, dayB)) {
            db.mealEntryDao().upsert(MealEntry(
                dateMs = d, mealType = MealType.LUNCH, productId = chickenId, grams = 200.0))
            db.mealEntryDao().upsert(MealEntry(
                dateMs = d, mealType = MealType.LUNCH, productId = riceId, grams = 150.0))
            db.mealEntryDao().upsert(MealEntry(
                dateMs = d, mealType = MealType.DINNER, productId = broccoliId, grams = 100.0))
        }
        return (dayA - 3600_000L) to (now)
    }

    @Test
    fun `ShoppingListGenerator agreguje posilki w liste zakupow`() = runBlocking {
        val (from, to) = seedTwoDaysOfMeals()
        val gen = ShoppingListGenerator(
            db.mealEntryDao(), db.foodProductDao(), db.shoppingListDao())

        val listId = gen.generate(from, to, "Zakupy testowe", safetyMarginPct = 0.10)
        val items = db.shoppingListDao().getItems(listId)

        val tr = TraceReport("shopping-list")
            .section("DANE")
            .kv("posiłki", "2 dni × 3 produkty")
            .section("WYGENEROWANA LISTA")
        items.sortedBy { it.productName }.forEach {
            tr.kv(it.productName, "${it.grams} g (×${it.occurrences} dni, ${it.category})")
        }
        val chicken = items.first { it.productName.contains("kurcz", ignoreCase = true) }
        tr.section("OCENA")
            .verdict("kurczak: 2×200g + 10% margines",
                if (chicken.grams >= 400.0) "OK" else "ZA MAŁO",
                "${chicken.grams} g (surowo 400 g)")
            .verdict("occurrences = liczba dni", "${chicken.occurrences}", "oczekiwane 2")
            .emit()

        assertEquals("lista ma 3 produkty", 3, items.size)
        assertTrue("każda pozycja ma nazwę", items.all { it.productName.isNotBlank() })
        assertTrue("kurczak: 400 g surowo + margines → ≥ 400 g", chicken.grams >= 400.0)
        assertEquals("kurczak występuje w 2 dniach", 2, chicken.occurrences)
    }

    @Test
    fun `ShoppingListGenerator pusty zakres daje pusta ale istniejaca liste`() = runBlocking {
        val gen = ShoppingListGenerator(
            db.mealEntryDao(), db.foodProductDao(), db.shoppingListDao())
        val future = System.currentTimeMillis() + 10 * day
        val listId = gen.generate(future, future + day, "Pusta")

        assertTrue("lista istnieje mimo braku posiłków",
            db.shoppingListDao().getById(listId) != null)
        assertTrue("lista nie ma pozycji",
            db.shoppingListDao().getItems(listId).isEmpty())
    }

    @Test
    fun `MealPrepPlanner generuje plan z krokami przygotowania`() = runBlocking {
        val (from, to) = seedTwoDaysOfMeals()
        val planner = MealPrepPlanner(
            db.mealEntryDao(), db.foodProductDao(), db.mealPrepPlanDao())

        val planId = planner.generate(from, to, "Meal prep testowy")
        val plan = db.mealPrepPlanDao().getById(planId)!!
        val steps = db.mealPrepPlanDao().getSteps(planId)

        val tr = TraceReport("meal-prep")
            .section("PLAN")
            .kv("pojemniki", plan.containersCount.toString())
            .kv("czas łączny", "${plan.totalMinutes} min")
            .section("KROKI")
        steps.sortedBy { it.orderIdx }.forEach {
            tr.kv("#${it.orderIdx} ${it.action}", "${it.description} (${it.estimatedMinutes} min)")
        }
        tr.section("OCENA")
            .verdict("plan ma kroki", if (steps.isNotEmpty()) "OK" else "PUSTY",
                "${steps.size} kroków")
            .emit()

        assertTrue("plan ma co najmniej 1 krok przygotowania", steps.isNotEmpty())
        assertTrue("każdy krok ma opis", steps.all { it.description.isNotBlank() })
        assertTrue("łączny czas planu dodatni", plan.totalMinutes > 0)
    }
}
