package pl.filebit.gymtracker.data.entity

enum class ActivityLevel {
    SEDENTARY,      // siedzący tryb (biuro, mało ruchu) — ×1.2
    LIGHT,          // lekko aktywny (chodzenie, lekkie prace) — ×1.375
    MODERATE,       // umiarkowanie aktywny (3-5 treningów + sporo chodzenia) — ×1.55
    VERY_ACTIVE,    // bardzo aktywny (6-7 treningów + ciężka praca fizyczna) — ×1.725
    EXTREME         // ekstremalnie aktywny (zawodowy sportowiec) — ×1.9
}

enum class DietPreference {
    STANDARD,
    VEGETARIAN,
    VEGAN,
    PESCATARIAN,    // ryby + nabiał, bez mięsa
    KETO,           // bardzo niskowęglowodanowa
    MEDITERRANEAN
}

enum class DietGoalType {
    FAT_LOSS,           // redukcja tkanki tłuszczowej
    MUSCLE_GAIN,        // budowa masy mięśniowej
    RECOMP,             // rekompozycja sylwetki (waga stała, mniej tłuszczu/więcej mięśni)
    MAINTAIN,           // utrzymanie masy
    STRENGTH,           // poprawa siły
    ENDURANCE,          // poprawa wydolności
    HEALTH,             // poprawa zdrowia (cholesterol/cukier)
    EVENT_PREP          // przygotowanie do konkretnego terminu/zawodów
}

/**
 * Widok pól diety profilu użytkownika.
 *
 * v1.28 (refaktor "jedno źródło prawdy", Etap 1): to JUŻ NIE jest encja Room —
 * dane fizycznie żyją w scalonej encji [UserProfile] / tabeli `user_profile`.
 * Ten data class zostaje jako DTO/widok: `UserDietProfileRepository` mapuje go
 * w obie strony na `UserProfile`, dzięki czemu konsumenci (DietViewModel,
 * DietAiService, backup itd.) działają bez zmian. Patrz docs/CONFIG-UNIFICATION-PLAN.md.
 */
data class UserDietProfile(
    val id: Int = 1,

    // === Krok 1: Dane podstawowe (potrzebne do dokładnego BMR Mifflin-St Jeor) ===
    val ageYears: Int = 30,
    val heightCm: Int = 175,

    // === Krok 2: Aktywność poza treningiem ===
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val avgStepsPerDay: Int = 7000,         // szacowanie / z urządzenia (PRO)

    // === Krok 3: Cel dietetyczny + tempo ===
    val goalType: DietGoalType = DietGoalType.MAINTAIN,
    /** Tempo zmiany wagi w kg/tydzień (CUT: -0.3..-1.0, BULK: +0.2..+0.5, RECOMP: 0). */
    val paceKgPerWeek: Double = 0.0,
    /** Wybrany przez usera deficyt/nadwyżka kcal (override automatu). */
    val customDeficitKcal: Int? = null,

    // === Krok 4: Preferencje + ograniczenia ===
    val dietPreference: DietPreference = DietPreference.STANDARD,
    /** CSV alergeny — laktoza/gluten/jaja/orzechy/soja/ryby/owoce_morza/sezam/gorczyca/inne. */
    val allergies: String = "",
    val intolerances: String = "",
    /** Produkty których user UNIKA (CSV) — AI omija przy generowaniu. */
    val dislikedFoods: String = "",
    /** Ulubione produkty (CSV) — AI preferuje przy zamiennikach. */
    val lovedFoods: String = "",

    // === Krok 5: Praktyczne (wykonalność diety) ===
    val cookingTimePerMealMin: Int = 15,
    val eatsAtWork: Boolean = false,
    val hasMicrowaveAtWork: Boolean = true,
    val mealPrepInterested: Boolean = false,
    val weeklyBudgetPln: Int? = null,

    // === Krok 6: Zdrowie (medical flags — wymaga konsultacji) ===
    /** CSV — diabetes/kidney/liver/heart/hypertension/pregnancy/breastfeeding/eating_disorder. */
    val medicalConditions: String = "",
    /** True = user zaakceptował info "skonsultuj ze specjalistą". */
    val medicalAwareness: Boolean = false,

    // === Trening: typowa godzina (do pre/post-WO matching) ===
    /** Modalna godzina treningu (0-23) — wykrywana z historii lub z onboardingu. Null = nie wiadomo. */
    val usualTrainingHour: Int? = null,

    // === Stan onboardingu ===
    val onboardingCompletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isOnboardingDone: Boolean get() = onboardingCompletedAt != null

    fun parsedAllergies(): List<String> =
        allergies.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun parsedDislikedFoods(): List<String> =
        dislikedFoods.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun parsedLovedFoods(): List<String> =
        lovedFoods.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun parsedMedicalConditions(): List<String> =
        medicalConditions.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
