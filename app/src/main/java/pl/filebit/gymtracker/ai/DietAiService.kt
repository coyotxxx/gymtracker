package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.util.computeDailyGoal
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiMealRecipe(
    val name: String,
    val ingredients: List<AiRecipeIngredient>,
    val instructions: String,
    val prepMinutes: Int = 15,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

@Serializable
data class AiRecipeIngredient(
    val productName: String,
    val grams: Int
)

@Serializable
data class AiDayPlan(
    val meals: List<AiMealRecipe>
)

data class GeneratedDayPlan(
    val mealsForSlots: List<Pair<MealType, AiMealRecipe>>
)

/**
 * Generuje pełny plan dnia żywieniowego dopasowany do profilu usera.
 *
 * AI uwzględnia WSZYSTKO:
 * - Aktualną wagę i cel (CUT/MAINTAIN/BULK)
 * - Liczbę treningów w tygodniu
 * - Płeć (modulator zapotrzebowania)
 * - Konfigurację dnia (liczba posiłków + okno żywieniowe)
 * - Listę dostępnych produktów (60 z bazy + custom)
 *
 * Output: 3-6 prostych przepisów (max 6 składników, ≤15 min) których suma
 * trafia w cel kcal i makro.
 */
@Singleton
class DietAiService @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val profileRepo: UserProfileRepository,
    private val dietRepo: DietRepository
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generateDayPlan(config: DietConfig): Result<GeneratedDayPlan> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("Skonfiguruj klucz AI w ustawieniach (Profil → Połączenie AI)"))
        }

        val profile = profileRepo.get()
        val goal = computeDailyGoal(profile)
        val products = dietRepo.observeAllProducts().first()

        val mealHours = config.mealHoursDecimal()
        val mealsCount = config.mealsPerDay
        val perMealKcal = if (mealsCount > 0) goal.kcal / mealsCount else 0

        val productsListing = products.joinToString("\n") { p ->
            "- ${p.name} (${p.kcalPer100g.toInt()} kcal/100g, B${p.proteinPer100g.toInt()}/W${p.carbsPer100g.toInt()}/T${p.fatPer100g.toInt()})"
        }

        val slotLabels = mealHours.indices.map { idx ->
            val time = config.formatTime(mealHours[idx])
            val label = labelForSlot(idx + 1, mealsCount)
            "$label ($time, ~$perMealKcal kcal)"
        }

        val goalLabel = when (profile.weightGoalType) {
            pl.filebit.gymtracker.data.entity.WeightGoalType.CUT -> "redukcja (deficyt kaloryczny)"
            pl.filebit.gymtracker.data.entity.WeightGoalType.BULK -> "masa (nadwyżka kaloryczna)"
            pl.filebit.gymtracker.data.entity.WeightGoalType.MAINTAIN -> "utrzymanie wagi"
            pl.filebit.gymtracker.data.entity.WeightGoalType.NONE -> "brak deklaracji (utrzymanie)"
        }

        val prompt = buildString {
            append("Jesteś personalnym dietetykiem. Wygeneruj plan dnia po polsku dla tej osoby:\n\n")
            append("PROFIL:\n")
            append("- Płeć: ${if (profile.gender == pl.filebit.gymtracker.data.entity.Gender.MALE) "mężczyzna" else "kobieta"}\n")
            profile.bodyweightKg?.let { append("- Waga: $it kg\n") }
            profile.targetWeightKg?.let { append("- Waga docelowa: $it kg\n") }
            append("- Cel: $goalLabel\n")
            append("- Treningów/tydzień: ${profile.daysPerWeek}\n")
            if (profile.injuriesNotes.isNotBlank()) {
                append("- Notatki/kontuzje: ${profile.injuriesNotes}\n")
            }
            append("\nCEL DZIENNY:\n")
            append("- ${goal.kcal} kcal\n")
            append("- Białko: ${goal.proteinG} g\n")
            append("- Węglowodany: ${goal.carbsG} g\n")
            append("- Tłuszcz: ${goal.fatG} g\n")

            append("\nLICZBA POSIŁKÓW: $mealsCount\n")
            append("OKNO ŻYWIENIOWE: ${config.eatingWindowHours}h (od ${"%02d:00".format(config.windowStartHour)} do ${"%02d:00".format(config.windowEndHour())})\n")
            append("CEL KCAL/POSIŁEK: ~$perMealKcal kcal\n\n")

            append("SLOTY:\n")
            slotLabels.forEach { append("- $it\n") }

            append("\nDOSTĘPNE PRODUKTY (używaj WYŁĄCZNIE z tej listy, nazwy DOKŁADNIE):\n")
            append(productsListing)

            append("\n\nWYMAGANIA:\n")
            append("- Każdy przepis: max 6 składników, max 15 min przygotowania\n")
            append("- Proste, zdrowe — bez egzotycznych dodatków\n")
            append("- Suma ${mealsCount} posiłków = cel dzienny (±10%)\n")
            append("- Każdy posiłek balansuje białko/węgle/tłuszcz\n")
            append("- Instrukcje krok po kroku, każdy krok w nowej linii\n")
            append("- productName z listy 1:1 (literówki = błąd)\n\n")

            append("ZWRÓĆ TYLKO JSON (bez markdown, bez tekstu poza JSON):\n")
            append("""
            {
              "meals": [
                {
                  "name": "Owsianka z twarogiem",
                  "ingredients": [
                    {"productName": "Płatki owsiane", "grams": 60},
                    {"productName": "Twaróg chudy", "grams": 150},
                    {"productName": "Jagody", "grams": 80}
                  ],
                  "instructions": "1. Zalej płatki wrzątkiem.\n2. Dodaj twaróg.\n3. Posyp jagodami.",
                  "prepMinutes": 5,
                  "kcal": 480,
                  "proteinG": 35,
                  "carbsG": 60,
                  "fatG": 8
                }
              ]
            }
            """.trimIndent())
        }

        val result = client.chat(cfg, listOf(AiMessage(AiRole.USER, prompt)))
        return result.mapCatching { raw ->
            Log.d("DietAiService", "raw response: ${raw.take(500)}")
            val cleaned = stripJsonFences(raw)
            val parsed = json.decodeFromString<AiDayPlan>(cleaned)
            if (parsed.meals.isEmpty()) error("AI zwróciło pusty plan")

            // Mapowanie sloty → MealType (analogicznie do DietViewModel)
            val typesForSlots: List<MealType> = when (mealsCount) {
                2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
                3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
                4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
                5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
                6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
                else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
            }
            val mealsForSlots = parsed.meals.zip(typesForSlots).map { (m, t) -> t to m }
            GeneratedDayPlan(mealsForSlots)
        }
    }

    private fun stripJsonFences(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.substringAfter("\n").substringBeforeLast("```").trim()
        }
        // Zostaw tylko od pierwszego { do ostatniego }
        val first = t.indexOf('{')
        val last = t.lastIndexOf('}')
        if (first >= 0 && last > first) {
            t = t.substring(first, last + 1)
        }
        return t
    }

    private fun labelForSlot(slot: Int, total: Int): String = when {
        total == 2 && slot == 1 -> "Śniadanie"
        total == 2 -> "Kolacja"
        total == 3 && slot == 1 -> "Śniadanie"
        total == 3 && slot == 2 -> "Obiad"
        total == 3 -> "Kolacja"
        total == 4 && slot == 1 -> "Śniadanie"
        total == 4 && slot == 2 -> "Drugie śniadanie"
        total == 4 && slot == 3 -> "Obiad"
        total == 4 -> "Kolacja"
        total == 5 && slot == 1 -> "Śniadanie"
        total == 5 && slot == 2 -> "Drugie śniadanie"
        total == 5 && slot == 3 -> "Obiad"
        total == 5 && slot == 4 -> "Podwieczorek"
        total == 5 -> "Kolacja"
        else -> "Posiłek $slot"
    }
}
