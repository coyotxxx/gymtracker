package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.ActivityLevel
import pl.filebit.gymtracker.data.entity.DietPreference
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType

/**
 * Kompletny kontekst użytkownika dla AI services.
 *
 * Filozofia: JEDEN obiekt zawiera CAŁE info o userze. Każdy AI service
 * (DietAi, WorkoutPlanAi, EmergencyMeal, WeeklyReport, PlanAudit) używa
 * tego samego — pełna spójność między dietą a treningiem.
 *
 * Wzorzec: builder zbiera wszystko z 12 tabel. AI service wybiera
 * potrzebne sekcje przez `toBaseProfileSection()`, `toTrainingHistorySection()`,
 * `toDietHistorySection()`, `toRecoverySection()`, etc.
 */
data class MasterAiContext(
    // === BAZOWY PROFIL ===
    val displayName: String,
    val gender: Gender,
    val ageYears: Int?,
    val heightCm: Int?,
    val weightKg: Double?,                  // NAJŚWIEŻSZA z BodyMeasurement
    val targetWeightKg: Double?,
    val experience: ExperienceLevel,
    val trainingGoal: TrainingGoal,
    val weightGoalType: WeightGoalType,
    val daysPerWeek: Int,
    val sessionMinutes: Int,
    val defaultRestSeconds: Int,
    val injuriesNotes: String,              // wolny tekst kontuzji

    // === PROFIL DIETETYCZNY ===
    val activityLevel: ActivityLevel,
    val avgStepsPerDay: Int,
    val dietPreference: DietPreference,
    val allergiesCsv: String,
    val intolerances: String,
    val dislikedFoodsCsv: String,
    val lovedFoodsCsv: String,
    val weeklyBudgetPln: Int?,
    val medicalConditionsCsv: String,
    val cookingTimePerMealMin: Int,
    val usualTrainingHour: Int?,
    val dietProfileFilled: Boolean,         // false = user pominął profil dietetyczny

    // === SPRZĘT ===
    val availableEquipmentCsv: String,      // pusty = pełna siłownia

    // === ULUBIONE ===
    val favoriteExercises: List<Exercise>,  // do generatora planu
    val avoidedExercises: List<Exercise>,   // user oznaczył jako "unikam" — AI nie wstawi
    val favoriteFoodNames: List<String>,    // do generatora diety
    val likedMealNames: List<String>,       // rating ≥4
    val dislikedMealNames: List<String>,    // rating ≤2

    // === HISTORIA WAGI ===
    val weightTrendSlopeKgPerWeek: Double?,
    val weightAvg7d: Double?,
    val weightAvg14d: Double?,
    val weightSampleCount: Int,             // ilość pomiarów w ostatnich 14 dniach

    // === ADHERENCE DIETY (14 dni) ===
    val avgKcalAdherencePct: Int,           // 0-200, 100=trafia w cel
    val avgProteinAdherencePct: Int,
    val avgCarbsAdherencePct: Int,
    val avgFatAdherencePct: Int,
    val mealsLoggedDays: Int,               // ile dni z wpisanymi posiłkami (0-14)
    val workoutsPlanned14d: Int,
    val workoutsDone14d: Int,

    // === CELE MAKRO (dzienne, ABSOLUTNE) — v2.38.0: AI ma znać LICZBY, nie tylko % ===
    val targetKcal: Int = 0,
    val targetProteinG: Int = 0,
    val targetCarbsG: Int = 0,
    val targetFatG: Int = 0,
    val mealsPerDay: Int = 0,
    val perMealKcal: Int = 0,                // cel kcal podzielony przez liczbę posiłków
    val perMealProteinG: Int = 0,

    // === RECOVERY (7 dni) ===
    val avgSleepHours: Double?,
    val avgSleepQuality: Double?,           // 1-5
    val avgStressLevel: Double?,
    val avgHungerLevel: Double?,
    val avgEnergyLevel: Double?,
    val avgSorenessLevel: Double?,
    val avgDifficultyLevel: Double?,
    val recoverySampleDays: Int,
    // Z zegarka (v1.7.3) — null gdy brak danych
    val avgRestingHr: Double? = null,
    val avgSpO2: Double? = null,
    val avgHrv: Double? = null,
    val avgVo2max: Double? = null,
    val elevatedHeartRate: Boolean = false,
    val lowHrv: Boolean = false,
    val lowSpO2: Boolean = false,
    val improvingFitness: Boolean = false,
    // === v1.9.0: Recovery per partia + Training Readiness ===
    val muscleRecoveryReport: MuscleRecoveryReport? = null,
    val trainingReadiness: TrainingReadiness? = null,

    // === NEAT (kroki) ===
    val avgSteps7d: Int,
    val avgSteps30d: Int,
    val baselineSteps: Int,                 // z UserDietProfile lub aktualnej średniej

    // === HYDRATION ===
    val hydrationAdherencePct: Int,         // 14d średnia

    // === FAZA DIETY ===
    val currentDietPhase: String,           // CUT/BULK/MAINTAIN/DELOAD/REFEED — z DietPhase lub UserProfile
    val daysInCurrentPhase: Int,

    // === DZIŚ ===
    val isTodayTrainingDay: Boolean,
    val todayTrainingTypeLabel: String?,    // np. "PUSH" / "LEGS" / null jeśli rest
    val todayMuscleGroups: List<String>,    // ["CHEST","TRICEPS"] z TrainingDaySummary

    // === PRy (top 5 ćwiczeń) ===
    val topPRs: List<PRSummary>,

    // === META ===
    val builtAt: Long = System.currentTimeMillis()
)

/** Krótkie summary PR ćwiczenia. */
data class PRSummary(
    val exerciseName: String,
    val maxWeightKg: Double,
    val repsAtMax: Int,
    val estimatedOneRepMaxKg: Double
)

/**
 * Konwersja MasterAiContext do tekstu sekcji prompt.
 * Każdy AI service wybiera potrzebne sekcje.
 */
object MasterAiContextPromptHelper {

    /** Bazowy profil — używają wszystkie services. */
    fun toBaseProfileSection(ctx: MasterAiContext): String = buildString {
        append("=== PROFIL UŻYTKOWNIKA ===\n")
        if (ctx.displayName.isNotBlank()) append("- Imię: ${ctx.displayName}\n")
        append("- Płeć: ${if (ctx.gender == Gender.MALE) "mężczyzna" else "kobieta"}\n")
        ctx.ageYears?.let { append("- Wiek: $it lat\n") }
        ctx.heightCm?.let { append("- Wzrost: $it cm\n") }
        ctx.weightKg?.let { append("- Aktualna waga: %.1f kg\n".format(it)) }
        ctx.targetWeightKg?.let { append("- Waga docelowa: $it kg\n") }
        append("- Doświadczenie: ${experienceLabel(ctx.experience)}\n")
        append("- Cel treningowy: ${trainingGoalLabel(ctx.trainingGoal)}\n")
        append("- Cel wagowy: ${weightGoalLabel(ctx.weightGoalType)}\n")
        append("- Dni treningowe/tydz: ${ctx.daysPerWeek}\n")
        append("- Czas sesji: ${ctx.sessionMinutes} min\n")
        if (ctx.injuriesNotes.isNotBlank()) {
            append("- ⚠ KONTUZJE/UWAGI: ${ctx.injuriesNotes}\n")
        }
        if (ctx.availableEquipmentCsv.isNotBlank()) {
            append("- Dostępny sprzęt: ${ctx.availableEquipmentCsv}\n")
        } else {
            append("- Dostępny sprzęt: pełna siłownia (wszystko)\n")
        }
    }

    /** Profil dietetyczny — DietAi, EmergencyMeal. */
    fun toDietProfileSection(ctx: MasterAiContext): String = buildString {
        if (!ctx.dietProfileFilled) {
            append("\n=== PROFIL DIETETYCZNY ===\n")
            append("(NIEWYPEŁNIONY — używaj domyślnych preferencji)\n")
            return@buildString
        }
        append("\n=== PROFIL DIETETYCZNY ===\n")
        append("- Aktywność poza treningiem: ${activityLevelLabel(ctx.activityLevel)}\n")
        append("- Średnio kroków/dzień: ${ctx.avgStepsPerDay}\n")
        append("- Preferencja: ${dietPreferenceLabel(ctx.dietPreference)}\n")
        if (ctx.allergiesCsv.isNotBlank()) append("- ⚠ ALERGIE: ${ctx.allergiesCsv}\n")
        if (ctx.intolerances.isNotBlank()) append("- Nietolerancje: ${ctx.intolerances}\n")
        if (ctx.lovedFoodsCsv.isNotBlank()) append("- LUBI: ${ctx.lovedFoodsCsv}\n")
        if (ctx.dislikedFoodsCsv.isNotBlank()) append("- NIE LUBI: ${ctx.dislikedFoodsCsv}\n")
        ctx.weeklyBudgetPln?.let { append("- Budżet tygodniowy: $it zł\n") }
        append("- Max czas gotowania/posiłek: ${ctx.cookingTimePerMealMin} min\n")
        if (ctx.medicalConditionsCsv.isNotBlank()) {
            append("- ⚠ STANY ZDROWIA: ${ctx.medicalConditionsCsv} (konserwatywne podejście!)\n")
        }
        ctx.usualTrainingHour?.let { append("- Typowa godzina treningu: ${it}:00\n") }
    }

    /** Trend wagi i adherence — silnik korekt + raporty. */
    fun toWeightTrendSection(ctx: MasterAiContext): String = buildString {
        append("\n=== WAGA I TREND (14 dni) ===\n")
        if (ctx.weightSampleCount < 3) {
            append("- ⚠ Mało pomiarów wagi (${ctx.weightSampleCount}/14 dni). Trend niewiarygodny.\n")
            return@buildString
        }
        ctx.weightAvg7d?.let { append("- Średnia waga 7d: %.2f kg\n".format(it)) }
        ctx.weightAvg14d?.let { append("- Średnia waga 14d: %.2f kg\n".format(it)) }
        ctx.weightTrendSlopeKgPerWeek?.let { slope ->
            val direction = when {
                slope < -0.1 -> "↓ spada"
                slope > 0.1 -> "↑ rośnie"
                else -> "→ stoi"
            }
            append("- Tempo: %.2f kg/tydz $direction\n".format(slope))
        }
        append("- Pomiary w 14d: ${ctx.weightSampleCount}\n")
    }

    /**
     * v2.38.0: ABSOLUTNE cele makro — żeby AI znało LICZBY (ile kcal/białka ma mieć dzień i
     * każdy posiłek), nie tylko procenty. Klucz: AI nie może doradzać/zmieniać posiłków bez
     * znajomości docelowego makro. „Pełna wiedza o userze".
     */
    fun toDailyTargetsSection(ctx: MasterAiContext): String = buildString {
        if (ctx.targetKcal <= 0) return@buildString
        append("\n=== CELE MAKRO (dzienne, ABSOLUTNE) ===\n")
        append("- Cel dnia: ${ctx.targetKcal} kcal · ${ctx.targetProteinG} g białka · ")
        append("${ctx.targetCarbsG} g węgli · ${ctx.targetFatG} g tłuszczu\n")
        if (ctx.mealsPerDay > 0) {
            append("- Liczba posiłków: ${ctx.mealsPerDay} → na 1 posiłek ≈ ")
            append("${ctx.perMealKcal} kcal i ${ctx.perMealProteinG} g białka\n")
        }
        append("- UŻYWAJ TYCH LICZB gdy doradzasz/zmieniasz posiłek (np. ile ma mieć kolacja). ")
        append("Białko priorytet na redukcji. Aktualne posiłki sprawdź narzędziem get_meals.\n")
    }

    /** Adherence diety + frekwencja treningów. */
    fun toAdherenceSection(ctx: MasterAiContext): String = buildString {
        append("\n=== ADHERENCE (14 dni) ===\n")
        if (ctx.mealsLoggedDays < 3) {
            append("- ⚠ Mało dni z logowaniem posiłków (${ctx.mealsLoggedDays}/14).\n")
        } else {
            append("- Dni z dietą: ${ctx.mealsLoggedDays}/14\n")
            append("- Średnia kcal: ${ctx.avgKcalAdherencePct}% celu\n")
            append("- Średnie białko: ${ctx.avgProteinAdherencePct}% celu\n")
            append("- Średnie węgle: ${ctx.avgCarbsAdherencePct}% celu\n")
            append("- Średnie tłuszcze: ${ctx.avgFatAdherencePct}% celu\n")
        }
        if (ctx.workoutsPlanned14d > 0) {
            val pct = ctx.workoutsDone14d * 100 / ctx.workoutsPlanned14d
            append("- Treningi: ${ctx.workoutsDone14d}/${ctx.workoutsPlanned14d} ($pct%)\n")
        }
    }

    /** Recovery + NEAT + hydration — silnik korekt. */
    fun toRecoverySection(ctx: MasterAiContext): String = buildString {
        append("\n=== REGENERACJA I AKTYWNOŚĆ ===\n")
        if (ctx.recoverySampleDays >= 3) {
            ctx.avgSleepHours?.let { append("- Średnio sen: %.1f h\n".format(it)) }
            ctx.avgSleepQuality?.let { append("- Jakość snu: %.1f/5\n".format(it)) }
            ctx.avgStressLevel?.let { append("- Stres: %.1f/5\n".format(it)) }
            ctx.avgEnergyLevel?.let { append("- Energia: %.1f/5\n".format(it)) }
            ctx.avgSorenessLevel?.let { append("- Soreness (DOMS): %.1f/5\n".format(it)) }
            ctx.avgHungerLevel?.let { append("- Głód: %.1f/5\n".format(it)) }
            ctx.avgDifficultyLevel?.let { append("- Trudność diety: %.1f/5\n".format(it)) }
        } else {
            append("- ⚠ Mało wpisów regeneracji (${ctx.recoverySampleDays}/7 dni)\n")
        }
        // Metryki z zegarka (v1.7.3) — pokazuj tylko gdy są
        ctx.avgRestingHr?.let { append("- Tętno spoczynkowe (7d): %.0f bpm\n".format(it)) }
        ctx.avgSpO2?.let { append("- SpO2 (7d): %.0f%%\n".format(it)) }
        ctx.avgHrv?.let { append("- HRV (7d): %.0f ms\n".format(it)) }
        ctx.avgVo2max?.let { append("- VO2Max (7d): %.1f ml/kg/min\n".format(it)) }
        // Sygnały deterministyczne — AI ma wziąć pod uwagę przy decyzjach
        if (ctx.elevatedHeartRate) {
            append("- ⚠ PODWYŻSZONE TĘTNO SPOCZYNKOWE — ostatnie 3 dni ≥+10 bpm vs baseline. Sygnał stresu/przemęczenia/choroby. Rozważ deload/lżejszy trening.\n")
        }
        if (ctx.lowHrv) {
            append("- ⚠ NISKIE HRV — spadek >15% vs baseline 7d. CNS przeładowane, regeneracja słaba. Sugeruj deload.\n")
        }
        if (ctx.lowSpO2) {
            append("- ⚠ NISKIE SpO2 (<94%) — możliwy problem oddechowy lub kiepski sen. Mniej intensywnych treningów.\n")
        }
        if (ctx.improvingFitness) {
            append("- ✅ Kondycja się poprawia — VO2Max rośnie. Plan działa, kontynuuj.\n")
        }
        append("- Kroki: 7d=${ctx.avgSteps7d}/dzień, 30d=${ctx.avgSteps30d}/dzień (baseline ${ctx.baselineSteps})\n")
        append("- Adherence wody (14d): ${ctx.hydrationAdherencePct}%\n")
    }

    /** Faza diety + dziś. */
    fun toCurrentStateSection(ctx: MasterAiContext): String = buildString {
        append("\n=== STAN AKTUALNY ===\n")
        append("- Aktualna faza: ${ctx.currentDietPhase} (dzień ${ctx.daysInCurrentPhase})\n")
        if (ctx.isTodayTrainingDay) {
            // v2.19.0 (P2-4): nie pokazuj sprzecznego "TRENING (REST)" — etykietę typu
            // dodajemy tylko gdy jest sensowna (np. STRENGTH/HYPERTROPHY), nie REST/puste.
            val typeLabel = ctx.todayTrainingTypeLabel
                ?.takeIf { it.isNotBlank() && !it.equals("REST", ignoreCase = true) }
            append("- DZIŚ: dzień treningowy${typeLabel?.let { " ($it)" } ?: ""}\n")
            if (ctx.todayMuscleGroups.isNotEmpty()) {
                append("- Trenowane partie dziś: ${ctx.todayMuscleGroups.joinToString(", ")}\n")
            }
        } else {
            append("- DZIŚ: REGENERACJA (rest)\n")
        }
    }

    /** Top PRy. */
    fun toPRsSection(ctx: MasterAiContext): String = buildString {
        if (ctx.topPRs.isEmpty()) return@buildString
        append("\n=== TOP REKORDY (PR) ===\n")
        ctx.topPRs.take(5).forEach { pr ->
            append("- ${pr.exerciseName}: %.1f kg × ${pr.repsAtMax} (e1RM ≈ %.1f kg)\n".format(pr.maxWeightKg, pr.estimatedOneRepMaxKg))
        }
    }

    /**
     * Preferencje ćwiczeń — lubię (priorytetuj) i unikam (NIE używaj).
     * AI musi to widzieć żeby spersonalizować plan/audyt.
     */
    fun toExercisePreferencesSection(ctx: MasterAiContext): String = buildString {
        if (ctx.favoriteExercises.isNotEmpty()) {
            append("\n=== ULUBIONE ĆWICZENIA (preferuj w nowym planie) ===\n")
            // v2.5.0: z wzorcem ruchowym i poziomem (canonical) — AI rozumie JAKIE
            // wzorce/trudność user preferuje, nie tylko konkretne nazwy.
            append(ctx.favoriteExercises.take(20).joinToString(", ") { ex ->
                val meta = listOfNotNull(
                    ex.movementPattern?.name,
                    ex.levelMin?.name
                ).joinToString("/")
                if (meta.isNotEmpty()) "${ex.name} [$meta]" else ex.name
            })
            append("\n")
        }
        if (ctx.avoidedExercises.isNotEmpty()) {
            append("\n=== ⛔ ĆWICZENIA UNIKANE (BEZWZGLĘDNIE NIE UŻYWAJ) ===\n")
            append(ctx.avoidedExercises.joinToString(", ") { it.name })
            append("\nUser oznaczył te ćwiczenia jako 'unikam' — nie wstawiaj ich do planu, ")
            append("nie proponuj jako alternatyw, nie sugeruj przy audycie. Jeśli partia, którą ")
            append("normalnie pokrywałyby te ćwiczenia, jest niedoreprezentowana — wybierz INNE ćwiczenia ")
            append("na tę samą partię z biblioteki.\n")
        }
    }

    /** Ulubione produkty + posiłki. */
    fun toFavoritesFoodSection(ctx: MasterAiContext): String = buildString {
        if (ctx.favoriteFoodNames.isNotEmpty()) {
            append("\n=== ULUBIONE PRODUKTY (⭐) ===\n")
            append(ctx.favoriteFoodNames.joinToString(", "))
            append("\n")
        }
        if (ctx.likedMealNames.isNotEmpty()) {
            append("\n=== LUBIANE POTRAWY (rating ≥4) ===\n")
            append(ctx.likedMealNames.take(10).joinToString(", "))
            append("\n")
        }
        if (ctx.dislikedMealNames.isNotEmpty()) {
            append("\n=== NIELUBIANE (rating ≤2 — UNIKAJ) ===\n")
            append(ctx.dislikedMealNames.take(5).joinToString(", "))
            append("\n")
        }
    }

    /** v1.9.0 — readiness + recovery per partia (kompozyty z innych analyzerów). */
    fun toReadinessAndMuscleSection(ctx: MasterAiContext): String = buildString {
        ctx.trainingReadiness?.let { append(TrainingReadinessPromptHelper.toPromptSection(it)) }
        ctx.muscleRecoveryReport?.let { append(MuscleRecoveryPromptHelper.toPromptSection(it)) }
    }

    /** Pełen kontekst — używaj jeśli chcesz dać AI wszystko. */
    fun toFullContext(ctx: MasterAiContext): String = buildString {
        append(toBaseProfileSection(ctx))
        append(toDietProfileSection(ctx))
        append(toWeightTrendSection(ctx))
        append(toAdherenceSection(ctx))
        append(toRecoverySection(ctx))
        append(toReadinessAndMuscleSection(ctx))
        append(toCurrentStateSection(ctx))
        append(toPRsSection(ctx))
        append(toExercisePreferencesSection(ctx))
        append(toFavoritesFoodSection(ctx))
    }

    private fun experienceLabel(e: ExperienceLevel): String = when (e) {
        ExperienceLevel.BEGINNER -> "początkujący"
        ExperienceLevel.INTERMEDIATE -> "średnio zaawansowany"
        ExperienceLevel.ADVANCED -> "zaawansowany"
    }

    private fun trainingGoalLabel(g: TrainingGoal): String = when (g) {
        TrainingGoal.STRENGTH -> "SIŁA (3-6 reps, heavy compound)"
        TrainingGoal.HYPERTROPHY -> "HIPERTROFIA — masa mięśniowa (8-12 reps)"
        TrainingGoal.MIX -> "siła + masa (6-8 reps)"
        TrainingGoal.GENERAL_FITNESS -> "ogólna sprawność (10-15 reps)"
        TrainingGoal.CARDIO_LIFTING -> "cardio + siłka (12-15 reps, supersets)"
    }

    private fun weightGoalLabel(g: WeightGoalType): String = when (g) {
        WeightGoalType.CUT -> "REDUKCJA tłuszczu (deficyt)"
        WeightGoalType.BULK -> "MASA mięśniowa (nadwyżka)"
        WeightGoalType.MAINTAIN -> "utrzymanie wagi"
        WeightGoalType.NONE -> "bez celu wagowego"
    }

    private fun activityLevelLabel(l: ActivityLevel): String = when (l) {
        ActivityLevel.SEDENTARY -> "siedzący tryb (×1.2)"
        ActivityLevel.LIGHT -> "lekko aktywny (×1.375)"
        ActivityLevel.MODERATE -> "umiarkowanie aktywny (×1.55)"
        ActivityLevel.VERY_ACTIVE -> "bardzo aktywny (×1.725)"
        ActivityLevel.EXTREME -> "ekstremalnie aktywny (×1.9)"
    }

    private fun dietPreferenceLabel(p: DietPreference): String = when (p) {
        DietPreference.STANDARD -> "standardowa"
        DietPreference.VEGETARIAN -> "wegetariańska"
        DietPreference.VEGAN -> "wegańska"
        DietPreference.PESCATARIAN -> "pescatariańska"
        DietPreference.KETO -> "ketogeniczna (max 30g węgli)"
        DietPreference.MEDITERRANEAN -> "śródziemnomorska"
    }
}
