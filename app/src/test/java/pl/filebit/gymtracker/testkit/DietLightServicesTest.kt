package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.repository.DietaryKnowledgeRepository
import pl.filebit.gymtracker.data.repository.EmergencyFoodEstimates
import pl.filebit.gymtracker.data.repository.QuickComposeService

/**
 * v1.27 — FAZA 2.4 (blok dietowy) — lekkie serwisy diety bez stanu DB.
 *
 *  - QuickComposeService: dobiera gramaturę produktów pod target kcal/makro.
 *  - EmergencyFoodEstimates: statyczna tabela dań na mieście + lookup.
 *  - DietaryKnowledgeRepository: ładuje dietary_knowledge.json z assetów.
 */
class DietLightServicesTest : TestHarness() {

    private fun product(
        name: String, cat: FoodCategory,
        kcal: Double, p: Double, c: Double, f: Double
    ) = FoodProduct(
        name = name, category = cat, kcalPer100g = kcal,
        proteinPer100g = p, carbsPer100g = c, fatPer100g = f
    )

    @Test
    fun `QuickCompose dobiera gramature pod target kcal i makro`() {
        val chicken = product("Pierś z kurczaka", FoodCategory.PROTEIN, 165.0, 31.0, 0.0, 3.6)
        val rice = product("Ryż biały", FoodCategory.CARBS, 130.0, 2.7, 28.0, 0.3)
        val oil = product("Oliwa z oliwek", FoodCategory.FAT, 884.0, 0.0, 0.0, 100.0)
        val broccoli = product("Brokuł", FoodCategory.VEGETABLE, 34.0, 2.8, 7.0, 0.4)

        val r = QuickComposeService().compose(
            protein = chicken, carb = rice, fat = oil, vegetable = broccoli,
            targetKcal = 600, targetProteinG = 50, targetFatG = 20
        )

        val tr = TraceReport("quick-compose")
            .section("WEJŚCIE")
            .kv("target", "600 kcal / 50 g B / 20 g T")
            .section("WYNIK compose()")
            .kv("gramatura", "B ${r.proteinGrams}g, W ${r.carbGrams}g, " +
                "T ${r.fatGrams}g, warzywa ${r.vegetableGrams}g")
            .kv("realne makro", "${r.realKcal} kcal / ${r.realProteinG}B / " +
                "${r.realCarbsG}W / ${r.realFatG}T")
            .section("OCENA")
        r.warnings.forEach { tr.note("ostrzeżenie: $it") }
        val kcalOk = r.realKcal in 450..750
        val proteinOk = r.realProteinG in 40..65
        tr.verdict("kcal w okolicy targetu", if (kcalOk) "OK" else "ROZJAZD",
            "${r.realKcal} vs 600")
        tr.verdict("białko w okolicy targetu", if (proteinOk) "OK" else "ROZJAZD",
            "${r.realProteinG} vs 50")
        tr.emit()

        assertTrue("gramatura białka dodatnia", r.proteinGrams > 0)
        assertTrue("gramatura węgli dodatnia", r.carbGrams > 0)
        assertTrue("realne kcal w rozsądnym oknie targetu (450..750)", kcalOk)
        assertTrue("realne białko w okolicy targetu (40..65 g)", proteinOk)
    }

    @Test
    fun `QuickCompose ostrzega gdy produkt bialkowy nie ma bialka`() {
        val fakeProtein = product("Woda", FoodCategory.PROTEIN, 0.0, 0.0, 0.0, 0.0)
        val rice = product("Ryż", FoodCategory.CARBS, 130.0, 2.7, 28.0, 0.3)
        val oil = product("Oliwa", FoodCategory.FAT, 884.0, 0.0, 0.0, 100.0)

        val r = QuickComposeService().compose(
            protein = fakeProtein, carb = rice, fat = oil, vegetable = null,
            targetKcal = 500, targetProteinG = 40, targetFatG = 15
        )
        assertTrue("compose zgłasza ostrzeżenie o braku białka",
            r.warnings.any { it.contains("białk", ignoreCase = true) })
    }

    @Test
    fun `EmergencyFood lookup po slowach kluczowych i lista popularnych`() {
        val kebabs = EmergencyFoodEstimates.findByKeyword("kebab xl")
        val popular = EmergencyFoodEstimates.POPULAR
        val all = EmergencyFoodEstimates.DISHES

        val tr = TraceReport("emergency-food")
            .section("DANE")
            .kv("dań w tabeli", all.size.toString())
            .section("LOOKUP")
            .kv("findByKeyword('kebab xl')", "${kebabs.size} trafień")
            .kv("POPULAR", "${popular.size} dań")
            .section("OCENA")
        val allHaveKcal = all.all { it.kcal > 0 }
        tr.verdict("każde danie ma kcal > 0", if (allHaveKcal) "OK" else "BŁĄD",
            "${all.count { it.kcal <= 0 }} dań bez kcal")
        tr.emit()

        assertTrue("tabela dań niepusta", all.isNotEmpty())
        assertTrue("'kebab' coś znajduje", kebabs.isNotEmpty())
        assertTrue("trafienia zawierają kebab", kebabs.all {
            it.displayName.contains("kebab", ignoreCase = true)
        })
        assertEquals("POPULAR ma 10 dań", 10, popular.size)
        assertTrue("każde danie ma dodatnie kcal", allHaveKcal)
        assertTrue("pusty/krótki query nie wybucha",
            EmergencyFoodEstimates.findByKeyword("a").isEmpty())
    }

    @Test
    fun `DietaryKnowledge laduje JSON z assetow`() {
        val repo = DietaryKnowledgeRepository(context)
        val k = repo.load()

        val tr = TraceReport("dietary-knowledge")
            .section("ZAŁADOWANE")
            .kv("version", k.version.toString())
            .kv("źródło", k.source)
            .kv("IG: low/medium/high", "${k.glycemic_index.low.size}/" +
                "${k.glycemic_index.medium.size}/${k.glycemic_index.high.size}")
            .kv("kategorie tipów", k.tips.keys.joinToString(", "))
            .section("LOOKUP")
            .kv("seasonalForMonth(6)", repo.seasonalForMonth(6).size.toString() + " pozycji")
            .kv("lowGiProducts()", repo.lowGiProducts().size.toString())
            .emit()

        assertTrue("knowledge ma produkty o niskim IG", repo.lowGiProducts().isNotEmpty())
        assertTrue("knowledge ma tipy", k.tips.isNotEmpty())
        assertTrue("seasonal dla czerwca coś zwraca", repo.seasonalForMonth(6).isNotEmpty())
        assertEquals("drugie load() z cache zwraca to samo", k.version, repo.load().version)
    }
}
