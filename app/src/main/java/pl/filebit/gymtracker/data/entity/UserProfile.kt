package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrainingGoal {
    STRENGTH,           // 3-6 reps, ciężko
    HYPERTROPHY,        // 8-12 reps, masa
    MIX,                // siła + hipertrofia
    GENERAL_FITNESS,    // ogólna sprawność
    CARDIO_LIFTING      // cardio + siłka
}

enum class ExperienceLevel {
    BEGINNER, INTERMEDIATE, ADVANCED
}

enum class WeightUnit { KG, LB }

enum class Gender { MALE, FEMALE }

enum class WeightGoalType {
    NONE,       // user nie deklaruje celu
    CUT,        // redukcja
    BULK,       // masa
    MAINTAIN    // utrzymanie
}

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,                        // singleton row
    val displayName: String = "",                       // imię użytkownika ("Cześć, X" w Home)
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val preferredUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 120,
    val injuriesNotes: String = "",
    val showAdvancedSetFields: Boolean = false,
    val weightGoalType: WeightGoalType = WeightGoalType.NONE,
    val targetWeightKg: Double? = null,
    val unfinishedWorkoutNotifyEnabled: Boolean = true,
    val unfinishedWorkoutNotifyHours: Int = 3,
    val gender: Gender = Gender.MALE,
    val bodyweightKg: Double? = null,   // jeśli null → fallback na ostatni BodyMeasurement.weightKg
    val flashOnTimerEnd: Boolean = false,
    // v2.10.0: własny dźwięk końca przerwy. null = domyślne beepy. Inaczej URI
    // (systemowy dzwonek lub własny plik audio). Odtwarzane na strumieniu MEDIA,
    // więc idzie tam gdzie muzyka (słuchawki BT gdy podłączone, inaczej głośnik).
    val restSoundUri: String? = null,
    val aiOverlayEnabled: Boolean = false,  // pływający FAB Asystenta AI dostępny z każdego ekranu
    val onboardingCompleted: Boolean = false,  // true po przejściu wizard'a pierwszego uruchomienia (v0.86)
    val aiProactiveChecksEnabled: Boolean = false,  // codzienna notyfikacja od AI o regeneracji/stagnacji/bólu (v0.87)
    val aiAutoGenerateWeeklyReports: Boolean = true,  // v2.13.0: auto-raport tygodniowy w poniedziałek (domyślnie ON)
    /**
     * Dostępny sprzęt (CSV nazw Equipment enum).
     * Pusty = brak ograniczeń (pełna siłownia).
     * AI generator planu treningowego filtruje ćwiczenia po tym polu.
     */
    val availableEquipmentCsv: String = "",
    /**
     * v1.26.2 — preferowane grupy mięśniowe (CSV nazw MuscleGroup enum).
     * Pusty = brak preferencji (wszystkie partie). Używane do:
     *  - wizualnego "obszaru zainteresowania" w ustawieniach treningu
     *  - filtrowania kontekstu AI (v1.26.3: ~600 zamiast 1213 ćwiczeń)
     */
    val preferredMuscleGroupsCsv: String = "",
    /**
     * v1.26.5 — dostępny sprzęt jako praktyczne kategorie (CSV nazw
     * EquipmentCategory enum: BARBELL, EZ_BAR, DUMBBELL, KETTLEBELL, MACHINE,
     * CABLE, BODYWEIGHT, BANDS, BALL, CARDIO, OTHER_GEAR).
     * Pusty = brak ograniczeń (siłownia kompletna).
     * AI generator planu filtruje ćwiczenia po `Exercise.equipmentDbCsv`
     * (28 surowych typów z ExerciseDB) mapowanych przez EquipmentCategory.
     */
    val equipmentCategoriesCsv: String = "",

    // ======================================================================
    // === POLA DIETY (v1.28 — scalone z dawnej encji UserDietProfile) ======
    // Refaktor "jedno źródło prawdy": konfiguracja diety i treningu żyje
    // w jednej encji. Patrz docs/CONFIG-UNIFICATION-PLAN.md (Etap 1).
    // ======================================================================
    /** Wiek — potrzebny do dokładnego BMR Mifflin-St Jeor. */
    val ageYears: Int = 30,
    /** Wzrost w cm — BMR. */
    val heightCm: Int = 175,
    /** Poziom aktywności POZA treningiem (mnożnik TDEE). */
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    /** Średnie kroki/dzień — fallback NEAT gdy brak danych z urządzenia. */
    val avgStepsPerDay: Int = 7000,
    /** Cel dietetyczny — 8 wartości (FAT_LOSS/MUSCLE_GAIN/RECOMP/…). */
    val goalType: DietGoalType = DietGoalType.MAINTAIN,
    /** Tempo zmiany wagi kg/tydzień (zawsze dodatnie — kierunek z goalType). */
    val paceKgPerWeek: Double = 0.0,
    /** Wybrany przez usera deficyt/nadwyżka kcal (override automatu). */
    val customDeficitKcal: Int? = null,
    /** Styl diety (STANDARD/VEGETARIAN/VEGAN/…). */
    val dietPreference: DietPreference = DietPreference.STANDARD,
    /** CSV alergeny. */
    val allergies: String = "",
    /** CSV nietolerancje. */
    val intolerances: String = "",
    /** Produkty których user UNIKA (CSV) — AI omija. */
    val dislikedFoods: String = "",
    /** Ulubione produkty (CSV) — AI preferuje. */
    val lovedFoods: String = "",
    /** Czas gotowania na posiłek (min). */
    val cookingTimePerMealMin: Int = 15,
    val eatsAtWork: Boolean = false,
    val hasMicrowaveAtWork: Boolean = true,
    val mealPrepInterested: Boolean = false,
    val weeklyBudgetPln: Int? = null,
    /** CSV stany zdrowotne (medical flags). */
    val medicalConditions: String = "",
    /** True = user zaakceptował info "skonsultuj ze specjalistą". */
    val medicalAwareness: Boolean = false,
    /** Modalna godzina treningu (0-23) — do pre/post-WO matching. Null = nieznana. */
    val usualTrainingHour: Int? = null,
    /** Znacznik ukończenia onboardingu diety (osobny od onboardingCompleted treningu). */
    val dietOnboardingCompletedAt: Long? = null,
    /** Znacznik ostatniej zmiany pól diety. */
    val dietUpdatedAt: Long = System.currentTimeMillis()
)

// ======================================================================
// === JEDEN CEL (v1.28.1 — refaktor "jedno źródło prawdy", Etap 2) =====
// `goalType` (DietGoalType, 8 wartości) jest JEDYNYM celem aplikacji.
// `weightGoalType` (4 wartości) zostaje jako pole legacy — wielu konsumentów
// treningu wciąż je czyta — ale jest AUTO-NORMALIZOWANY z `goalType` przy
// każdym zapisie (UserProfileRepository.save). Nie da się ich rozjechać.
// Patrz docs/CONFIG-UNIFICATION-PLAN.md (Etap 2).
// ======================================================================

/** Kanoniczny cel (8 wart.) → kierunek wagi (4 wart., legacy mirror). */
fun DietGoalType.toWeightGoal(): WeightGoalType = when (this) {
    DietGoalType.FAT_LOSS, DietGoalType.EVENT_PREP -> WeightGoalType.CUT
    DietGoalType.MUSCLE_GAIN -> WeightGoalType.BULK
    DietGoalType.MAINTAIN, DietGoalType.RECOMP, DietGoalType.STRENGTH,
    DietGoalType.ENDURANCE, DietGoalType.HEALTH -> WeightGoalType.MAINTAIN
}

/** Kierunek wagi (legacy) → kanoniczny cel. Używane gdy stary kod ustawia cel. */
fun WeightGoalType.toDietGoal(): DietGoalType = when (this) {
    WeightGoalType.CUT -> DietGoalType.FAT_LOSS
    WeightGoalType.BULK -> DietGoalType.MUSCLE_GAIN
    WeightGoalType.MAINTAIN, WeightGoalType.NONE -> DietGoalType.MAINTAIN
}
