package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.db.dao.MealEntryDao
import pl.filebit.gymtracker.data.db.dao.ShoppingListDao
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.ShoppingList
import pl.filebit.gymtracker.data.entity.ShoppingListItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Generuje listę zakupów z faktycznych wpisów MealEntry w zakresie dat.
 *
 * Algorytm:
 *  1. Pobierz wszystkie MealEntry z zakresu [fromMs, toMs)
 *  2. Pogrupuj po productId, zsumuj gramy, policz unique dni (occurrences)
 *  3. Dorzuć narzut bezpieczeństwa (~10%) bo:
 *     - świeże produkty trzeba kupić w typowych opakowaniach
 *     - czasem zostanie nadwyżka, ale lepsze niż pójście drugi raz po brakującą rzecz
 *  4. Zaokrąglij gramaturę do "rozsądnej" wartości (najbliższe 50/100/250g)
 *  5. Posortuj alfabetycznie wewnątrz kategorii
 */
@Singleton
class ShoppingListGenerator @Inject constructor(
    private val mealDao: MealEntryDao,
    private val productDao: FoodProductDao,
    private val shoppingDao: ShoppingListDao
) {
    /**
     * Generuje listę z [fromMs] do [toMs] (exclusive). Stara lista usuwana — utrzymujemy 1 aktywną.
     * Zwraca id nowo utworzonej listy.
     */
    suspend fun generate(
        fromMs: Long,
        toMs: Long,
        listName: String,
        safetyMarginPct: Double = 0.10
    ): Long {
        require(toMs > fromMs) { "toMs musi być > fromMs" }
        val entries = mealDao.getForDateRange(fromMs, toMs)
        if (entries.isEmpty()) {
            // Pusta lista — i tak zapisz, żeby UI mógł pokazać "brak posiłków"
            return shoppingDao.replaceWithSingleList(
                ShoppingList(name = listName, fromDateMs = fromMs, toDateMs = toMs),
                emptyList()
            )
        }
        // ProductId → pair(grams, set of unique days)
        val byProduct = HashMap<Long, ProductAgg>()
        for (e in entries) {
            val agg = byProduct.getOrPut(e.productId) { ProductAgg() }
            agg.grams += e.grams
            agg.dayKeys += startOfDay(e.dateMs)
        }

        val productIds = byProduct.keys.toList()
        val products = productDao.getByIds(productIds).associateBy { it.id }

        val items = byProduct.mapNotNull { (pid, agg) ->
            val p = products[pid] ?: return@mapNotNull null
            val totalGrams = agg.grams * (1.0 + safetyMarginPct)
            ShoppingListItem(
                listId = 0L, // ustawione w replaceWithSingleList
                productId = pid,
                productName = p.name,
                category = p.category,
                grams = roundShoppingGrams(totalGrams, p.category),
                occurrences = agg.dayKeys.size
            )
        }
        return shoppingDao.replaceWithSingleList(
            ShoppingList(name = listName, fromDateMs = fromMs, toDateMs = toMs),
            items
        )
    }

    private class ProductAgg(
        var grams: Double = 0.0,
        val dayKeys: MutableSet<Long> = HashSet()
    )

    private fun startOfDay(ms: Long): Long {
        // Bierzemy "kalendarzowy" początek dnia w UTC dla uproszczenia (klucz unikatowy)
        return ms - (ms % (24 * 3600 * 1000))
    }

    /**
     * Zaokrąglanie do kupowalnych ilości:
     *  - VEGETABLE/FRUIT: do 100g w górę
     *  - DAIRY: do 50g w górę (twaróg często 200g, jogurt 150g)
     *  - PROTEIN (mięso/ryba): do 100g w górę
     *  - CARBS: <500g→ co 100g, ≥500g → co 250g
     *  - FAT (oliwa/orzechy): do 25g w górę
     *  - OTHER: do 50g w górę
     */
    private fun roundShoppingGrams(grams: Double, category: FoodCategory): Double {
        val step = when (category) {
            FoodCategory.VEGETABLE, FoodCategory.FRUIT -> 100.0
            FoodCategory.DAIRY -> 50.0
            FoodCategory.PROTEIN -> 100.0
            FoodCategory.CARBS -> if (grams < 500) 100.0 else 250.0
            FoodCategory.FAT -> 25.0
            FoodCategory.OTHER -> 50.0
        }
        return ceil(grams / step) * step
    }

    /**
     * Eksportuje listę jako tekst — do schowka/Share/notes.
     * Format:
     *
     *   🛒 Lista zakupów: 5 V 2026 - 11 V 2026
     *
     *   --- Białko ---
     *   - Kurczak pierś: 1.5 kg (5 dni)
     *   - Twaróg chudy: 600 g (3 dni)
     *
     *   --- Węglowodany ---
     *   ...
     */
    fun exportAsText(list: ShoppingList, items: List<ShoppingListItem>): String = buildString {
        val df = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("pl", "PL"))
        appendLine("🛒 ${list.name}")
        appendLine("${df.format(java.util.Date(list.fromDateMs))} – ${df.format(java.util.Date(list.toDateMs - 1))}")
        appendLine()
        if (items.isEmpty()) {
            appendLine("(brak posiłków w wybranym zakresie)")
            return@buildString
        }
        items.groupBy { it.category }.toSortedMap(compareBy { it.ordinal }).forEach { (cat, group) ->
            appendLine("--- ${categoryLabel(cat)} ---")
            group.sortedBy { it.productName }.forEach { item ->
                val unit = if (item.grams >= 1000) "%.1f kg".format(item.grams / 1000.0)
                else "${item.grams.roundToInt()} g"
                val occ = if (item.occurrences > 1) " (${item.occurrences} dni)" else ""
                val mark = if (item.isPurchased) "[x] " else "[ ] "
                appendLine("$mark${item.productName}: $unit$occ")
            }
            appendLine()
        }
    }

    private fun categoryLabel(c: FoodCategory): String = when (c) {
        FoodCategory.PROTEIN -> "Białko"
        FoodCategory.CARBS -> "Węglowodany"
        FoodCategory.FAT -> "Tłuszcze"
        FoodCategory.DAIRY -> "Nabiał"
        FoodCategory.VEGETABLE -> "Warzywa"
        FoodCategory.FRUIT -> "Owoce"
        FoodCategory.OTHER -> "Inne"
    }
}
