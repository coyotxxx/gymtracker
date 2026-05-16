package pl.filebit.gymtracker.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wynik lookup w OpenFoodFacts.
 *  - Found: produkt znaleziony, mamy podstawowe makro per 100g
 *  - PartialData: znaleziony ale brak istotnych pól (np. brak kcal lub białka)
 *  - NotFound: barkod nie istnieje w bazie (lub 404)
 *  - Error: problem sieci/parsowania
 */
sealed class OpenFoodFactsResult {
    data class Found(val product: FoodProduct, val rawName: String, val brand: String?) : OpenFoodFactsResult()
    data class PartialData(val partialProduct: FoodProduct, val missingFields: List<String>) : OpenFoodFactsResult()
    object NotFound : OpenFoodFactsResult()
    data class Error(val message: String) : OpenFoodFactsResult()
}

/**
 * Wynik wyszukiwania OpenFoodFacts po nazwie (Etap 2 — ekran "Dodaj produkt").
 *  - Success: lista produktów z kompletnym makro, z polskiego rynku
 *  - Empty: brak wyników (lub za krótkie zapytanie)
 *  - Error: problem sieci/parsowania
 */
sealed class OffSearchResult {
    data class Success(val products: List<FoodProduct>) : OffSearchResult()
    object Empty : OffSearchResult()
    data class Error(val message: String) : OffSearchResult()
}

/**
 * Klient OpenFoodFacts API v2.
 *
 * Endpoint: https://world.openfoodfacts.org/api/v2/product/{barcode}.json
 * Bez konta, bez klucza, bez tokena. Wymaga User-Agent header.
 *
 * Zwraca JSON z kluczowymi polami:
 *  - product_name (PL fallback z product_name_pl)
 *  - brands
 *  - nutriments.energy-kcal_100g (LUB energy_100g w kJ → konwersja)
 *  - nutriments.proteins_100g
 *  - nutriments.carbohydrates_100g
 *  - nutriments.fat_100g
 *  - nutriments.fiber_100g
 *  - nutriments.calcium_100g (gramy → konwersja na mg)
 *  - nutriments.potassium_100g
 *  - nutriments.sodium_100g
 *  - allergens (lista CSV)
 *  - categories_tags (lista typu "en:bread")
 */
@Singleton
class OpenFoodFactsClient @Inject constructor() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val userAgent = "GymTracker/0.95 (Android, hobby project)"

    suspend fun lookup(barcode: String): OpenFoodFactsResult = withContext(Dispatchers.IO) {
        if (!barcode.matches(Regex("^[0-9]{6,14}$"))) {
            return@withContext OpenFoodFactsResult.Error("Nieprawidłowy format barkodu: $barcode")
        }

        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext OpenFoodFactsResult.Error("HTTP ${resp.code}")
                }
                val root = json.parseToJsonElement(body).jsonObject
                val status = root["status"]?.jsonPrimitive?.content
                if (status != "1" && status != "success" && root["product"] == null) {
                    return@withContext OpenFoodFactsResult.NotFound
                }
                val product = root["product"]?.jsonObject ?: return@withContext OpenFoodFactsResult.NotFound
                parse(product, barcode)
            }
        } catch (e: Exception) {
            OpenFoodFactsResult.Error("Sieć: ${e.message}")
        }
    }

    /**
     * Wyszukiwanie produktów po nazwie. Subdomena pl. ogranicza do polskiego
     * rynku; dodatkowo odfiltrowujemy produkty których countries_tags jawnie
     * NIE zawiera Polski. Zwracamy tylko pozycje z kompletnym makro.
     */
    suspend fun searchByName(query: String): OffSearchResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext OffSearchResult.Empty

        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val url = "https://pl.openfoodfacts.org/cgi/search.pl" +
            "?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=50" +
            "&fields=code,product_name,product_name_pl,product_name_en,brands," +
            "nutriments,categories_tags,countries_tags"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext OffSearchResult.Error("HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val products = root["products"] as? kotlinx.serialization.json.JsonArray
                    ?: return@withContext OffSearchResult.Empty

                val mapped = products.mapNotNull { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    if (!isPolishMarket(obj)) return@mapNotNull null
                    val code = obj["code"]?.jsonPrimitive?.content.orEmpty()
                    when (val r = parse(obj, code)) {
                        is OpenFoodFactsResult.Found -> r.product
                        else -> null  // PartialData/NotFound → pomijamy (niekompletne makro)
                    }
                }
                // Deduplikacja po nazwie (OFF bywa zaszumione duplikatami)
                val unique = mapped.distinctBy { it.name.lowercase() }
                if (unique.isEmpty()) OffSearchResult.Empty
                else OffSearchResult.Success(unique)
            }
        } catch (e: Exception) {
            OffSearchResult.Error("Sieć: ${e.message}")
        }
    }

    /**
     * Produkt z polskiego rynku — countries_tags zawiera Polskę, LUB tag jest
     * pusty (brak danych — nie odrzucamy, subdomena pl. już zawęża wyniki).
     */
    private fun isPolishMarket(p: kotlinx.serialization.json.JsonObject): Boolean {
        val tags = (p["countries_tags"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        if (tags.isEmpty()) return true
        return tags.any { it.contains("poland", ignoreCase = true) || it.contains("polska", ignoreCase = true) }
    }

    private fun parse(p: kotlinx.serialization.json.JsonObject, barcode: String): OpenFoodFactsResult {
        val name = pickName(p)
        if (name.isBlank()) return OpenFoodFactsResult.NotFound

        val brand = p["brands"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()

        val nutriments = p["nutriments"]?.jsonObject
        val kcal = nutriments?.findDoubleField(listOf("energy-kcal_100g", "energy-kcal", "energy_100g")) ?: 0.0
        val kcalNormalized = if (kcal > 1500) kcal / 4.184 else kcal // jeśli to kJ → konwersja

        val protein = nutriments?.findDoubleField(listOf("proteins_100g", "proteins")) ?: 0.0
        val carbs = nutriments?.findDoubleField(listOf("carbohydrates_100g", "carbohydrates")) ?: 0.0
        val fat = nutriments?.findDoubleField(listOf("fat_100g", "fat")) ?: 0.0
        val fiber = nutriments?.findDoubleField(listOf("fiber_100g", "fiber")) ?: 0.0

        // OpenFoodFacts zwykle zwraca wapń/potas/sód w GRAMACH per 100g — konwertujemy na mg
        val calciumG = nutriments?.findDoubleField(listOf("calcium_100g", "calcium")) ?: 0.0
        val potassiumG = nutriments?.findDoubleField(listOf("potassium_100g", "potassium")) ?: 0.0
        val sodiumG = nutriments?.findDoubleField(listOf("sodium_100g", "sodium", "salt_100g")) ?: 0.0

        val category = guessCategory(p, name)

        val productCandidate = FoodProduct(
            id = 0L,
            name = if (brand != null) "${name.trim()} (${brand})" else name.trim(),
            category = category,
            kcalPer100g = kcalNormalized,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            fiberPer100g = fiber,
            calciumMgPer100g = calciumG * 1000,
            potassiumMgPer100g = potassiumG * 1000,
            sodiumMgPer100g = sodiumG * 1000,
            isCustom = true,
            source = "openfoodfacts",
            barcode = barcode,
            brand = brand
        )

        // Sanity check — czy mamy minimum kcal+białko+węgle+tłuszcz?
        val missing = mutableListOf<String>()
        if (kcalNormalized <= 0) missing += "kcal"
        if (protein <= 0 && carbs <= 0 && fat <= 0) missing += "makro"

        return if (missing.isEmpty()) {
            OpenFoodFactsResult.Found(productCandidate, name, brand)
        } else {
            OpenFoodFactsResult.PartialData(productCandidate, missing)
        }
    }

    private fun pickName(p: kotlinx.serialization.json.JsonObject): String {
        val plName = p["product_name_pl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val genericName = p["product_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val englishName = p["product_name_en"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        return plName ?: genericName ?: englishName ?: ""
    }

    private fun guessCategory(p: kotlinx.serialization.json.JsonObject, name: String): FoodCategory {
        val lower = name.lowercase()
        val tags = (p["categories_tags"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content }
            ?.joinToString(" ")
            ?.lowercase()
            ?: ""
        val combined = "$lower $tags"

        return when {
            listOf("milk", "yogurt", "yaourt", "cheese", "ser", "twaróg", "twarog", "mleko", "skyr", "kefir", "jogurt").any { combined.contains(it) }
                -> FoodCategory.DAIRY
            listOf("meat", "chicken", "beef", "pork", "fish", "salmon", "tuna", "kurczak", "wołow", "mięs", "schab", "indyk", "ryby", "łosoś").any { combined.contains(it) }
                -> FoodCategory.PROTEIN
            listOf("vegetable", "warzyw").any { combined.contains(it) }
                -> FoodCategory.VEGETABLE
            listOf("fruit", "owoce", "owoc").any { combined.contains(it) }
                -> FoodCategory.FRUIT
            listOf("oil", "butter", "fat", "olej", "masło", "margaryna").any { combined.contains(it) }
                -> FoodCategory.FAT
            listOf("rice", "pasta", "bread", "ryż", "makaron", "pieczywo", "kasza", "ziemniak", "płatki", "cereal").any { combined.contains(it) }
                -> FoodCategory.CARBS
            else -> FoodCategory.OTHER
        }
    }

    private fun kotlinx.serialization.json.JsonObject.findDoubleField(keys: List<String>): Double? {
        for (k in keys) {
            val v = this[k]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (v != null) return v
        }
        return null
    }
}
