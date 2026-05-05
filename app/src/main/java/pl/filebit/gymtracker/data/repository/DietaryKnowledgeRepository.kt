package pl.filebit.gymtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GIProduct(val name: String, val gi: Int)

@Serializable
data class GlycemicIndex(
    val low: List<GIProduct>,
    val medium: List<GIProduct>,
    val high: List<GIProduct>
)

@Serializable
data class DietaryKnowledge(
    val version: Int,
    val source: String,
    val seasonal_fruits_by_month: Map<String, List<String>>,
    val seasonal_vegetables_by_month: Map<String, List<String>>,
    val glycemic_index: GlycemicIndex,
    val tips: Map<String, List<String>>
)

/**
 * Lazy load `assets/dietary_knowledge.json` (raz na sesję). Dostarcza:
 *  - sezonowe owoce/warzywa per miesiąc (1..12)
 *  - listy IG (LOW/MEDIUM/HIGH)
 *  - tipy dietetyczne (diabetic/insulin_resistance/gluten_free/balance)
 */
@Singleton
class DietaryKnowledgeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cache: DietaryKnowledge? = null

    fun load(): DietaryKnowledge {
        cache?.let { return it }
        val raw = context.assets.open("dietary_knowledge.json").bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<DietaryKnowledge>(raw)
        cache = parsed
        return parsed
    }

    /** Sezonowe (owoce + warzywa) dla danego miesiąca 1..12. */
    fun seasonalForMonth(month: Int): List<String> {
        val k = load()
        val key = month.toString()
        val fruits = k.seasonal_fruits_by_month[key].orEmpty()
        val vegs = k.seasonal_vegetables_by_month[key].orEmpty()
        return fruits + vegs
    }

    /** LOW IG produkty — preferowane dla cukrzycy/IR. */
    fun lowGiProducts(): List<GIProduct> = load().glycemic_index.low

    /** Tipy per kategoria (diabetic / insulin_resistance / gluten_free / balance). */
    fun tipsFor(category: String): List<String> = load().tips[category].orEmpty()
}
