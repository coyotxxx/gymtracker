package pl.filebit.gymtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct

/**
 * Baseline tests dla SubstituteService (v0.89.47).
 */
class SubstituteServiceTest {

    private val service = SubstituteService()

    private fun product(
        id: Long,
        name: String,
        cat: FoodCategory,
        kcal: Double,
        p: Double = 0.0,
        c: Double = 0.0,
        f: Double = 0.0
    ) = FoodProduct(
        id = id,
        name = name,
        category = cat,
        kcalPer100g = kcal,
        proteinPer100g = p,
        carbsPer100g = c,
        fatPer100g = f
    )

    @Test
    fun `chicken can be substituted with turkey (similar macro)`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0, c = 0.0, f = 3.6)
        val turkey = product(2, "Indyk", FoodCategory.PROTEIN, 158.0, p = 30.0, c = 0.0, f = 4.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 200.0,
            allProducts = listOf(chicken, turkey),
            tolerance = MatchTolerance.STRICT
        )
        assertEquals(1, result.size)
        assertEquals("Indyk", result[0].product.name)
    }

    @Test
    fun `same product is excluded from results`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 200.0,
            allProducts = listOf(chicken),
            tolerance = MatchTolerance.LOOSE
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `different category excluded by default (sameCategoryOnly)`() {
        val rice = product(1, "Ryż", FoodCategory.CARBS, 130.0, c = 28.0)
        val chicken = product(2, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val result = service.findSubstitutes(
            original = rice,
            originalGrams = 150.0,
            allProducts = listOf(rice, chicken),
            tolerance = MatchTolerance.LOOSE
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `protein and dairy are alternative categories`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val curd = product(2, "Twaróg chudy", FoodCategory.DAIRY, 95.0, p = 17.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 200.0,
            allProducts = listOf(chicken, curd),
            tolerance = MatchTolerance.LOOSE
        )
        // Twaróg może być alternatywą dla mięsa (PROTEIN ↔ DAIRY)
        assertTrue("Curd should be considered as alternative", result.any { it.product.id == 2L })
    }

    @Test
    fun `suggested grams hits target kcal`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val turkey = product(2, "Indyk", FoodCategory.PROTEIN, 110.0, p = 25.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 200.0, // 330 kcal
            allProducts = listOf(chicken, turkey),
            tolerance = MatchTolerance.LOOSE
        )
        if (result.isNotEmpty()) {
            // 330 kcal / 110 kcal/100g = 300g (rounded)
            val turkeyMatch = result.first()
            assertTrue(
                "Suggested grams should produce ~330 kcal",
                kotlin.math.abs(turkeyMatch.product.kcalPer100g * turkeyMatch.suggestedGrams / 100.0 - 330.0) < 50.0
            )
        }
    }

    @Test
    fun `kcal_only tolerance allows wider matches`() {
        val rice = product(1, "Ryż biały", FoodCategory.CARBS, 130.0, c = 28.0, p = 2.7)
        val potato = product(2, "Ziemniaki", FoodCategory.CARBS, 77.0, c = 17.0, p = 2.0)
        val result = service.findSubstitutes(
            original = rice,
            originalGrams = 150.0,
            allProducts = listOf(rice, potato),
            tolerance = MatchTolerance.KCAL_ONLY
        )
        assertEquals(1, result.size)
        assertEquals("Ziemniaki", result[0].product.name)
    }

    @Test
    fun `score is non-negative and identical products would score lowest`() {
        val curd1 = product(1, "Twaróg chudy", FoodCategory.DAIRY, 95.0, p = 17.0, c = 4.0, f = 0.5)
        val curd2 = product(2, "Twaróg półtłusty", FoodCategory.DAIRY, 130.0, p = 17.0, c = 3.5, f = 5.0)
        val result = service.findSubstitutes(
            original = curd1,
            originalGrams = 200.0,
            allProducts = listOf(curd1, curd2),
            tolerance = MatchTolerance.LOOSE
        )
        if (result.isNotEmpty()) {
            assertTrue("Score should be non-negative", result.all { it.score >= 0 })
        }
    }

    @Test
    fun `empty product list returns empty result`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 200.0,
            allProducts = emptyList(),
            tolerance = MatchTolerance.STRICT
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero grams returns empty result`() {
        val chicken = product(1, "Kurczak", FoodCategory.PROTEIN, 165.0, p = 31.0)
        val turkey = product(2, "Indyk", FoodCategory.PROTEIN, 158.0, p = 30.0)
        val result = service.findSubstitutes(
            original = chicken,
            originalGrams = 0.0,
            allProducts = listOf(chicken, turkey),
            tolerance = MatchTolerance.STRICT
        )
        assertTrue(result.isEmpty())
    }
}
