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
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
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
    private val dietProfileRepo: UserDietProfileRepository,
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
        val dietProfile = dietProfileRepo.get()
        // Uwzględnij ewentualny override usera (manualKcal lub customDeficit) + UserDietProfile
        val goal = computeDailyGoal(
            profile,
            manualKcalOverride = config.manualKcal,
            customDeficit = config.customDeficit,
            dietProfile = dietProfile
        )
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

            append("\n\n=== ZASADY DIETETYKA SPORTOWEGO ===\n")
            append("(Bazuj na: Israetel/RP Strength, Schoenfeld, Helms — dietetyka kulturystyczna+sportowa)\n\n")

            append("1. **CHARAKTER I CIĘŻAR POSIŁKU PER PORA DNIA:**\n\n")

            append("   **ŚNIADANIE** (rano, po 8-12h postu nocnego):\n")
            append("   - Cel: odbudowa glikogenu po nocy + start białkowy\n")
            append("   - Skład: węgle złożone (płatki owsiane / pieczywo razowe / kasze) + białko (twaróg/jaja/skyr) + mało tłuszczu (lepsze trawienie)\n")
            append("   - prepMinutes ≤ 8\n")
            append("   - DOZWOLONE: owsianka, jajecznica, twaróg, pieczywo razowe, jogurt, owoce\n")
            append("   - UNIKAJ: ciężkie mięsa (kotlet schabowy), ziemniaki, smażone\n\n")

            append("   **DRUGIE ŚNIADANIE / PRZEKĄSKA:**\n")
            append("   - Szybka, ≤5 min — twaróg/jogurt + orzechy/owoc, kanapka razowa, koktajl białkowy\n")
            append("   - Mała ilość kcal (~15% dziennych)\n\n")

            append("   **OBIAD** (środek dnia, główny posiłek):\n")
            append("   - Cel: główne ŹRÓDŁO kcal dnia (~35-40%), balans pełen B/W/T\n")
            append("   - Skład: białko 150-300g (mięso/ryba/strączki) + węgle (ryż/kasza/ziemniaki) + WARZYWA + zdrowy tłuszcz (oliwa/awokado)\n")
            append("   - prepMinutes ≤ 15\n")
            append("   - DOZWOLONE: kurczak/wołowina/ryba + ryż/kasza/ziemniaki + warzywa\n\n")

            append("   **PODWIECZOREK** (jeśli 5+ posiłków):\n")
            append("   - Pre-WO (1-2h przed treningiem): banan + jogurt, kanapka z indykiem, węgle szybkie + białko\n")
            append("   - Post-WO (do 1h po treningu): koktajl białkowy + owoc, twaróg + ryż, BIAŁKO + szybkie węgle\n\n")

            append("   **KOLACJA** ⚠️ KRYTYCZNE ZASADY (przed snem):\n")
            append("   - Sen za 2-3h, więc posiłek MUSI być LEKKI ŻOŁĄDKOWO\n")
            append("   - **MAX 30g węgli netto** (ziemniaki=20g/100g → max 150g; ryż=28g/100g → max 100g)\n")
            append("   - **PRIORYTET: białko WOLNOWCHŁANIALNE** — twaróg (kazeina!), jaja gotowane, ryba, indyk\n")
            append("   - Zdrowy tłuszcz w UMIARKOWANEJ ilości (orzechy 20-30g, pół awokado, łyżka oliwy)\n")
            append("   - DUŻO warzyw (sycące przy małej ilości kcal)\n")
            append("   - **ZAKAZANE NA KOLACJĘ:** ciężkie ziemniaki >150g, makarony białe, biały ryż >100g, pieczywo białe, smażone mięsa, fast food\n")
            append("   - **PREFEROWANE:** twaróg z warzywami i orzechami; jaja sadzone z sałatką; pieczona ryba (NIE zarówno ryż+ziemniaki!) z brokułami; sałatka z indykiem i awokado\n\n")

            append("2. **MAKRO PER POSIŁEK — cele liczbowe:**\n")
            append("   - Białko: ~${perMealProtein}g (±20%) STAŁE w każdym posiłku — chroni masę mięśniową\n")
            append("   - Węgle: śniadanie ${(perMealCarbs*1.2).toInt()}g, obiad ${(perMealCarbs*1.5).toInt()}g, KOLACJA ≤30g (low-carb wieczorem!)\n")
            append("   - Tłuszcze: rozłożone w obiad/śniadanie, kolacja max ${(perMealFat*0.5).toInt()}g\n")
            append("   - W dni TRENINGOWE (dziś: ${if (isTrainingDay) "TAK" else "NIE"}): więcej węgli przed/po treningu, kolacja taka sama\n\n")

            append("3. **REGENERACJA PODCZAS SNU:**\n")
            append("   - Twaróg/serek wiejski wieczorem = kazeina = stałe uwalnianie aminokwasów przez 6-8h snu\n")
            append("   - To IDEALNE dla budowy masy mięśniowej — używaj często w kolacji\n\n")

            append("4. **CEL DZIENNY:** suma kcal posiłków = ${goal.kcal} ±10%, suma makro ±10%\n\n")

            append("5. **WYMAGANIA TECHNICZNE:**\n")
            append("   - Max 6 składników na przepis (preferuj 4-5)\n")
            append("   - prepMinutes ≤ 15 (śniadanie ≤ 8, kolacja ≤ 10)\n")
            append("   - Polskie codzienne dania (NIC egzotycznego)\n")
            append("   - productName **DOKŁADNIE** z listy (literówki = błąd)\n")
            append("   - Każdy krok instrukcji w nowej linii (numerowany 1. 2. 3.)\n\n")

            append("6. **AUTOWERYFIKACJA — przed wysłaniem JSON sprawdź:**\n")
            append("   - Czy KOLACJA ma <30g węgli? (jeśli >30g — ZMIEŃ)\n")
            append("   - Czy każdy posiłek ma białko? (każdy slot >${(perMealProtein*0.7).toInt()}g)\n")
            append("   - Czy suma kcal = ${goal.kcal} ±10%? (czyli ${(goal.kcal*0.9).toInt()}-${(goal.kcal*1.1).toInt()})\n")
            append("   - Czy posiłki nie powtarzają się? (różnorodność)\n")
            append("   - Czy każdy productName istnieje w liście?\n")

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
