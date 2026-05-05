package pl.filebit.gymtracker.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.RecipeDao
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.Recipe
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Serializable
private data class SeedMacros(
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val fiber: Double? = null,
    val weight_g: Double? = null
)

@Serializable
private data class SeedRecipe(
    val name: String,
    val portions: Int = 1,
    val ingredients_raw: List<String> = emptyList(),
    val instructions: String = "",
    val macros: SeedMacros = SeedMacros(),
    val source: String = "sniadania",
    val mealCategory: String? = null
)

/**
 * Ładuje 79 przepisów Natalii Jurkian (51 śniadań + 28 obiadów) z assets/recipes.json
 * przy pierwszym uruchomieniu. source = "seed_jurkian".
 *
 * Re-seedowanie odbywa się tylko jeśli liczba pozycji "seed_jurkian" w bazie = 0
 * (czyli pierwszy install lub po wipe).
 */
@Singleton
class RecipeSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RecipeDao
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun seedIfEmpty() {
        if (dao.countBySource(SOURCE) > 0) return

        val raw = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        val seedList = json.decodeFromString<List<SeedRecipe>>(raw)
        val recipes = seedList.mapNotNull { it.toRecipe() }
        dao.insertAll(recipes)
    }

    private fun SeedRecipe.toRecipe(): Recipe? {
        if (name.isBlank() || macros.kcal == null) return null

        val mealType = when (source) {
            "sniadania" -> MealType.BREAKFAST
            "czas-na-obiad" -> MealType.LUNCH
            else -> MealType.SNACK
        }

        // Per-serving wartości (PDF podaje TOTAL dla wszystkich porcji)
        val portions = portions.coerceAtLeast(1)
        val perKcal = ((macros.kcal ?: 0.0) / portions).roundToInt()
        val perProtein = ((macros.protein ?: 0.0) / portions).roundToInt()
        val perCarbs = ((macros.carbs ?: 0.0) / portions).roundToInt()
        val perFat = ((macros.fat ?: 0.0) / portions).roundToInt()
        val perFiber = (macros.fiber ?: 0.0) / portions

        val rawIngredients = ingredients_raw.joinToString("\n")

        return Recipe(
            name = name,
            mealType = mealType,
            ingredientsJson = "[]",  // seed nie ma mappingu na productId
            instructions = instructions,
            prepMinutes = estimatePrepMinutes(instructions),
            kcalPerServing = perKcal,
            proteinPerServing = perProtein,
            carbsPerServing = perCarbs,
            fatPerServing = perFat,
            fiberPerServingG = perFiber,
            servings = portions,
            rawIngredientsText = rawIngredients,
            isAiGenerated = false,
            source = SOURCE,
            mealCategory = mealCategory,
            isFavorite = false
        )
    }

    /** Heurystyka: liczymy zdania w instructions × ~2 min lub fallback 15. */
    private fun estimatePrepMinutes(instructions: String): Int {
        if (instructions.isBlank()) return 15
        val sentences = instructions.split(Regex("[.!?]\\s+|\n")).count { it.trim().length > 5 }
        return (sentences * 2).coerceIn(5, 45)
    }

    companion object {
        const val SOURCE = "seed_jurkian"
    }
}
