package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.db.dao.MealEntryDao
import pl.filebit.gymtracker.data.db.dao.MealPrepPlanDao
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealPrepActionType
import pl.filebit.gymtracker.data.entity.MealPrepPlan
import pl.filebit.gymtracker.data.entity.MealPrepStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Generator planu meal prep z istniejących MealEntry (zwykle 2-3 dni do przodu).
 *
 * Algorytm:
 *  1. Pobierz wszystkie MealEntry z zakresu
 *  2. Pogrupuj po productId, sumuj gramy, policz ile slotów (porcji)
 *  3. Dla produktów wymagających gotowania (PROTEIN, niektóre CARBS) — wygeneruj kroki:
 *     - "Ugotuj 600g ryżu (do 4 porcji)"
 *     - "Upiecz 800g kurczaka (do 4 porcji)"
 *  4. Dla warzyw — krok "Pokrój"
 *  5. Sumaryczny czas + liczba pojemników
 */
@Singleton
class MealPrepPlanner @Inject constructor(
    private val mealDao: MealEntryDao,
    private val productDao: FoodProductDao,
    private val planDao: MealPrepPlanDao
) {

    /**
     * Generuje plan meal prep z zakresu [fromMs, toMs).
     * Zwraca id nowo utworzonego planu (zastępuje poprzedni).
     */
    suspend fun generate(
        fromMs: Long,
        toMs: Long,
        planName: String,
        dayMultiplier: Int = 1
    ): Long {
        require(toMs > fromMs) { "toMs musi być > fromMs" }

        // Meal prep mnoży dzienny plan ×N: czytamy posiłki TYLKO z pierwszego dnia
        // (aktywny plan) i skalujemy gramy/porcje przez dayMultiplier.
        val mult = dayMultiplier.coerceAtLeast(1)
        val oneDayMs = 24L * 60 * 60 * 1000
        val entries = mealDao.getForDateRange(fromMs, fromMs + oneDayMs)
        if (entries.isEmpty()) {
            // Pusty plan (UI pokaże "brak posiłków w zakresie")
            return planDao.replaceWithSinglePlan(
                MealPrepPlan(name = planName, fromDateMs = fromMs, toDateMs = toMs),
                emptyList()
            )
        }

        // Agregacja po produkcie
        data class Agg(var grams: Double = 0.0, val portions: MutableSet<Long> = HashSet())
        val byProduct = HashMap<Long, Agg>()
        for (e in entries) {
            val agg = byProduct.getOrPut(e.productId) { Agg() }
            agg.grams += e.grams
            // Klucz porcji: dateMs + mealType (jedna gotowana porcja per slot)
            agg.portions += (e.dateMs + e.mealType.ordinal)
        }

        val productIds = byProduct.keys.toList()
        val products = productDao.getByIds(productIds).associateBy { it.id }

        // Generuj kroki
        val steps = mutableListOf<MealPrepStep>()
        var orderIdx = 0
        var totalMinutes = 0
        val containers = HashSet<Long>()

        // Sortuj produkty: najpierw te które wymagają gotowania (PROTEIN, CARBS), potem warzywa, na końcu reszta
        val sortedProducts = byProduct.entries.sortedBy { (pid, _) ->
            val cat = products[pid]?.category
            when (cat) {
                FoodCategory.PROTEIN -> 0
                FoodCategory.CARBS -> 1
                FoodCategory.VEGETABLE -> 2
                FoodCategory.DAIRY -> 3
                FoodCategory.FAT -> 4
                FoodCategory.FRUIT -> 5
                FoodCategory.OTHER, null -> 6
            }
        }

        for ((pid, agg) in sortedProducts) {
            val product = products[pid] ?: continue
            // ×mult — gotowanie na N dni; +5% margin (odparowanie/utrata przy gotowaniu)
            val totalG = (agg.grams * mult * 1.05).toInt()
            val portionCount = agg.portions.size * mult
            containers += agg.portions

            val (action, minutes, desc) = stepFor(product, totalG, portionCount)
            steps += MealPrepStep(
                planId = 0L,
                orderIdx = orderIdx++,
                action = action,
                description = desc,
                estimatedMinutes = minutes,
                productNames = product.name,
                gramsTotal = totalG.toDouble()
            )
            totalMinutes += minutes
        }

        // Liczba pojemników = sloty z 1 dnia × liczba dni
        val containerCount = containers.size * mult

        // Krok końcowy: porcjowanie do pojemników
        if (containerCount > 1) {
            steps += MealPrepStep(
                planId = 0L,
                orderIdx = orderIdx++,
                action = MealPrepActionType.PORTION,
                description = "Podziel wszystko na $containerCount pojemników i schowaj do lodówki/zamrażarki",
                estimatedMinutes = 5 + containerCount,
                productNames = "",
                gramsTotal = 0.0
            )
            totalMinutes += 5 + containerCount
        }

        return planDao.replaceWithSinglePlan(
            MealPrepPlan(
                name = planName, fromDateMs = fromMs, toDateMs = toMs,
                containersCount = containerCount,
                totalMinutes = totalMinutes
            ),
            steps
        )
    }

    /**
     * Generuje krok dla danego produktu na podstawie kategorii.
     */
    private fun stepFor(product: FoodProduct, totalG: Int, portions: Int): Triple<MealPrepActionType, Int, String> {
        val name = product.name.lowercase()
        val portionLabel = if (portions > 1) "($portions porcj${if (portions > 1 && portions < 5) "e" else "i"})" else ""
        return when {
            // Mięso pieczone w piekarniku
            name.contains("kurczak") || name.contains("indyk") || name.contains("schab") || name.contains("polędwicz") -> {
                Triple(
                    MealPrepActionType.BAKE,
                    25 + portions * 2,
                    "Upiecz $totalG g ${product.name.lowercase()} $portionLabel — 180°C, ~25 min, doprawione"
                )
            }
            // Wołowina/ryba
            name.contains("wołowin") || name.contains("ryba") || name.contains("łosoś") || name.contains("dorsz") || name.contains("tuńczyk") -> {
                Triple(
                    MealPrepActionType.PAN_FRY,
                    15 + portions * 2,
                    "Usmaż $totalG g ${product.name.lowercase()} $portionLabel — patelnia, ~15 min"
                )
            }
            // Ryż / kasze / makaron — gotowanie
            name.contains("ryż") || name.contains("kasza") || name.contains("makaron") -> {
                Triple(
                    MealPrepActionType.BOIL,
                    20,
                    "Ugotuj $totalG g ${product.name.lowercase()} $portionLabel — wg opakowania"
                )
            }
            // Ziemniaki/bataty
            name.contains("ziemniak") || name.contains("batat") -> {
                Triple(
                    MealPrepActionType.BAKE,
                    35,
                    "Upiecz $totalG g ${product.name.lowercase()} $portionLabel — 200°C, ~35 min"
                )
            }
            // Warzywa - krojenie i pakowanie
            product.category == FoodCategory.VEGETABLE -> {
                Triple(
                    MealPrepActionType.CHOP,
                    5 + portions,
                    "Pokrój $totalG g ${product.name.lowercase()} $portionLabel"
                )
            }
            // Jaja
            name.contains("jajk") || name.contains("jaja") -> {
                Triple(
                    MealPrepActionType.BOIL,
                    10,
                    "Ugotuj jaja na twardo (~10 min)"
                )
            }
            // Owoce
            product.category == FoodCategory.FRUIT -> {
                Triple(
                    MealPrepActionType.CHOP,
                    3,
                    "Umyj i pokrój ${product.name.lowercase()} ($totalG g)"
                )
            }
            // Reszta (twaróg, jogurt, orzechy, oleje, mleko) — tylko porcjowanie
            else -> {
                Triple(
                    MealPrepActionType.STORE,
                    2,
                    "Przygotuj $totalG g ${product.name.lowercase()} $portionLabel (gotowy do użycia)"
                )
            }
        }
    }

    /**
     * Eksport jako tekst dla schowka/share.
     */
    fun exportAsText(plan: MealPrepPlan, steps: List<MealPrepStep>): String = buildString {
        val df = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("pl", "PL"))
        appendLine("🍱 ${plan.name}")
        appendLine("${df.format(java.util.Date(plan.fromDateMs))} – ${df.format(java.util.Date(plan.toDateMs - 1))}")
        appendLine("Pojemniki: ${plan.containersCount} · Czas: ~${plan.totalMinutes} min")
        appendLine()
        if (steps.isEmpty()) {
            appendLine("(brak posiłków w zakresie)")
            return@buildString
        }
        steps.forEachIndexed { idx, step ->
            val mark = if (step.isCompleted) "✓" else "${idx + 1}."
            appendLine("$mark ${step.description} (~${step.estimatedMinutes} min)")
        }
    }
}
