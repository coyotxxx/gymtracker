package pl.filebit.gymtracker.ai

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.repository.DietConfig
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import pl.filebit.gymtracker.util.computeDailyGoal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val dietRepo: DietRepository,
    private val workoutRepo: WorkoutRepository,
    private val planRepo: PlanRepository,
    private val statsRepo: StatsRepository,
    private val bodyMeasurementDao: BodyMeasurementDao
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

        // === KONTEKST Z CAŁEJ APLIKACJI ===
        // Najświeższa waga (BodyMeasurement bardziej aktualna niż profile.bodyweightKg)
        val latestMeasurement = bodyMeasurementDao.getLatest()
        val currentWeight = latestMeasurement?.weightKg ?: profile.bodyweightKg

        // Ostatnie 7 dni treningów — czy aktywny tydzień, intensywność (RPE)
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val recentWorkouts = workoutRepo.observeRecent(20).first()
            .filter { it.finishedAt != null && it.startedAt >= sevenDaysAgo }
        val recentVolume = recentWorkouts.sumOf { w ->
            workoutRepo.getSetsForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .sumOf { it.reps * it.weightKg }
        }
        val recentRpeAvg = recentWorkouts.flatMap { w ->
            workoutRepo.getSetsForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .mapNotNull { it.rpe }
        }.takeIf { it.isNotEmpty() }?.average()

        // Dzień tygodnia + czy planowany trening dziś
        val isoToday = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        val plansToday = runCatching { planRepo.getPlansForDay(isoToday) }.getOrNull().orEmpty()
        val isTrainingDay = plansToday.isNotEmpty() || recentWorkouts.any { w ->
            // Trening dziś już rozpoczęty/skończony?
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            w.startedAt >= cal.timeInMillis
        }

        val mealHours = config.mealHoursDecimal()
        val mealsCount = config.mealsPerDay
        val perMealKcal = if (mealsCount > 0) goal.kcal / mealsCount else 0

        // Rozkład makro per posiłek (śniadanie/obiad/kolacja proporcjonalnie)
        val perMealProtein = if (mealsCount > 0) goal.proteinG / mealsCount else 0
        val perMealCarbs = if (mealsCount > 0) goal.carbsG / mealsCount else 0
        val perMealFat = if (mealsCount > 0) goal.fatG / mealsCount else 0

        val productsListing = products.joinToString("\n") { p ->
            "- ${p.name} (${p.kcalPer100g.toInt()} kcal/100g, B${p.proteinPer100g.toInt()}/W${p.carbsPer100g.toInt()}/T${p.fatPer100g.toInt()})"
        }

        val slotLabels = mealHours.indices.map { idx ->
            val time = config.formatTime(mealHours[idx])
            val label = labelForSlot(idx + 1, mealsCount)
            "$label (godz. $time, ~$perMealKcal kcal, B${perMealProtein}g W${perMealCarbs}g T${perMealFat}g)"
        }

        val goalLabel = when (profile.weightGoalType) {
            pl.filebit.gymtracker.data.entity.WeightGoalType.CUT -> "redukcja (deficyt kaloryczny)"
            pl.filebit.gymtracker.data.entity.WeightGoalType.BULK -> "masa (nadwyżka kaloryczna)"
            pl.filebit.gymtracker.data.entity.WeightGoalType.MAINTAIN -> "utrzymanie wagi"
            pl.filebit.gymtracker.data.entity.WeightGoalType.NONE -> "brak deklaracji (utrzymanie)"
        }

        val today = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl", "PL")).format(Date())

        val prompt = buildString {
            append("Jesteś personalnym dietetykiem-trenerem (jak Israetel/RP Strength). ")
            append("Twoja wiedza: dietetyka sportowa, IF 16/8, wymienniki kaloryczne, makroskładniki. ")
            append("Wygeneruj plan dnia po polsku dla tej osoby:\n\n")

            append("DZIŚ: $today\n\n")

            append("=== PROFIL ===\n")
            append("- Imię: ${profile.displayName.ifBlank { "—" }}\n")
            append("- Płeć: ${if (profile.gender == pl.filebit.gymtracker.data.entity.Gender.MALE) "mężczyzna" else "kobieta"}\n")
            currentWeight?.let { append("- Aktualna waga: $it kg\n") }
            profile.targetWeightKg?.let { append("- Waga docelowa: $it kg\n") }
            append("- Cel: $goalLabel\n")
            append("- Cel treningowy: ${profile.goal.name} (${profile.experience.name})\n")
            append("- Treningów/tydzień (deklarowane): ${profile.daysPerWeek}\n")
            if (profile.injuriesNotes.isNotBlank()) {
                append("- Notatki/kontuzje/preferencje: ${profile.injuriesNotes}\n")
            }

            // Pomiary obwodów (jeśli świeże)
            latestMeasurement?.let { m ->
                if (m.waistCm != null || m.chestCm != null || m.bodyFatPercent != null) {
                    append("- Ostatnie pomiary: ")
                    val parts = listOfNotNull(
                        m.waistCm?.let { "talia $it cm" },
                        m.chestCm?.let { "klatka $it cm" },
                        m.bodyFatPercent?.let { "BF% $it" }
                    )
                    append(parts.joinToString(", "))
                    append("\n")
                }
            }

            append("\n=== AKTYWNOŚĆ (ostatnie 7 dni) ===\n")
            append("- Treningów: ${recentWorkouts.size}\n")
            if (recentVolume > 0) append("- Łączna objętość: ${recentVolume.toInt()} kg\n")
            recentRpeAvg?.let { append("- Średnie RPE: %.1f\n".format(it)) }
            append("- Dziś jest dzień ${if (isTrainingDay) "TRENINGOWY (więcej węgli pre/post-workout)" else "REGENERACJI (mniej węgli, więcej tłuszczu i białka)"}\n")

            append("\n=== CEL DZIENNY ===\n")
            append("- ${goal.kcal} kcal\n")
            append("- Białko: ${goal.proteinG} g (priorytet — chroni masę mięśniową)\n")
            append("- Węglowodany: ${goal.carbsG} g\n")
            append("- Tłuszcz: ${goal.fatG} g (zdrowe — oliwa/orzechy/awokado/mleko kokosowe)\n")

            append("\n=== KONFIGURACJA DNIA ===\n")
            append("- Liczba posiłków: $mealsCount\n")
            append("- Okno żywieniowe: ${config.eatingWindowHours}h (${"%02d:00".format(config.windowStartHour)}–${"%02d:00".format(config.windowEndHour())})\n")
            append("- Cel kcal/posiłek: ~$perMealKcal kcal\n")
            append("- Cel makro/posiłek: B${perMealProtein}g W${perMealCarbs}g T${perMealFat}g\n")

            append("\n=== SLOTY (z godzinami i kaloriami) ===\n")
            slotLabels.forEach { append("- $it\n") }

            append("\n=== DOSTĘPNE PRODUKTY (używaj WYŁĄCZNIE z tej listy, nazwy DOKŁADNIE) ===\n")
            append(productsListing)

            append("\n\n=== ZASADY UKŁADANIA ===\n")
            append("1. **Charakter posiłku zależy od pory:**\n")
            append("   - ŚNIADANIE (rano): lekkie, węgle złożone (płatki/owsianka/pieczywo) + białko + odrobina tłuszczu, ≤8 min\n")
            append("   - DRUGIE ŚNIADANIE: szybkie, np. jogurt z owocami / kanapka, ≤5 min\n")
            append("   - OBIAD: pełen posiłek, balansowane B/W/T, główne źródło dziennych kcal, ≤15 min\n")
            append("   - PODWIECZOREK: białko + węgle (po treningu) lub białko + tłuszcz (przed snem)\n")
            append("   - KOLACJA: lżejsza, więcej białka, mniej węgli (utrzymanie sytości na noc)\n")

            append("\n2. **Makro per posiłek bliskie celom** (±20%):\n")
            append("   - Białko stałe w każdym posiłku (~${perMealProtein}g)\n")
            append("   - Węgle: w dni treningowe więcej w obiad/posiłek po treningu, w dni rest rozłożone równo\n")
            append("   - Tłuszcze: w 1-2 posiłkach (śniadanie/obiad), kolacja zwykle low-fat\n")

            append("\n3. **Wymagania techniczne:**\n")
            append("   - Każdy przepis: max 6 składników\n")
            append("   - prepMinutes ≤ 15 (śniadanie ≤ 8 min!)\n")
            append("   - Tylko polskie codzienne dania (nic egzotycznego)\n")
            append("   - productName MUSI być dokładnie z listy (literówki = błąd parsowania)\n")
            append("   - Każdy krok instrukcji w nowej linii\n")

            append("\n4. **Sprawdź sumę:** wszystkie posiłki razem = cel dzienny ±10%\n")

            append("\n=== OUTPUT ===\n")
            append("Zwróć TYLKO JSON (bez markdown, bez tekstu poza JSON). Format:\n")
            append("""
            {
              "meals": [
                {
                  "name": "Owsianka z twarogiem i jagodami",
                  "ingredients": [
                    {"productName": "Płatki owsiane", "grams": 60},
                    {"productName": "Twaróg chudy", "grams": 150},
                    {"productName": "Mleko 0,5%", "grams": 200}
                  ],
                  "instructions": "1. Zalej płatki gorącym mlekiem.\n2. Wymieszaj z twarogiem.\n3. Odstaw na 5 min.",
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
