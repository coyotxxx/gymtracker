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

enum class WeightGoalType {
    NONE,       // user nie deklaruje celu
    CUT,        // redukcja
    BULK,       // masa
    MAINTAIN    // utrzymanie
}

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,                        // singleton row
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
    val unfinishedWorkoutNotifyHours: Int = 3
)
