package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.network.OffSearchResult
import pl.filebit.gymtracker.data.network.OpenFoodFactsClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.74.0 (POSIŁKI N / ETAP 3): rozwiązywanie produktu po nazwie.
 *
 * Jedno źródło dla WSZYSTKICH ścieżek, które trafiają na produkt spoza bazy
 * (zdjęcie jedzenia, plan AI, add_meal). Zamiast po cichu pomijać / wstawiać
 * placeholder z zerowym makro — dociągamy realne dane z OpenFoodFacts i dodajemy
 * do naszej bazy. Kolejność:
 *   1) dopasowanie lokalne (dokładne ignoreCase → potem „zawiera"),
 *   2) OpenFoodFacts `searchByName` — pierwszy trafny → upsert (dedup po nazwie),
 *   3) null (brak nawet w OFF) — wtedy caller decyduje (placeholder / pominięcie).
 */
@Singleton
class ProductResolver @Inject constructor(
    private val foodDao: FoodProductDao,
    private val offClient: OpenFoodFactsClient,
    // nullable-default — Hilt wstrzykuje realny logger w apce, testy konstruują bez niego.
    private val diag: DiagnosticLogger? = null
) {
    suspend fun resolveOrNull(name: String): FoodProduct? {
        val q = name.trim()
        if (q.isBlank()) return null

        val local = foodDao.getAll()
        local.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return it }
        local.firstOrNull { it.name.contains(q, ignoreCase = true) }?.let { return it }

        // Brak lokalnie → spróbuj OpenFoodFacts (rynek PL, pełne makro).
        val off = runCatching { offClient.searchByName(q) }.getOrNull()
        if (off is OffSearchResult.Success) {
            val candidate = off.products.firstOrNull() ?: return null
            // Dedup: jeśli OFF zwrócił produkt o nazwie, którą już mamy — użyj istniejącego.
            local.firstOrNull { it.name.equals(candidate.name, ignoreCase = true) }?.let { return it }
            val id = foodDao.upsert(candidate.copy(id = 0))
            diag?.info(
                DiagnosticCategory.DIET, "ProductResolver", "product_added_from_off",
                "Dodano produkt z OpenFoodFacts: ${candidate.name} (szukano: \"$q\")",
                dataJson = """{"query":"$q","added":"${candidate.name}"}""", success = true
            )
            return foodDao.getById(id)
        }
        return null
    }
}
