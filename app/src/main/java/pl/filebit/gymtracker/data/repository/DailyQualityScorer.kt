package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.FoodProduct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cele jakościowe diety per dzień. Bierzemy konserwatywnie — można dostroić.
 *
 * Źródła:
 *  - błonnik: PL Norma 25-35g (dorośli), wyższy = lepiej dla cytatu zdrowotnego
 *  - warzywa+owoce: WHO 400g/dzień minimum, sportowcy 600-800g
 *  - wapń: 1000mg (dorośli 19-50), 1200mg (>50, kobiety w ciąży)
 *  - potas: WHO 3500mg/dzień (większość Polaków je <2500)
 *  - sód: WHO max 2000mg/dzień (typowa dieta PL: 4000-5000mg)
 */
data class DailyQualityTarget(
    val fiberG: ClosedRange<Double>,         // 25.0..35.0
    val vegetablesAndFruitsG: ClosedRange<Double>, // 400.0..800.0
    val calciumMgMin: Double,                // 1000.0
    val potassiumMgMin: Double,              // 3500.0
    val sodiumMgMax: Double                  // 2300.0
) {
    companion object {
        fun forUser(gender: Gender, ageYears: Int): DailyQualityTarget {
            val fiberMin = if (gender == Gender.MALE) 30.0 else 25.0
            val fiberMax = if (gender == Gender.MALE) 38.0 else 32.0
            val calciumMin = if (ageYears > 50) 1200.0 else 1000.0
            return DailyQualityTarget(
                fiberG = fiberMin..fiberMax,
                vegetablesAndFruitsG = 400.0..800.0,
                calciumMgMin = calciumMin,
                potassiumMgMin = 3500.0,
                sodiumMgMax = 2300.0
            )
        }
    }
}

data class DailyQualitySummary(
    val fiberG: Double,
    val vegetablesAndFruitsG: Double,
    val calciumMg: Double,
    val potassiumMg: Double,
    val sodiumMg: Double,
    val uniqueProductsCount: Int,
    val target: DailyQualityTarget
) {
    /** 0..100, im więcej tym lepiej. */
    val overallScore: Int
        get() {
            val fiberScore = scoreInRange(fiberG, target.fiberG)
            val vegScore = scoreInRange(vegetablesAndFruitsG, target.vegetablesAndFruitsG)
            val calciumScore = scoreAtLeast(calciumMg, target.calciumMgMin)
            val potassiumScore = scoreAtLeast(potassiumMg, target.potassiumMgMin)
            val sodiumScore = scoreAtMost(sodiumMg, target.sodiumMgMax)
            val varietyScore = (uniqueProductsCount.coerceIn(0, 10) * 10)
            return ((fiberScore + vegScore + calciumScore + potassiumScore + sodiumScore + varietyScore) / 6.0).toInt()
        }

    val fiberOk: Boolean get() = fiberG in target.fiberG
    val vegOk: Boolean get() = vegetablesAndFruitsG in target.vegetablesAndFruitsG
    val calciumOk: Boolean get() = calciumMg >= target.calciumMgMin
    val potassiumOk: Boolean get() = potassiumMg >= target.potassiumMgMin
    val sodiumOk: Boolean get() = sodiumMg <= target.sodiumMgMax

    fun warnings(): List<String> = buildList {
        if (!fiberOk) {
            if (fiberG < target.fiberG.start) add("Mało błonnika: %.0fg (cel %.0f-%.0fg). Dodaj kasze, warzywa, owoce, pełnoziarniste pieczywo.".format(fiberG, target.fiberG.start, target.fiberG.endInclusive))
            else add("Bardzo dużo błonnika: %.0fg (cel %.0f-%.0fg). Może powodować dyskomfort jelitowy.".format(fiberG, target.fiberG.start, target.fiberG.endInclusive))
        }
        if (!vegOk && vegetablesAndFruitsG < target.vegetablesAndFruitsG.start) {
            add("Mało warzyw/owoców: %.0fg (cel %.0f-%.0fg). Dodaj sałatę, brokuły, paprykę, owoc.".format(vegetablesAndFruitsG, target.vegetablesAndFruitsG.start, target.vegetablesAndFruitsG.endInclusive))
        }
        if (!calciumOk) add("Mało wapnia: %.0fmg (cel min %.0fmg). Dodaj nabiał, tofu, sezam.".format(calciumMg, target.calciumMgMin))
        if (!potassiumOk) add("Mało potasu: %.0fmg (cel min %.0fmg). Dodaj banany, ziemniaki, awokado, szpinak.".format(potassiumMg, target.potassiumMgMin))
        if (!sodiumOk) add("Za dużo sodu: %.0fmg (max %.0fmg). Mniej soli, wędlin, konserw.".format(sodiumMg, target.sodiumMgMax))
        if (uniqueProductsCount < 5) add("Mało różnorodności: tylko $uniqueProductsCount produktów. Dieta monotonna sprzyja niedoborom mikro.")
    }

    private fun scoreInRange(value: Double, range: ClosedRange<Double>): Int = when {
        value in range -> 100
        value < range.start -> ((value / range.start) * 100).toInt().coerceIn(0, 100)
        else -> {
            val excess = (value - range.endInclusive) / range.endInclusive
            (100 - (excess * 50).toInt()).coerceIn(0, 100)
        }
    }

    private fun scoreAtLeast(value: Double, min: Double): Int =
        ((value / min) * 100).toInt().coerceIn(0, 100)

    private fun scoreAtMost(value: Double, max: Double): Int =
        if (value <= max) 100
        else (100 - ((value - max) / max * 100).toInt()).coerceIn(0, 100)
}

/**
 * Liczy jakość diety dnia z istniejących MealEntry × FoodProduct.
 * Pure function — testowalne.
 */
@Singleton
class DailyQualityScorer @Inject constructor() {

    fun compute(
        entries: List<MealEntryWithMacros>,
        target: DailyQualityTarget
    ): DailyQualitySummary {
        var fiber = 0.0
        var calcium = 0.0
        var potassium = 0.0
        var sodium = 0.0
        var vegFruitsGrams = 0.0
        val uniqueProducts = HashSet<Long>()

        for (m in entries) {
            val factor = m.entry.grams / 100.0
            fiber += m.product.fiberPer100g * factor
            calcium += m.product.calciumMgPer100g * factor
            potassium += m.product.potassiumMgPer100g * factor
            sodium += m.product.sodiumMgPer100g * factor
            if (m.product.category == FoodCategory.VEGETABLE || m.product.category == FoodCategory.FRUIT) {
                vegFruitsGrams += m.entry.grams
            }
            uniqueProducts += m.product.id
        }

        return DailyQualitySummary(
            fiberG = fiber,
            vegetablesAndFruitsG = vegFruitsGrams,
            calciumMg = calcium,
            potassiumMg = potassium,
            sodiumMg = sodium,
            uniqueProductsCount = uniqueProducts.size,
            target = target
        )
    }

    /** Pomocnicza wersja z surowymi MealEntry i mapą produktów. */
    fun computeRaw(
        entries: List<MealEntry>,
        productsById: Map<Long, FoodProduct>,
        target: DailyQualityTarget
    ): DailyQualitySummary {
        val withMacros = entries.mapNotNull { e ->
            productsById[e.productId]?.let { p -> e.macrosFor(p) }
        }
        return compute(withMacros, target)
    }
}
