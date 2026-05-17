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
 * Generuje listę zakupów z AKTUALNEGO planu dnia × liczba dni.
 *
 * v1.29.5: przejście z „zakresu dat" na „aktualny plan × N". Plan diety i tak
 * się powtarza dzień w dzień (carry-over), więc lista = składniki najnowszego
 * zaplanowanego dnia pomnożone przez 1 / 3 / 7. Generujesz nowy plan → lista
 * liczy się od nowego; stare dni nie mieszają się do wyniku.
 *
 * Algorytm:
 *  1. Znajdź najnowszy dzień z posiłkami = „aktualny plan"
 *  2. Zsumuj gramy per produkt dla tego JEDNEGO dnia
 *  3. Pomnóż przez liczbę dni + narzut bezpieczeństwa (~10%)
 *  4. Zaokrąglij gramaturę do kupowalnych wartości (50/100/250 g)
 */
@Singleton
class ShoppingListGenerator @Inject constructor(
    private val mealDao: MealEntryDao,
    private val productDao: FoodProductDao,
    private val shoppingDao: ShoppingListDao
) {
    /**
     * Generuje listę: składniki aktualnego planu dnia × [days]. Stara lista
     * usuwana — utrzymujemy 1 aktywną. Zwraca id nowo utworzonej listy.
     */
    suspend fun generate(
        days: Int,
        listName: String,
        safetyMarginPct: Double = 0.10
    ): Long {
        require(days > 0) { "days musi być > 0" }
        val dayMs = 24 * 3600 * 1000L
        // Szukamy najnowszego dnia z posiłkami w ostatnich 90 dniach.
        val windowEnd = System.currentTimeMillis() + dayMs
        val recent = mealDao.getForDateRange(windowEnd - 90 * dayMs, windowEnd)
        if (recent.isEmpty()) {
            val today = startOfDay(System.currentTimeMillis())
            return shoppingDao.replaceWithSingleList(
                ShoppingList(name = listName, fromDateMs = today, toDateMs = today + dayMs),
                emptyList()
            )
        }
        // Aktualny plan = najnowszy dzień, dla którego są wpisy.
        val planDay = recent.maxOf { startOfDay(it.dateMs) }
        val planEntries = recent.filter { startOfDay(it.dateMs) == planDay }

        // Suma gramów per produkt dla tego jednego dnia.
        val byProduct = HashMap<Long, Double>()
        for (e in planEntries) {
            byProduct[e.productId] = (byProduct[e.productId] ?: 0.0) + e.grams
        }

        val products = productDao.getByIds(byProduct.keys.toList()).associateBy { it.id }
        val items = byProduct.mapNotNull { (pid, gramsOneDay) ->
            val p = products[pid] ?: return@mapNotNull null
            val total = gramsOneDay * days * (1.0 + safetyMarginPct)
            ShoppingListItem(
                listId = 0L, // ustawione w replaceWithSingleList
                productId = pid,
                productName = p.name,
                category = p.category,
                grams = roundShoppingGrams(total, p.category),
                occurrences = 1
            )
        }
        return shoppingDao.replaceWithSingleList(
            ShoppingList(name = listName, fromDateMs = planDay, toDateMs = planDay + days * dayMs),
            items
        )
    }

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
