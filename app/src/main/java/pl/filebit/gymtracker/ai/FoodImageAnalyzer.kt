package pl.filebit.gymtracker.ai

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FoodAnalysisIngredient(
    val productName: String,
    val grams: Int,
    val confidence: String   // "LOW" / "MEDIUM" / "HIGH"
)

@Serializable
data class FoodAnalysis(
    val dishName: String,
    val ingredients: List<FoodAnalysisIngredient>,
    val totalKcal: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int,
    val overallConfidence: String,   // LOW/MEDIUM/HIGH
    val notes: String = ""           // np. "Sos niewidoczny — możliwe niedoszacowanie"
)

/**
 * Analizuje zdjęcie posiłku przez multimodal LLM.
 *
 * Filozofia:
 *  - AI sam ocenia confidence (LOW gdy niewidoczna część posiłku, MEDIUM standard,
 *    HIGH gdy proste danie z widocznymi porcjami)
 *  - Lokalna baza nie weryfikuje (AI nie ma listy produktów PL)
 *  - User zawsze może edytować przed zapisem do MealEntry
 */
@Singleton
class FoodImageAnalyzer @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun analyze(imageBytes: ByteArray, mimeType: String = "image/jpeg"): Result<FoodAnalysis> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("Skonfiguruj klucz AI w ustawieniach (Profil → Połączenie AI)"))
        }

        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val prompt = buildString {
            append("Jesteś dietetykiem-ekspertem. Analizujesz zdjęcie posiłku.\n\n")
            append("Zadanie:\n")
            append("1. Zidentyfikuj danie (np. 'Spaghetti bolognese', 'Sałatka cezar')\n")
            append("2. Wymień widoczne składniki + szacowaną gramaturę (na podstawie wielkości porcji, talerza, zwykłych standardów PL)\n")
            append("3. Oblicz sumaryczne kcal i makro\n")
            append("4. Oceń confidence — LOW/MEDIUM/HIGH:\n")
            append("   - HIGH = proste danie, widoczne porcje, dobrze rozpoznane składniki\n")
            append("   - MEDIUM = częściowo widoczne, sos pokrywa część\n")
            append("   - LOW = mocno niewyraźne, ukryte składniki, trudne do oceny\n")
            append("5. Notatki o niepewnościach (np. 'sos sojowy niewidoczny — możliwe +200 kcal')\n\n")

            append("WAŻNE:\n")
            append("- Bądź realistą — porcje typowe dla PL (talerz obiadowy ~250-400g, talerz głęboki sałatki ~200g)\n")
            append("- Confidence HIGH tylko gdy faktycznie jesteś pewien\n")
            append("- Jeśli nie widzisz CO TO JEST — confidence LOW + ogólna nazwa\n\n")

            append("Zwróć TYLKO JSON (bez markdown):\n")
            append("""
            {
              "dishName": "Spaghetti bolognese",
              "ingredients": [
                {"productName": "Makaron spaghetti", "grams": 150, "confidence": "HIGH"},
                {"productName": "Sos boloński (mięso mielone + pomidory)", "grams": 200, "confidence": "MEDIUM"},
                {"productName": "Parmezan", "grams": 15, "confidence": "MEDIUM"}
              ],
              "totalKcal": 720,
              "totalProteinG": 32,
              "totalCarbsG": 78,
              "totalFatG": 28,
              "overallConfidence": "MEDIUM",
              "notes": "Ilość parmezanu trudna do oceny przez nakładkę."
            }
            """.trimIndent())
        }

        val result = client.chatWithImage(cfg, base64, mimeType, prompt, source = "FoodImageAnalyzer")
        return result.mapCatching { raw ->
            val cleaned = stripJsonFences(raw)
            json.decodeFromString<FoodAnalysis>(cleaned)
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
