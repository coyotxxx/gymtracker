package pl.filebit.gymtracker.data.seed

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct

@Serializable
private data class SeedFoodProduct(
    val name: String,
    val category: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val calciumMgPer100g: Double = 0.0,
    val potassiumMgPer100g: Double = 0.0,
    val sodiumMgPer100g: Double = 0.0
)

class FoodProductSeeder(
    private val context: Context,
    private val dao: FoodProductDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Idempotentny seed:
     *  - jeśli baza pusta → ładuje wszystko
     *  - jeśli baza ma już produkty → dodaje TYLKO brakujące (po nazwie)
     *
     * Dzięki temu update aplikacji z nowymi produktami w JSON nie wymaga
     * destructive migration i nie kasuje favorites/custom usera.
     */
    suspend fun seedIfEmpty() {
        val raw = context.assets.open("food_products.json").bufferedReader().use { it.readText() }
        val seedList = json.decodeFromString<List<SeedFoodProduct>>(raw)
        val seedEntities = seedList.map { s ->
            FoodProduct(
                name = s.name,
                category = runCatching { FoodCategory.valueOf(s.category) }
                    .getOrDefault(FoodCategory.OTHER),
                kcalPer100g = s.kcalPer100g,
                proteinPer100g = s.proteinPer100g,
                carbsPer100g = s.carbsPer100g,
                fatPer100g = s.fatPer100g,
                fiberPer100g = s.fiberPer100g,
                calciumMgPer100g = s.calciumMgPer100g,
                potassiumMgPer100g = s.potassiumMgPer100g,
                sodiumMgPer100g = s.sodiumMgPer100g,
                isCustom = false,
                source = "seed"
            )
        }

        if (dao.count() == 0) {
            dao.insertAll(seedEntities)
            return
        }

        // Dodaj tylko brakujące — match po nazwie (case-insensitive)
        val existingNames = dao.allNamesLower().toHashSet()
        val newOnly = seedEntities.filter { it.name.lowercase() !in existingNames }
        if (newOnly.isNotEmpty()) dao.insertAll(newOnly)
    }
}
