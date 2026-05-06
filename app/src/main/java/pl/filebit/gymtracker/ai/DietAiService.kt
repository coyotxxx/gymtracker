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
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
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
    val fatG: Int,
    /** 0..2 alternatywne dania na ten sam slot (taka sama suma kcal/makro ±10%). */
    val alternatives: List<AiAlternative> = emptyList()
)

/**
 * Lekka alternatywa — bez instrukcji, tylko nazwa + składniki + makro.
 * Gdy user wybierze, traktujemy ją jak pełną AiMealRecipe.
 */
@Serializable
data class AiAlternative(
    val name: String,
    val ingredients: List<AiRecipeIngredient>,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val prepMinutes: Int = 15,
    val instructions: String = ""
) {
    fun toRecipe(): AiMealRecipe = AiMealRecipe(
        name = name,
        ingredients = ingredients,
        instructions = instructions.ifBlank { "Przygotuj jak standardowy posiłek z tych składników." },
        prepMinutes = prepMinutes,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG
    )
}

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
    val mealsForSlots: List<Pair<MealType, AiMealRecipe>>,
    /** SOFT warnings z walidatora — pokaż userowi (nie blokujące). */
    val warnings: List<ValidationIssue> = emptyList(),
    /** Suma kcal/makro POLICZONA Z LOKALNEJ BAZY (nie z deklaracji AI). */
    val correctedTotal: CorrectedMacros? = null
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
    private val trainingDietBridge: TrainingDietBridge,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val mealFeedbackRepo: pl.filebit.gymtracker.data.repository.MealFeedbackRepository,
    private val constraintResolver: pl.filebit.gymtracker.data.repository.ConstraintResolver,
    private val validator: AiMealJsonValidator,
    private val workoutTimeAnalyzer: pl.filebit.gymtracker.data.repository.WorkoutTimeAnalyzer,
    private val knowledgeRepo: pl.filebit.gymtracker.data.repository.DietaryKnowledgeRepository
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generateDayPlan(
        config: DietConfig,
        stylePrefs: MealStylePreferences = MealStylePreferences()
    ): Result<GeneratedDayPlan> {
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

        // Mostek do modułu trening — TrainingDaySummary dla dziś
        runCatching { trainingDietBridge.ensureForToday() }
        val todaySummary = runCatching { trainingDietBridge.getForDate(System.currentTimeMillis()) }
            .getOrNull()
        val isTrainingDay = todaySummary?.isTrainingDay == true
        val intensity = todaySummary?.intensityScore
        val trainingType = todaySummary?.trainingType
        val muscleGroupsToday = todaySummary?.parsedMuscleGroups().orEmpty()

        val mealHours = config.mealHoursDecimal()
        val mealsCount = config.mealsPerDay
        val perMealKcal = if (mealsCount > 0) goal.kcal / mealsCount else 0

        // Rozkład makro per posiłek (śniadanie/obiad/kolacja proporcjonalnie)
        val perMealProtein = if (mealsCount > 0) goal.proteinG / mealsCount else 0
        val perMealCarbs = if (mealsCount > 0) goal.carbsG / mealsCount else 0
        val perMealFat = if (mealsCount > 0) goal.fatG / mealsCount else 0

        val productsListing = products.joinToString("\n") { p ->
            val star = if (p.isFavorite) " ⭐" else ""
            "- ${p.name}$star (${p.kcalPer100g.toInt()} kcal/100g, B${p.proteinPer100g.toInt()}/W${p.carbsPer100g.toInt()}/T${p.fatPer100g.toInt()})"
        }

        // Ulubione produkty oznaczone przez usera (heart icon w AddMealDialog)
        val favoriteProducts = runCatching { dietRepo.getFavoriteProducts() }.getOrNull().orEmpty()

        // Preferencje usera (rating ≥4 = preferuj, ≤2 = unikaj)
        val favorites = runCatching { mealFeedbackRepo.getTopFavorites(limit = 10) }.getOrNull().orEmpty()
        val disliked = runCatching { mealFeedbackRepo.getTopDisliked(limit = 5) }.getOrNull().orEmpty()

        // Wykryj typową godzinę treningu (z UserDietProfile lub z historii treningów)
        val typesForSlotsForCtx = mealTypesForSlots(mealsCount)
        val trainingHourCandidate = dietProfile?.usualTrainingHour
            ?: run {
                val recentStarts = workoutRepo.observeRecent(50).first()
                    .filter { it.finishedAt != null }
                    .map { it.startedAt }
                workoutTimeAnalyzer.detectUsualTrainingHour(recentStarts)
            }
        val slotContexts = workoutTimeAnalyzer.classifySlots(
            mealHoursDecimal = mealHours,
            mealTypesForSlots = typesForSlotsForCtx,
            trainingHour = if (isTrainingDay) trainingHourCandidate else null
        )

        // Rozkład kcal per slot — różny dla każdej pory dnia, bo śniadanie/obiad/kolacja mają różny ciężar
        // Standard dietetyczny: śniadanie 25-30%, obiad 35-40%, kolacja 25-30% (bez przekąsek)
        // Z przekąskami: śniadanie 25%, II śniadanie 10%, obiad 35%, podwieczorek 10%, kolacja 20%
        val perSlotKcalTargets = computePerSlotKcalDistribution(goal.kcal, mealsCount)
        val perSlotProteinTargets = computePerSlotProteinDistribution(goal.proteinG, mealsCount)
        val perSlotCarbsTargets = computePerSlotCarbsDistribution(goal.carbsG, mealsCount)
        val perSlotFatTargets = computePerSlotFatDistribution(goal.fatG, mealsCount)

        val slotLabels = mealHours.indices.map { idx ->
            val time = config.formatTime(mealHours[idx])
            val label = labelForSlot(idx + 1, mealsCount)
            val ctx = slotContexts.getOrNull(idx)?.workoutContext
            val ctxTag = when (ctx) {
                pl.filebit.gymtracker.data.entity.WorkoutContext.PRE_WORKOUT -> " 🏋 PRE-WORKOUT"
                pl.filebit.gymtracker.data.entity.WorkoutContext.POST_WORKOUT -> " 🏋 POST-WORKOUT"
                else -> ""
            }
            val kcalSlot = perSlotKcalTargets.getOrElse(idx) { perMealKcal }
            val pSlot = perSlotProteinTargets.getOrElse(idx) { perMealProtein }
            val cSlot = perSlotCarbsTargets.getOrElse(idx) { perMealCarbs }
            val fSlot = perSlotFatTargets.getOrElse(idx) { perMealFat }
            "$label (godz. $time, **$kcalSlot kcal ±15%**, B${pSlot}g W${cSlot}g T${fSlot}g)$ctxTag"
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

            // === PROFIL DIETETYCZNY (z DietOnboarding) ===
            dietProfile?.let { dp ->
                append("- Wiek: ${dp.ageYears} lat\n")
                append("- Wzrost: ${dp.heightCm} cm\n")
                append("- Aktywność poza treningiem: ${activityLabel(dp.activityLevel)}\n")

                // PREFERENCJE DIETETYCZNE — TWARDE OGRANICZENIA
                val prefLabel = dietPreferenceLabel(dp.dietPreference)
                if (prefLabel.isNotBlank()) {
                    append("- Preferencja: $prefLabel ⚠ MUSI być przestrzegana\n")
                }

                // ALERGIE — KRYTYCZNE
                val dpAllergies = dp.parsedAllergies()
                if (dpAllergies.isNotEmpty()) {
                    append("- ⚠ ALERGIE (BEZWZGLĘDNIE UNIKAJ): ${dpAllergies.joinToString(", ")}\n")
                }
                if (dp.intolerances.isNotBlank()) {
                    append("- Nietolerancje: ${dp.intolerances}\n")
                }

                // PREFEROWANE/UNIKANE PRODUKTY (z onboardingu, niezależne od MealFeedback)
                val dpLoved = dp.parsedLovedFoods()
                if (dpLoved.isNotEmpty()) {
                    append("- Lubi: ${dpLoved.joinToString(", ")}\n")
                }
                val dpDisliked = dp.parsedDislikedFoods()
                if (dpDisliked.isNotEmpty()) {
                    append("- NIE lubi: ${dpDisliked.joinToString(", ")}\n")
                }

                // PRAKTYCZNE OGRANICZENIA WYKONALNOŚCI
                append("- Max czas na 1 posiłek: ${dp.cookingTimePerMealMin} min ⚠ przepisy MUSZĄ się zmieścić\n")
                if (dp.eatsAtWork) {
                    append("- Jada w pracy/szkole — obiad MUSI być przenośny w pojemniku")
                    if (dp.hasMicrowaveAtWork) append(" (ma mikrofalówkę)\n")
                    else append(" (BEZ mikrofalówki — preferuj zimne dania: sałatki, kanapki, wraps)\n")
                }
                if (dp.mealPrepInterested) {
                    append("- Meal prep: tak — preferuj porcje gotujące się raz na 2-3 dni (np. zapiekanki, gulasze)\n")
                }
                dp.weeklyBudgetPln?.let { budget ->
                    val tier = when {
                        budget <= 100 -> "BARDZO NISKI: kurczak udko, jaja, twaróg, kasza, ryż, marchew, kapusta — UNIKAJ łososia, wołowiny, awokado, nasion chia"
                        budget <= 200 -> "NISKI: kurczak pierś, jaja, twaróg, ryż, makaron, sezon. warzywa — UNIKAJ łososia, wołowiny premium"
                        budget <= 300 -> "ŚREDNI: większość produktów OK, oszczędnie z premium (łosoś 1-2x/tydz)"
                        else -> "WYSOKI: brak ograniczeń"
                    }
                    append("- Budżet tygodniowy: $budget zł — $tier\n")
                }

                // STANY ZDROWIA — konserwatywne sugestie
                val medical = dp.parsedMedicalConditions()
                if (medical.isNotEmpty()) {
                    append("- ⚠ Stany zdrowia: ${medical.joinToString(", ")} — WYMAGAJ konsultacji ze specjalistą, generuj KONSERWATYWNE plany:\n")
                    if ("cukrzyca" in medical) append("  • cukrzyca: niski IG, ograniczone proste cukry, podziel węgle równomiernie\n")
                    if ("nadciśnienie" in medical) append("  • nadciśnienie: niska sól, ograniczone konserwy/wędliny\n")
                    if ("choroby_nerek" in medical) append("  • nerki: kontrolowane białko (NIE 2g/kg), niski potas/fosfor — sugeruj konsultację\n")
                    if ("ciąża" in medical || "karmienie_piersią" in medical) append("  • ciąża/karmienie: NIE redukcja kcal, dodatek żelazo+kwas foliowy, unikaj sushi/serów pleśniowych\n")
                    if ("zaburzenia_odżywiania" in medical) append("  • zab. odżywiania: NIE liczenie kcal jako głównego mechanizmu, łagodny ton, sugeruj specjalistę\n")
                }
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

            append("\n=== DZIŚ (z TrainingDaySummary) ===\n")
            if (isTrainingDay) {
                append("- DZIEŃ TRENINGOWY (${trainingType?.name ?: "?"})\n")
                intensity?.let { append("- Intensywność: ${it.name}\n") }
                if (muscleGroupsToday.isNotEmpty()) {
                    append("- Trenowane partie: ${muscleGroupsToday.joinToString(", ")}\n")
                }
                todaySummary?.fatigueScore?.let { append("- Zmęczenie po treningu: $it/10\n") }
                append("- → Dieta: WIĘCEJ węgli pre/post-WO. Białko stałe. Wodę i elektrolity.\n")
                if (intensity == pl.filebit.gymtracker.data.entity.IntensityScore.HEAVY) {
                    append("- TRENING CIĘŻKI: zwiększ węgle o ~20% w obiad/post-WO. Twaróg na noc kazeina.\n")
                }
                if (muscleGroupsToday.contains("QUADS") || muscleGroupsToday.contains("HAMSTRINGS") || muscleGroupsToday.contains("GLUTES")) {
                    append("- DZIEŃ NÓG: największe zapotrzebowanie na węgle. Daj 30% więcej w obiad.\n")
                }
            } else {
                append("- DZIEŃ REGENERACJI (rest)\n")
                append("- → Dieta: MNIEJ węgli (-10% vs trening), WIĘCEJ tłuszczu i białka. Niski deficyt OK.\n")
            }

            append("\n=== CEL DZIENNY ===\n")
            append("**KCAL — TWARDY (musi być trafiony):**\n")
            append("- **${goal.kcal} kcal** ⚠ Suma posiłków MUSI być ${(goal.kcal*0.93).toInt()}–${(goal.kcal*1.07).toInt()} kcal\n")
            append("- Deficyt/nadwyżka jest JUŻ WLICZONA w ten cel — NIE odejmuj dodatkowych kalorii!\n\n")

            append("**MAKRO — asymetryczne progi (filozofia dietetyki sportowej):**\n")
            append("- Białko: **min ${goal.proteinG}g** (cel = MINIMUM, nadmiar OK do +50%, niedobór szkodzi mięśniom). " +
                "Bezwzględny min ${(goal.proteinG*0.85).toInt()}g.\n")
            append("- Tłuszcz: **min ${(goal.fatG*0.75).toInt()}g, cel ~${goal.fatG}g** (potrzebny dla hormonów; powyżej +30% — bez sensu).\n")
            append("- Węgle: **~${goal.carbsG}g** (balansujące makro — wypełnij to co zostanie po białku/tłuszczu, ±30% OK).\n\n")
            append("→ Reguła praktyczna: TRAFI W KCAL → spełnij białko (≥cel) → tłuszcz (≥min) → węgle wezmą resztę.\n")

            append("\n=== KONFIGURACJA DNIA ===\n")
            append("- Liczba posiłków: $mealsCount\n")
            append("- Okno żywieniowe: ${config.eatingWindowHours}h (${"%02d:00".format(config.windowStartHour)}–${"%02d:00".format(config.windowEndHour())})\n")
            append("- Cel kcal/posiłek: ~$perMealKcal kcal\n")
            append("- Cel makro/posiłek: B${perMealProtein}g W${perMealCarbs}g T${perMealFat}g\n")

            // === STYL PLANU (od usera, przed wygenerowaniem) ===
            if (stylePrefs.globalStyle != PlanStyle.CLASSIC ||
                stylePrefs.slotStyles.isNotEmpty() ||
                stylePrefs.freeText.isNotBlank()) {
                append("\n=== STYL PLANU (preferencje usera dla TEJ generacji) ===\n")
                if (stylePrefs.globalStyle != PlanStyle.CLASSIC) {
                    append("Styl globalny: **${stylePrefs.globalStyle.label}** — ${stylePrefs.globalStyle.promptHint}\n")
                }
                if (stylePrefs.slotStyles.isNotEmpty()) {
                    append("Per slot:\n")
                    val typesForSlots = mealTypesForSlots(mealsCount)
                    typesForSlots.forEachIndexed { idx, type ->
                        val style = stylePrefs.slotStyles[type] ?: return@forEachIndexed
                        if (style == MealStyle.DEFAULT || style == MealStyle.NO_PREFERENCE) return@forEachIndexed
                        val slotLabel = labelForSlot(idx + 1, mealsCount)
                        append("  - $slotLabel: **${style.label}** — ${style.promptHint}\n")
                    }
                }
                if (stylePrefs.freeText.isNotBlank()) {
                    append("Dodatkowe życzenia usera: \"${stylePrefs.freeText}\"\n")
                }
                append("→ Honor preferencje stylu przy generowaniu, ALE makro/kcal targety dalej obowiązują.\n")
            }

            append("\n=== SLOTY (z godzinami i kaloriami) ===\n")
            slotLabels.forEach { append("- $it\n") }

            // === ⚠ TWARDE WYMAGANIE: ROZKŁAD KCAL PER SLOT ===
            append("\n=== ⚠ ROZKŁAD KCAL PER POSIŁEK (TWARDY WYMÓG — INACZEJ PLAN ZOSTANIE ODRZUCONY) ===\n")
            append("MUSISZ wygenerować DOKŁADNIE $mealsCount posiłków (nie mniej, nie więcej).\n")
            append("Każdy posiłek MUSI mieć kcal w zakresie ±15% od targetu poniżej:\n")
            perSlotKcalTargets.forEachIndexed { idx, kcalTarget ->
                val label = labelForSlot(idx + 1, mealsCount)
                val minK = (kcalTarget * 0.85).toInt()
                val maxK = (kcalTarget * 1.15).toInt()
                append("  $idx. $label = **$kcalTarget kcal** (zakres dopuszczalny: $minK–$maxK kcal)\n")
            }
            append("\nZAKAZ: wrzucania całych ${goal.kcal} kcal w jeden posiłek.\n")
            append("ZAKAZ: pomijania któregokolwiek slotu (każdy slot MUSI mieć posiłek z >${(perMealKcal*0.5).toInt()} kcal).\n")
            append("ZAKAZ: generowania mniej kcal niż target — jeśli wyjdzie ${(goal.kcal*0.85).toInt()} kcal zamiast ${goal.kcal}, ZWIĘKSZ gramatury!\n")
            append("**Suma wszystkich posiłków MUSI być ${goal.kcal} kcal ±7% (czyli ${(goal.kcal*0.93).toInt()}–${(goal.kcal*1.07).toInt()}).**\n")
            append("Strategia gdy nie wychodzi: zwiększ porcje białka (kurczak 200→250g), dodaj zdrowy tłuszcz (orzechy 20→30g, oliwa 10→15g), dorzuć węgiel (ryż 100→150g).\n")

            // PRE/POST workout — szczegółowe instrukcje
            val hasPreOrPost = slotContexts.any {
                it.workoutContext != pl.filebit.gymtracker.data.entity.WorkoutContext.NORMAL
            }
            if (hasPreOrPost && isTrainingDay) {
                append("\n=== POSIŁKI WOKÓŁ TRENINGU ===\n")
                slotContexts.forEachIndexed { idx, ctx ->
                    when (ctx.workoutContext) {
                        pl.filebit.gymtracker.data.entity.WorkoutContext.PRE_WORKOUT -> {
                            append("- Slot ${idx + 1} = PRE-WORKOUT (${config.formatTime(ctx.mealHourDecimal)}):\n")
                            append("  • SZYBKIE węgle (banan, ryż biały, miód, owsianka błyskawiczna)\n")
                            append("  • ŚREDNIE białko 20-30g (twaróg/jogurt/kanapka z indykiem)\n")
                            append("  • MAŁO tłuszczu (gorsze trawienie przed wysiłkiem)\n")
                            append("  • Brak warzyw kapustnych (gazy)\n")
                        }
                        pl.filebit.gymtracker.data.entity.WorkoutContext.POST_WORKOUT -> {
                            append("- Slot ${idx + 1} = POST-WORKOUT (${config.formatTime(ctx.mealHourDecimal)}):\n")
                            append("  • DUŻO białka 30-40g (kurczak/ryba/twaróg/WPI)\n")
                            append("  • SZYBKIE węgle 60-80g (ryż biały, ziemniaki, banany)\n")
                            append("  • MAŁO tłuszczu (spowalnia anabolizm)\n")
                            append("  • Anaboliczne okno — nie pomijaj\n")
                        }
                        else -> { /* normal */ }
                    }
                }
            }

            // === PREFERENCJE USERA (z MealFeedback) ===
            if (favorites.isNotEmpty() || disliked.isNotEmpty()) {
                append("\n=== PREFERENCJE USERA (na bazie wcześniejszych ocen) ===\n")
                if (favorites.isNotEmpty()) {
                    append("ULUBIONE (PREFERUJ — używaj jako inspirację, możesz powtarzać 1-2x w tygodniu):\n")
                    favorites.forEach { f ->
                        append("- ${f.displayName} (ocena ${f.rating}/5, zjedzono ${f.timesEaten}×")
                        if (f.tags.isNotBlank()) append(", tagi: ${f.tags}")
                        append(")\n")
                    }
                }
                if (disliked.isNotEmpty()) {
                    append("UNIKAJ (rating ≤2 — nie generuj tych dań ani BARDZO podobnych):\n")
                    disliked.forEach { f ->
                        append("- ${f.displayName} (ocena ${f.rating}/5")
                        if (f.tags.isNotBlank()) append(", powód: ${f.tags}")
                        if (f.notes.isNotBlank()) append(", notatka: ${f.notes}")
                        append(")\n")
                    }
                }
                append("→ Jeśli tworzysz nowe dania, inspiruj się PROFILEM smakowym ulubionych (np. user lubi twaróg → częściej twaróg w innych daniach).\n")
            }

            // === Constraints (HARD i SOFT) z ConstraintResolver ===
            val constraintsForPrompt = constraintResolver.resolve(profile, dietProfile)
            val constraintsText = constraintResolver.toPromptText(constraintsForPrompt)
            if (constraintsText.isNotBlank()) {
                append("\n")
                append(constraintsText)
            }

            // === SEZONOWOŚĆ (z dietary_knowledge.json) ===
            val now = java.util.Calendar.getInstance()
            val month = now.get(java.util.Calendar.MONTH) + 1
            val seasonal = runCatching { knowledgeRepo.seasonalForMonth(month) }.getOrNull().orEmpty()
            if (seasonal.isNotEmpty()) {
                val monthName = listOf("styczniu", "lutym", "marcu", "kwietniu", "maju", "czerwcu",
                    "lipcu", "sierpniu", "wrześniu", "październiku", "listopadzie", "grudniu")[month - 1]
                append("\n=== W SEZONIE (w $monthName) ===\n")
                append("Preferuj te owoce/warzywa — świeższe, lepsze ceny, lepszy smak:\n")
                append(seasonal.take(20).joinToString(", "))
                append("\n")
            }

            // === LOW IG (gdy cukrzyca lub insulinooporność) ===
            val medical = dietProfile?.parsedMedicalConditions().orEmpty()
            if ("cukrzyca" in medical || "insulinooporność" in medical) {
                val lowGi = runCatching { knowledgeRepo.lowGiProducts() }.getOrNull().orEmpty()
                if (lowGi.isNotEmpty()) {
                    append("\n=== PRODUKTY LOW IG (cukrzyca/IR — preferuj!) ===\n")
                    append("Stabilizują poziom cukru. Wybieraj te ZAMIAST produktów o wysokim IG:\n")
                    append(lowGi.take(30).joinToString(", ") { "${it.name} (IG ${it.gi})" })
                    append("\n")
                }
            }

            // === ULUBIONE PRODUKTY USERA (oznaczone ❤ w aplikacji) ===
            if (favoriteProducts.isNotEmpty()) {
                append("\n=== ULUBIONE PRODUKTY USERA (oznaczone ❤ — UŻYWAJ ICH JAKO BAZY) ===\n")
                append("User wybrał te produkty jako preferowane. Plan MUSI je wykorzystywać:\n")
                favoriteProducts.forEach { fp ->
                    append("- ⭐ ${fp.name} (${fp.kcalPer100g.toInt()} kcal/100g, B${fp.proteinPer100g.toInt()}/W${fp.carbsPer100g.toInt()}/T${fp.fatPer100g.toInt()})\n")
                }
                append("ZASADA: każdy posiłek MUSI zawierać ≥1 produkt z tej listy ulubionych.\n")
                append("ZASADA: użyj co najmniej ${favoriteProducts.size.coerceAtMost(mealsCount)} różnych ulubionych produktów w całym planie dnia.\n\n")
            }

            // === RÓŻNORODNOŚĆ — wymuszamy żeby AI nie generowało zawsze tego samego ===
            val seedRandom = (System.currentTimeMillis() / 1000).toString().takeLast(6)
            append("\n=== RÓŻNORODNOŚĆ (KRYTYCZNE) ===\n")
            append("- Seed dziennej zmienności: $seedRandom (użyj go żeby plan dnia BYŁ INNY niż wczoraj)\n")
            append("- ZAKAZ powtarzania tego samego głównego białka >1× w planie dnia (śniadanie kurczak → obiad NIE kurczak)\n")
            append("- ZAKAZ powtarzania tego samego węgla bazowego >1× (jeśli ryż w obiad → kolacja NIE ryż)\n")
            append("- Każdy posiłek = INNY profil smakowy (jeden słodki/owsiankowy, drugi mięsny/wytrawny, trzeci lekki)\n")
            append("- Jeśli generujesz po raz N-ty — wybieraj składniki dalsze od wcześniejszych iteracji\n\n")

            append("\n=== DOSTĘPNE PRODUKTY (używaj WYŁĄCZNIE z tej listy, nazwy DOKŁADNIE) ===\n")
            append("⭐ = ulubiony produkt usera — preferuj go w planie\n")
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

            append("\n=== ALTERNATYWY PER SLOT ===\n")
            append("Dla KAŻDEGO posiłku podaj 2 alternatywy (`alternatives` w JSON) — różnorodność:\n")
            append("- alternatywa 1: bazujący na INNYM białku (np. mięso → ryba lub roślinne strączki)\n")
            append("- alternatywa 2: INNE węgle / styl (np. owsianka → kanapka, ryż → kasza)\n")
            append("- każda alternatywa MUSI mieć kcal/B/W/T w ±10% od głównego posiłku tego slotu\n")
            append("- każda alternatywa MUSI używać productName z listy DOSTĘPNYCH PRODUKTÓW\n")
            append("- alternatywy MUSZĄ honorować preferencje/alergie/budżet/medical (te same reguły)\n")
            append("- jeśli któraś preferencja (vegan/keto/itd.) wyklucza alternatywę — pomiń ją (ALE postaraj się dać 2 jak się da)\n")
            append("- alternatywy NIE potrzebują instrukcji (instructions=\"\") — user dostanie je przy wyborze\n\n")

            append("=== OUTPUT ===\n")
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
                  "fatG": 8,
                  "alternatives": [
                    {
                      "name": "Jajecznica na maśle z pieczywem razowym",
                      "ingredients": [
                        {"productName": "Jajka", "grams": 150},
                        {"productName": "Masło", "grams": 10},
                        {"productName": "Chleb razowy", "grams": 80}
                      ],
                      "kcal": 475,
                      "proteinG": 32,
                      "carbsG": 45,
                      "fatG": 18,
                      "prepMinutes": 6,
                      "instructions": ""
                    },
                    {
                      "name": "Skyr z miodem i orzechami",
                      "ingredients": [
                        {"productName": "Skyr naturalny", "grams": 250},
                        {"productName": "Miód", "grams": 15},
                        {"productName": "Orzechy włoskie", "grams": 25}
                      ],
                      "kcal": 470,
                      "proteinG": 30,
                      "carbsG": 38,
                      "fatG": 18,
                      "prepMinutes": 2,
                      "instructions": ""
                    }
                  ]
                }
              ]
            }
            """.trimIndent())
        }

        // Buduj kontekst walidacji
        val constraints = constraintResolver.resolve(profile, dietProfile)
        val productsByName = products.associateBy { it.name.lowercase() }
        val validationCtx = ValidationContext(
            expectedMealsCount = mealsCount,
            targetKcal = goal.kcal,
            targetProteinG = goal.proteinG,
            targetCarbsG = goal.carbsG,
            targetFatG = goal.fatG,
            perMealProteinMinG = (perMealProtein * 0.7).toInt().coerceAtLeast(15),
            maxCookingMinutesPerMeal = dietProfile?.cookingTimePerMealMin ?: 20,
            ketoMaxCarbsG = if (dietProfile?.dietPreference == pl.filebit.gymtracker.data.entity.DietPreference.KETO) 30 else null,
            productsByName = productsByName,
            constraints = constraints,
            perSlotKcalTargets = perSlotKcalTargets,
            perSlotKcalTolerance = 0.15,
            enforceDailyMacros = true,
            dailyMacroTolerance = 0.20
        )

        // Próba 1: oryginalny prompt
        var attempt = 1
        var validation: ValidationResult? = null
        var parsed: AiDayPlan? = null
        var lastRaw: String? = null
        var currentPrompt = prompt

        while (attempt <= 3) {
            val callResult = client.chat(cfg, listOf(AiMessage(AiRole.USER, currentPrompt)))
            if (callResult.isFailure) return callResult.map { GeneratedDayPlan(emptyList()) }

            lastRaw = callResult.getOrThrow()
            Log.d("DietAiService", "attempt=$attempt raw response: ${lastRaw!!.take(500)}")
            val cleaned = stripJsonFences(lastRaw!!)
            parsed = runCatching { json.decodeFromString<AiDayPlan>(cleaned) }.getOrNull()
            if (parsed == null || parsed.meals.isEmpty()) {
                return Result.failure(IllegalStateException("AI zwróciło niepoprawny lub pusty JSON (attempt=$attempt)"))
            }

            // === AUTO-SCALE GRAMATUR ===
            // AI generuje plan blisko celu — my deterministycznie skalujemy gramatury
            // żeby trafić w target kcal per slot. Walidacja po skalowaniu powinna przejść.
            parsed = autoScaleSlotKcal(parsed, perSlotKcalTargets, productsByName)

            validation = validator.validate(parsed, validationCtx)
            Log.d("DietAiService", "validation isValid=${validation.isValid} errors=${validation.errors.size} warnings=${validation.warnings.size}")
            if (validation.isValid) break

            // Retry z error feedback
            val feedback = validator.buildRetryFeedback(validation)
            currentPrompt = "$prompt\n\n=== POPRAWKA (PILNE) ===\n$feedback"
            attempt++
        }

        if (parsed == null) return Result.failure(IllegalStateException("AI nie zwróciło planu"))
        if (validation != null && !validation.isValid) {
            // Po 3 próbach nadal HARD violations — zwróć błąd informujący
            val errorMsgs = validation.errors.joinToString("\n") { "• ${it.message}" }
            return Result.failure(IllegalStateException("Po 3 próbach AI nadal generuje plan z naruszeniami HARD constraints:\n$errorMsgs"))
        }

        // Mapowanie sloty → MealType
        val typesForSlots: List<MealType> = when (mealsCount) {
            2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
            3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
            4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
            5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
            6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
            else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        }
        val mealsForSlots = parsed.meals.zip(typesForSlots).map { (m, t) -> t to m }
        return Result.success(GeneratedDayPlan(mealsForSlots, validation?.warnings.orEmpty(), validation?.correctedTotal))
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

    private fun mealTypesForSlots(mealsCount: Int): List<MealType> = when (mealsCount) {
        2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
        5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
        else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    }

    private fun activityLabel(level: pl.filebit.gymtracker.data.entity.ActivityLevel): String = when (level) {
        pl.filebit.gymtracker.data.entity.ActivityLevel.SEDENTARY -> "siedzący tryb (biuro)"
        pl.filebit.gymtracker.data.entity.ActivityLevel.LIGHT -> "lekko aktywny"
        pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE -> "umiarkowanie aktywny"
        pl.filebit.gymtracker.data.entity.ActivityLevel.VERY_ACTIVE -> "bardzo aktywny"
        pl.filebit.gymtracker.data.entity.ActivityLevel.EXTREME -> "ekstremalnie aktywny"
    }

    /**
     * Standard dietetyczny rozkładu kcal na posiłki — różne proporcje w zależności od liczby posiłków.
     * Suma = total ±2 (zaokrąglenia).
     */
    private fun computePerSlotKcalDistribution(totalKcal: Int, mealsCount: Int): List<Int> {
        val ratios = when (mealsCount) {
            2 -> listOf(0.45, 0.55)                              // Śniadanie 45%, Kolacja 55%
            3 -> listOf(0.30, 0.40, 0.30)                        // Śniadanie/Obiad/Kolacja
            4 -> listOf(0.25, 0.10, 0.40, 0.25)                  // Śniad/IIŚniad/Obiad/Kolacja
            5 -> listOf(0.22, 0.10, 0.38, 0.10, 0.20)            // 5 posiłków klasyk
            6 -> listOf(0.20, 0.10, 0.30, 0.10, 0.10, 0.20)      // 6 posiłków bodybuilding
            else -> List(mealsCount) { 1.0 / mealsCount }
        }
        return ratios.map { (totalKcal * it).toInt() }
    }

    private fun computePerSlotProteinDistribution(totalP: Int, mealsCount: Int): List<Int> =
        // Białko ~stale rozłożone: trochę więcej w obiad i kolację (regeneracja)
        when (mealsCount) {
            2 -> listOf(0.45, 0.55)
            3 -> listOf(0.30, 0.35, 0.35)
            4 -> listOf(0.27, 0.13, 0.32, 0.28)
            5 -> listOf(0.23, 0.12, 0.30, 0.13, 0.22)
            6 -> listOf(0.20, 0.12, 0.25, 0.13, 0.12, 0.18)
            else -> List(mealsCount) { 1.0 / mealsCount }
        }.map { (totalP * it).toInt() }

    private fun computePerSlotCarbsDistribution(totalC: Int, mealsCount: Int): List<Int> =
        // Węgle: dużo śniadanie i obiad, MAŁO kolacja (low-carb wieczorem)
        when (mealsCount) {
            2 -> listOf(0.55, 0.45)
            3 -> listOf(0.35, 0.45, 0.20)
            4 -> listOf(0.30, 0.13, 0.45, 0.12)
            5 -> listOf(0.25, 0.12, 0.40, 0.13, 0.10)
            6 -> listOf(0.22, 0.13, 0.35, 0.12, 0.10, 0.08)
            else -> List(mealsCount) { 1.0 / mealsCount }
        }.map { (totalC * it).toInt() }

    private fun computePerSlotFatDistribution(totalF: Int, mealsCount: Int): List<Int> =
        // Tłuszcze: rozłożone, mało wieczorem (lepsze trawienie)
        when (mealsCount) {
            2 -> listOf(0.50, 0.50)
            3 -> listOf(0.30, 0.40, 0.30)
            4 -> listOf(0.27, 0.10, 0.40, 0.23)
            5 -> listOf(0.22, 0.10, 0.35, 0.13, 0.20)
            6 -> listOf(0.20, 0.10, 0.30, 0.13, 0.12, 0.15)
            else -> List(mealsCount) { 1.0 / mealsCount }
        }.map { (totalF * it).toInt() }

    /**
     * Skaluje gramatury w każdym posiłku tak, żeby trafić w target kcal per slot.
     * AI często generuje "blisko" celu — auto-scale dopasowuje idealnie deterministycznie.
     *
     * Strategia:
     *  1. Liczymy real kcal slotu z bazy produktów
     *  2. factor = target / real
     *  3. Cap factor ∈ [0.6, 1.5] żeby nie tworzyć absurdalnych porcji (1500g ryżu)
     *  4. Mnożymy KAŻDĄ gramaturę przez factor (zachowując proporcje składników)
     *  5. Sanity: minimum 5g, max 1000g per ingredient
     */
    private fun autoScaleSlotKcal(
        plan: AiDayPlan,
        perSlotKcalTargets: List<Int>,
        productsByName: Map<String, FoodProduct>
    ): AiDayPlan {
        if (perSlotKcalTargets.isEmpty()) return plan

        val scaledMeals = plan.meals.mapIndexed { idx, meal ->
            val target = perSlotKcalTargets.getOrNull(idx) ?: return@mapIndexed meal

            // Real kcal slotu z bazy
            val realKcal = meal.ingredients.sumOf { ing ->
                val key = ing.productName.trim().lowercase()
                val product = productsByName[key]
                    ?: productsByName.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value
                if (product != null) product.kcalPer100g * ing.grams / 100.0 else 0.0
            }

            if (realKcal < 50) return@mapIndexed meal // Zbyt mało żeby skalować — fallback na walidator

            val rawFactor = target / realKcal
            val factor = rawFactor.coerceIn(0.6, 1.5)
            Log.d("DietAiService", "autoScale slot=$idx real=${realKcal.toInt()} target=$target factor=$factor")

            val scaled = meal.ingredients.map { ing ->
                val newGrams = (ing.grams * factor).toInt().coerceIn(5, 1000)
                ing.copy(grams = newGrams)
            }
            meal.copy(ingredients = scaled)
        }
        return plan.copy(meals = scaledMeals)
    }

    private fun dietPreferenceLabel(pref: pl.filebit.gymtracker.data.entity.DietPreference): String = when (pref) {
        pl.filebit.gymtracker.data.entity.DietPreference.STANDARD -> ""
        pl.filebit.gymtracker.data.entity.DietPreference.VEGETARIAN -> "wegetarianizm (BEZ mięsa, ryby OK? — domyślnie NIE)"
        pl.filebit.gymtracker.data.entity.DietPreference.VEGAN -> "weganizm (BEZ produktów odzwierzęcych — żadnego mięsa, ryb, nabiału, jaj, miodu)"
        pl.filebit.gymtracker.data.entity.DietPreference.PESCATARIAN -> "pescetarianizm (ryby + nabiał + jaja, BEZ mięsa)"
        pl.filebit.gymtracker.data.entity.DietPreference.KETO -> "dieta ketogeniczna (max 30g węgli netto/dzień, dużo tłuszczu)"
        pl.filebit.gymtracker.data.entity.DietPreference.MEDITERRANEAN -> "śródziemnomorska (oliwa, ryby, warzywa, pełnoziarniste, ograniczone czerwone mięso)"
    }
}
