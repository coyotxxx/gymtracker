package pl.filebit.gymtracker.ai

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wynik analizy zrzutu ekranu z aplikacji zdrowia (Huawei Health, Mi Fit, Garmin Connect, Samsung Health, etc.).
 *
 * Wszystkie pola opcjonalne — różne aplikacje raportują różne metryki. AI wypełnia tylko to co WIDZI na zrzucie.
 */
@Serializable
data class HealthScreenshotData(
    /** Sen z ostatniej nocy w godzinach (np. 4.32 dla "4 godz. 19 min"). */
    val sleepHours: Double? = null,
    /** Waga w kg (jeśli widoczna na zrzucie). */
    val weightKg: Double? = null,
    /** % tkanki tłuszczowej (np. '24,9%' z wagi analitycznej → 24.9). */
    val bodyFatPercent: Double? = null,
    /** Masa mięśniowa / masa mięśni szkieletowych w kg (np. '34,2 kg' → 34.2). */
    val muscleMassKg: Double? = null,
    /** Liczba kroków dziennie. */
    val steps: Int? = null,
    /** Tętno spoczynkowe (bpm) — np. "Tętno 64 ud./min" w Huawei Health. */
    val restingHeartRateBpm: Int? = null,
    /** SpO2 % (saturacja). */
    val spO2Pct: Int? = null,
    /** Stres 0-100 (Huawei) lub 0-5 (różne aplikacje) — AI normalizuje do 1-5 dla RecoveryLog. */
    val stressLevel1to5: Int? = null,
    /** HRV w ms jeśli aplikacja pokazuje. */
    val hrvMs: Double? = null,
    /** VO2Max ml/kg/min. */
    val vo2max: Double? = null,
    /** Spalone kalorie aktywne. */
    val activeCalories: Int? = null,
    /** Confidence ogólne — LOW/MEDIUM/HIGH. */
    val overallConfidence: String = "MEDIUM",
    /** Wszelkie notatki AI o niepewnościach lub dodatkowe dane spoza schematu. */
    val notes: String = "",
    /** Wykryta data zrzutu — null = dziś. Format ISO yyyy-MM-dd. */
    val detectedDate: String? = null
)

/**
 * Multimodal AI parsuje zrzuty ekranu z aplikacji zdrowotnych i wyciąga metryki regeneracji.
 *
 * **Po co?** Health Connect nie syncuje się z Huawei Health, Garmin (częściowo), Polar, Mi Fit
 * — zamknięte ekosystemy. User wysyła zrzut, AI wyciąga liczby, my zapisujemy do RecoveryLog
 * i BodyMeasurement. Działa z DOWOLNĄ aplikacją mającą czytelny ekran z liczbami.
 *
 * **Filozofia:** AI ekstraktuje tylko co WIDZI. Brak halucynacji liczb — null jest OK.
 * User potwierdza przed zapisem (UI pokaże preview).
 */
@Singleton
class HealthScreenshotAnalyzer @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun analyze(imageBytes: ByteArray, mimeType: String = "image/jpeg"): Result<HealthScreenshotData> {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            return Result.failure(IllegalStateException("Skonfiguruj klucz AI w ustawieniach (Profil → Połączenie AI)"))
        }

        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val prompt = buildString {
            append("Jesteś asystentem analizującym zrzuty ekranu z aplikacji zdrowotnych. ")
            append("Może to być: Huawei Health (Zdrowie Huawei), Mi Fit, Garmin Connect, Samsung Health, Polar Flow, Fitbit, Google Fit, Whoop, Oura.\n\n")

            append("ZADANIE:\n")
            append("Wyciągnij z obrazka KAŻDĄ metrykę którą widzisz. Jeśli czegoś nie widać — zostaw null. ")
            append("NIE HALUCYNUJ liczb — lepiej null niż zgadnięcie.\n\n")

            append("METRYKI:\n")
            append("• sleepHours — sen w godzinach (zamień '4 godz. 19 min' → 4.32, '7h 45min' → 7.75)\n")
            append("• weightKg — waga w kg (np. '77,2 kg' → 77.2)\n")
            append("• bodyFatPercent — % tkanki tłuszczowej z wagi analitycznej ('Procentowa zawartość tkanki tłuszczowej 24,9%' → 24.9). NIE myl z trzewną tkanką (poziom 1-59) ani wodą.\n")
            append("• muscleMassKg — masa mięśniowa / mięśni szkieletowych w kg ('Masa mięśni szkieletowych 34,2 kg' → 34.2). NIE myl z masą beztłuszczową.\n")
            append("• steps — liczba kroków dziś (np. '857 kroków' → 857)\n")
            append("• restingHeartRateBpm — tętno spoczynkowe ('64 ud./min', '64 bpm' → 64)\n")
            append("• spO2Pct — saturacja '97%' → 97\n")
            append("• stressLevel1to5 — stres znormalizowany do 1-5:\n")
            append("    - Huawei pokazuje 0-99, gdzie 0-29=Spokojny=1, 30-59=Normalny=2, 60-79=Średni=3, 80-99=Wysoki=5\n")
            append("    - Garmin pokazuje 0-100 podobnie\n")
            append("• hrvMs — HRV w ms (jeśli widoczne, np. 'HRV 45 ms' → 45.0)\n")
            append("• vo2max — VO2Max ml/kg/min (np. '35 ml/kg/min' → 35.0)\n")
            append("• activeCalories — kalorie aktywne ('35 kcal' → 35)\n")
            append("• detectedDate — data widoczna na ekranie (yyyy-MM-dd) lub null jeśli nie widzisz\n\n")

            append("CONFIDENCE:\n")
            append("• HIGH = liczby są wyraźne, jednostki jasne (kg/h/bpm/%), label po polsku/angielsku\n")
            append("• MEDIUM = większość metryk czytelna, ale parę niepewnych\n")
            append("• LOW = ekran nieostry, większość niewidoczna lub w nieznanym układzie\n\n")

            append("Zwróć TYLKO JSON (bez markdown, bez tekstu wokół):\n")
            append("""
            {
              "sleepHours": 4.32,
              "weightKg": 77.2,
              "bodyFatPercent": 24.9,
              "muscleMassKg": 34.2,
              "steps": 857,
              "restingHeartRateBpm": 64,
              "spO2Pct": 97,
              "stressLevel1to5": 2,
              "hrvMs": null,
              "vo2max": 35.0,
              "activeCalories": 35,
              "overallConfidence": "HIGH",
              "notes": "Wszystkie kluczowe metryki czytelne. Brak HRV w widocznym widoku.",
              "detectedDate": null
            }
            """.trimIndent())
            append("\n\nUWAGA: jeśli zrzut zawiera tylko np. 1-2 metryki — wypełnij tylko te, reszta null. To NORMALNE.")
        }

        val result = client.chatWithImage(cfg, base64, mimeType, prompt, source = "HealthScreenshotAnalyzer")
        return result.mapCatching { raw ->
            val cleaned = stripJsonFences(raw)
            json.decodeFromString<HealthScreenshotData>(cleaned)
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
