package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sugestia zamiennika produktu:
 *  - `product` — alternatywny FoodProduct
 *  - `suggestedGrams` — gramatura która trafia w oryginalne kcal
 *  - `*Delta` — różnice makro w gramach (po przeliczeniu na suggestedGrams)
 *  - `score` — niższy = lepszy (suma znormalizowanych odchyleń od oryginału)
 */
data class Substitute(
    val product: FoodProduct,
    val suggestedGrams: Double,
    val kcalDelta: Double,
    val proteinDelta: Double,
    val carbsDelta: Double,
    val fatDelta: Double,
    val score: Double
)

enum class MatchTolerance {
    /** ±10% każdego makro vs oryginał. Najlepszy do POSIŁKÓW kompletnych. */
    STRICT,
    /** ±25% każdego makro lub ±15g bezwzględne. Pełniejsza lista. */
    LOOSE,
    /** Tylko zbliżone kcal (±15%). Makro ignorowane. Dla "po prostu coś podobnego". */
    KCAL_ONLY
}

/**
 * Znajduje zamienniki produktu z zachowaniem makro.
 *
 * Filozofia:
 *  - Trzymamy makro (B/W/T) ±X% od oryginału
 *  - Gramatura przeliczana tak, żeby kcal się zgadzały
 *  - Domyślnie tylko ta sama kategoria (mięso↔mięso, węgle↔węgle)
 *  - Sortowanie po dystansie — najpodobniejsze pierwsze
 */
@Singleton
class SubstituteService @Inject constructor() {

    fun findSubstitutes(
        original: FoodProduct,
        originalGrams: Double,
        allProducts: List<FoodProduct>,
        tolerance: MatchTolerance = MatchTolerance.STRICT,
        sameCategoryOnly: Boolean = true,
        limit: Int = 8
    ): List<Substitute> {
        if (originalGrams <= 0.0) return emptyList()
        val factor = originalGrams / 100.0
        val origKcal = original.kcalPer100g * factor
        val origProtein = original.proteinPer100g * factor
        val origCarbs = original.carbsPer100g * factor
        val origFat = original.fatPer100g * factor

        if (origKcal <= 0.0) return emptyList()

        val candidates = allProducts.asSequence()
            .filter { it.id != original.id }
            .filter { !sameCategoryOnly || it.category == original.category || sameCategoryAlt(original.category, it.category) }
            .filter { it.kcalPer100g > 0.0 }

        val matches = mutableListOf<Substitute>()
        for (cand in candidates) {
            // Wyznacz gramaturę żeby trafić w oryginalne kcal
            val suggestedGrams = (origKcal / cand.kcalPer100g * 100.0).coerceIn(5.0, 1000.0)
            val candFactor = suggestedGrams / 100.0
            val candKcal = cand.kcalPer100g * candFactor
            val candProtein = cand.proteinPer100g * candFactor
            val candCarbs = cand.carbsPer100g * candFactor
            val candFat = cand.fatPer100g * candFactor

            if (!withinTolerance(
                    origKcal, candKcal,
                    origProtein, candProtein,
                    origCarbs, candCarbs,
                    origFat, candFat,
                    tolerance
                )
            ) continue

            // Score = suma znormalizowanych odchyleń (im mniej tym lepiej)
            val score = relDiff(origKcal, candKcal) +
                relDiff(origProtein, candProtein) * 1.5 +   // białko ważniejsze
                relDiff(origCarbs, candCarbs) +
                relDiff(origFat, candFat)

            matches += Substitute(
                product = cand,
                suggestedGrams = roundGrams(suggestedGrams),
                kcalDelta = candKcal - origKcal,
                proteinDelta = candProtein - origProtein,
                carbsDelta = candCarbs - origCarbs,
                fatDelta = candFat - origFat,
                score = score
            )
        }
        return matches.sortedBy { it.score }.take(limit)
    }

    /**
     * Niektóre kategorie są "kompatybilne" w realnym jedzeniu:
     *  - PROTEIN ↔ DAIRY (twaróg jako alternatywa dla mięsa, jeśli białkowy)
     */
    private fun sameCategoryAlt(a: FoodCategory, b: FoodCategory): Boolean {
        if (a == b) return true
        val pair = setOf(a, b)
        return pair == setOf(FoodCategory.PROTEIN, FoodCategory.DAIRY)
    }

    private fun withinTolerance(
        oKcal: Double, cKcal: Double,
        oProt: Double, cProt: Double,
        oCarb: Double, cCarb: Double,
        oFat: Double, cFat: Double,
        tol: MatchTolerance
    ): Boolean = when (tol) {
        MatchTolerance.STRICT -> {
            // ±10% każdego makro (lub ±3g bezwzględne dla małych liczb)
            inTol(oProt, cProt, 0.10, 3.0) &&
                inTol(oCarb, cCarb, 0.10, 5.0) &&
                inTol(oFat, cFat, 0.10, 3.0) &&
                inTol(oKcal, cKcal, 0.05, 15.0)
        }
        MatchTolerance.LOOSE -> {
            inTol(oProt, cProt, 0.25, 8.0) &&
                inTol(oCarb, cCarb, 0.25, 12.0) &&
                inTol(oFat, cFat, 0.25, 8.0) &&
                inTol(oKcal, cKcal, 0.10, 30.0)
        }
        MatchTolerance.KCAL_ONLY -> inTol(oKcal, cKcal, 0.15, 30.0)
    }

    /** True jeśli różnica jest w ±relPct LUB w ±absMin gramów (cokolwiek większe). */
    private fun inTol(a: Double, b: Double, relPct: Double, absMin: Double): Boolean {
        val diff = abs(a - b)
        val allowed = maxOf(absMin, abs(a) * relPct)
        return diff <= allowed
    }

    private fun relDiff(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        if (a == 0.0) return 1.0
        return abs(a - b) / abs(a)
    }

    /** Zaokrągla gramaturę do "sensownej" wartości: <50 → krok 5, <200 → krok 10, ≥200 → krok 25. */
    private fun roundGrams(g: Double): Double {
        val step = when {
            g < 50 -> 5.0
            g < 200 -> 10.0
            else -> 25.0
        }
        return (g / step).roundToInt() * step
    }
}
