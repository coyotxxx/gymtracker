package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealType

class DailyQualityScorerTest {

    private val scorer = DailyQualityScorer()
    private val target = DailyQualityTarget.forUser(Gender.MALE, ageYears = 30)

    private fun product(
        id: Long,
        name: String,
        cat: FoodCategory,
        kcal: Double = 100.0,
        fiber: Double = 0.0,
        calcium: Double = 0.0,
        potassium: Double = 0.0,
        sodium: Double = 0.0
    ) = FoodProduct(
        id = id,
        name = name,
        category = cat,
        kcalPer100g = kcal,
        proteinPer100g = 5.0,
        carbsPer100g = 10.0,
        fatPer100g = 2.0,
        fiberPer100g = fiber,
        calciumMgPer100g = calcium,
        potassiumMgPer100g = potassium,
        sodiumMgPer100g = sodium
    )

    private fun entry(productId: Long, grams: Double) = MealEntry(
        id = productId * 10,
        dateMs = 0,
        mealType = MealType.LUNCH,
        productId = productId,
        grams = grams
    )

    @Test
    fun `male target 30+ gives 30g fiber min`() {
        assertEquals(30.0, target.fiberG.start, 0.01)
        assertEquals(38.0, target.fiberG.endInclusive, 0.01)
    }

    @Test
    fun `female target 25g fiber min`() {
        val femaleTarget = DailyQualityTarget.forUser(Gender.FEMALE, ageYears = 30)
        assertEquals(25.0, femaleTarget.fiberG.start, 0.01)
    }

    @Test
    fun `empty entries return zero everything`() {
        val summary = scorer.compute(emptyList(), target)
        assertEquals(0.0, summary.fiberG, 0.01)
        assertEquals(0.0, summary.vegetablesAndFruitsG, 0.01)
        assertEquals(0, summary.uniqueProductsCount)
    }

    @Test
    fun `100g of broccoli (3_3g fiber, 293mg potassium) sums correctly`() {
        val broccoli = product(1, "Brokuły", FoodCategory.VEGETABLE, fiber = 3.3, calcium = 40.0, potassium = 293.0, sodium = 41.0)
        val withMacros = listOf(entry(1, 100.0).macrosFor(broccoli))
        val summary = scorer.compute(withMacros, target)
        assertEquals(3.3, summary.fiberG, 0.01)
        assertEquals(40.0, summary.calciumMg, 0.01)
        assertEquals(293.0, summary.potassiumMg, 0.01)
        assertEquals(100.0, summary.vegetablesAndFruitsG, 0.01)
        assertEquals(1, summary.uniqueProductsCount)
    }

    @Test
    fun `200g of broccoli doubles values`() {
        val broccoli = product(1, "Brokuły", FoodCategory.VEGETABLE, fiber = 3.3, potassium = 293.0)
        val withMacros = listOf(entry(1, 200.0).macrosFor(broccoli))
        val summary = scorer.compute(withMacros, target)
        assertEquals(6.6, summary.fiberG, 0.01)
        assertEquals(586.0, summary.potassiumMg, 0.01)
        assertEquals(200.0, summary.vegetablesAndFruitsG, 0.01)
    }

    @Test
    fun `meat is NOT counted as vegetables`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, fiber = 0.0, potassium = 256.0)
        val withMacros = listOf(entry(1, 200.0).macrosFor(chicken))
        val summary = scorer.compute(withMacros, target)
        assertEquals(0.0, summary.vegetablesAndFruitsG, 0.01)
        assertEquals(512.0, summary.potassiumMg, 0.01) // potas liczony zawsze
    }

    @Test
    fun `unique products count distinct product ids`() {
        val p1 = product(1, "A", FoodCategory.VEGETABLE)
        val p2 = product(2, "B", FoodCategory.PROTEIN)
        val withMacros = listOf(
            entry(1, 100.0).macrosFor(p1),
            entry(1, 50.0).macrosFor(p1), // ten sam produkt w 2 posiłkach
            entry(2, 100.0).macrosFor(p2)
        )
        val summary = scorer.compute(withMacros, target)
        assertEquals(2, summary.uniqueProductsCount)
    }

    @Test
    fun `low fiber generates warning`() {
        val rice = product(1, "Ryż", FoodCategory.CARBS, fiber = 0.4)
        val withMacros = listOf(entry(1, 200.0).macrosFor(rice))
        val summary = scorer.compute(withMacros, target)
        assertFalse(summary.fiberOk)
        assertTrue(summary.warnings().any { it.contains("błonnik", ignoreCase = true) })
    }

    @Test
    fun `excessive sodium generates warning`() {
        val saltyBread = product(1, "Sól", FoodCategory.OTHER, sodium = 5000.0)
        val withMacros = listOf(entry(1, 100.0).macrosFor(saltyBread))
        val summary = scorer.compute(withMacros, target)
        assertFalse(summary.sodiumOk)
        assertTrue(summary.warnings().any { it.contains("sodu", ignoreCase = true) })
    }

    @Test
    fun `score is 0-100`() {
        val p1 = product(1, "A", FoodCategory.VEGETABLE)
        val withMacros = listOf(entry(1, 100.0).macrosFor(p1))
        val summary = scorer.compute(withMacros, target)
        assertTrue(summary.overallScore in 0..100)
    }

    @Test
    fun `low variety (less than 5 products) generates warning`() {
        val p1 = product(1, "A", FoodCategory.VEGETABLE)
        val p2 = product(2, "B", FoodCategory.PROTEIN)
        val p3 = product(3, "C", FoodCategory.CARBS)
        val withMacros = listOf(
            entry(1, 100.0).macrosFor(p1),
            entry(2, 100.0).macrosFor(p2),
            entry(3, 100.0).macrosFor(p3)
        )
        val summary = scorer.compute(withMacros, target)
        assertTrue(summary.warnings().any { it.contains("różnorodności", ignoreCase = true) })
    }
}
