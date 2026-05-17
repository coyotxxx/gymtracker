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

    // Testy ShoppingListGenerator przeniesione do dedykowanego ShoppingListGeneratorTest
    // (v1.29.5 — nowy model „aktualny plan × N dni").

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
