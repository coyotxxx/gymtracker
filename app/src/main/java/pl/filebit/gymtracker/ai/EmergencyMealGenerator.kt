package pl.filebit.gymtracker.ai

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class EmergencyMode {
    QUICK_5MIN,    // produkty gotowe do zjedzenia, max 5 min
    NO_COOKING,    // bez kuchni — sałatka, kanapka, twaróg
    AT_WORK,       // przenośny posiłek do pojemnika (z mikrofalówką lub bez)
    STORE_SHOP,    // sklep typu Biedronka/Lidl (typowe produkty)
    LATE_NIGHT     // późna kolacja: max 30g węgli, lekkie
}

/**
 * Lekki wariant DietAiService dla pojedynczego posiłku awaryjnego.
 * Zawężony prompt, krótszy output (1 recipe), szybsza odpowiedź.
 */
@Singleton
class EmergencyMealGenerator @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val profileRepo: UserProfileRepository,
    private val dietProfileRepo: UserDietProfileRepository,
    private val dietRepo: DietRepository,
    private val masterContextBuilder: MasterAiContextBuilder
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generate(
        mode: EmergencyMode,
        targetKcal: Int,
        mealType: MealType
    ): Result<AiMealRecipe> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("Skonfiguruj klucz AI w ustawieniach (Profil → Połączenie AI)"))
        }
        val profile = profileRepo.get()
        val dietProfile = dietProfileRepo.get()
        val products = dietRepo.observeAllProducts().first()

        val productsListing = products.joinToString("\n") { p ->
            "- ${p.name} (${p.kcalPer100g.toInt()} kcal/100g, B${p.proteinPer100g.toInt()}/W${p.carbsPer100g.toInt()}/T${p.fatPer100g.toInt()})"
        }

        val modeRules = when (mode) {
            EmergencyMode.QUICK_5MIN -> """
                TRYB: SZYBKI 5 MIN
                - max 5 minut przygotowania (BEZ gotowania, max otwarcie/wymieszanie)
                - tylko produkty natychmiastowe: jogurt/skyr/twaróg/banan/wafle ryżowe/orzechy/serek wiejski/jajko na twardo (gotowe)
                - max 4 składniki
            """.trimIndent()
            EmergencyMode.NO_COOKING -> """
                TRYB: BEZ GOTOWANIA
                - ZERO grzania/gotowania, tylko mieszanie/krojenie
                - sałatki, kanapki, owsianka na zimno, twaróg z dodatkami
                - max 6 składników, max 10 min
            """.trimIndent()
            EmergencyMode.AT_WORK -> """
                TRYB: W PRACY/SZKOLE
                - MUSI być przenośny w pojemniku
                - jeśli dietProfile.hasMicrowaveAtWork=${dietProfile?.hasMicrowaveAtWork ?: false}
                  - true → ciepły obiad z ryżem/kaszą + mięso + warzywa
                  - false → sałatka, kanapka, wraps, NA ZIMNO
                - max 6 składników
            """.trimIndent()
            EmergencyMode.STORE_SHOP -> """
                TRYB: ZE SKLEPU (Biedronka/Lidl)
                - tylko produkty kupione tego dnia, BEZ gotowania
                - jogurt naturalny + płatki owsiane + banan, twaróg + orzechy
                - sushi/wrapy gotowe (jeśli w bazie), zimna kanapka
                - max 4 składniki, max 5 min
            """.trimIndent()
            EmergencyMode.LATE_NIGHT -> """
                TRYB: PÓŹNA KOLACJA (sen <2h)
                - MAX 30g węgli netto (kazeina + białko)
                - PRIORYTET: twaróg/skyr/jaja/ryba (białko WOLNOWCHŁANIALNE)
                - ZERO ciężkich węgli (BEZ ziemniaków >100g, BEZ ryżu >70g)
                - dużo warzyw (sycące, niskokaloryczne)
                - max 5 składników, max 10 min
            """.trimIndent()
        }

        val mealLabel = when (mealType) {
            MealType.BREAKFAST -> "śniadanie"
            MealType.LUNCH -> "obiad"
            MealType.DINNER -> "kolacja"
            MealType.SNACK -> "przekąska"
        }

        // === MASTER CONTEXT (krótka sekcja — nie potrzebujemy pełnej historii) ===
        val masterCtx = runCatching { masterContextBuilder.build() }.getOrNull()

        val prompt = buildString {
            append("Wygeneruj JEDEN szybki posiłek po polsku — POSIŁEK AWARYJNY.\n\n")

            if (masterCtx != null) {
                append(MasterAiContextPromptHelper.toBaseProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toDietProfileSection(masterCtx))
                append(MasterAiContextPromptHelper.toCurrentStateSection(masterCtx))
                append("\n")
            }

            append("=== CEL ===\n")
            append("- Posiłek: $mealLabel\n")
            append("- Kcal docelowo: ~$targetKcal kcal (±15%)\n")

            append("\n=== ZASADY TRYBU ===\n")
            append(modeRules)

            // HARD constraints krótkie
            dietProfile?.let { dp ->
                if (dp.parsedAllergies().isNotEmpty()) {
                    append("\n\n⚠ ALERGIE BEZWZGLĘDNIE UNIKAJ: ${dp.parsedAllergies().joinToString(", ")}\n")
                }
                if (dp.dietPreference != pl.filebit.gymtracker.data.entity.DietPreference.STANDARD) {
                    append("⚠ Preferencja: ${dp.dietPreference.name} (musi być przestrzegana)\n")
                }
            }

            append("\n=== DOSTĘPNE PRODUKTY (DOKŁADNE NAZWY) ===\n")
            append(productsListing)

            append("\n\n=== OUTPUT ===\n")
            append("TYLKO JSON, jeden obiekt:\n")
            append("""
            {
              "name": "Twaróg z bananem i orzechami",
              "ingredients": [
                {"productName": "Twaróg chudy", "grams": 200},
                {"productName": "Banan", "grams": 100},
                {"productName": "Orzechy włoskie", "grams": 20}
              ],
              "instructions": "1. Wymieszaj twaróg z bananem.\n2. Posyp orzechami.",
              "prepMinutes": 3,
              "kcal": 380,
              "proteinG": 28,
              "carbsG": 35,
              "fatG": 14
            }
            """.trimIndent())
        }

        val result = client.chat(cfg, listOf(AiMessage(AiRole.USER, prompt)), source = "EmergencyMeal")
        return result.mapCatching { raw ->
            val cleaned = stripJsonFences(raw)
            json.decodeFromString<AiMealRecipe>(cleaned)
        }
    }

    private fun stripJsonFences(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.substringAfter("\n").substringBeforeLast("```").trim()
        }
        val first = t.indexOf('{')
        val last = t.lastIndexOf('}')
        if (first >= 0 && last > first) {
            t = t.substring(first, last + 1)
        }
        return t
    }
}
