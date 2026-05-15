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
    val aiOverlayEnabled: Boolean = false,  // pływający FAB Asystenta AI dostępny z każdego ekranu
    val onboardingCompleted: Boolean = false,  // true po przejściu wizard'a pierwszego uruchomienia (v0.86)
    val aiProactiveChecksEnabled: Boolean = false,  // codzienna notyfikacja od AI o regeneracji/stagnacji/bólu (v0.87)
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
    val equipmentCategoriesCsv: String = ""
)
