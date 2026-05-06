package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
    QUADS, HAMSTRINGS, GLUTES, CALVES,
    CORE, CARDIO, OTHER
}

enum class Equipment {
    BARBELL, DUMBBELLS, MACHINE, CABLE, BODYWEIGHT, OTHER
}

/**
 * Typ metryki — co loguje user dla danego ćwiczenia.
 */
enum class MetricType {
    WEIGHT_REPS,        // klasyczne: kg + powt. (siłowe, większość)
    REPS_ONLY,          // tylko powt. (np. pompki BW, sit-up)
    DURATION,           // tylko czas (plank, deska, izometryczne)
    DISTANCE_DURATION,  // dystans + czas (bieżnia, rower, bieg)
    DURATION_WEIGHT     // czas + ciężar (farmer's walk, plank z obciążeniem)
}

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
    val notes: String = "",
    val metricType: MetricType = MetricType.WEIGHT_REPS,
    /** Edukacyjny opis ćwiczenia: czym jest, jak wykonać, na co uważać. */
    val description: String = "",
    /** Ulubione ćwiczenie usera — AI używa do priorytetyzacji w generowanym planie. */
    val isFavorite: Boolean = false
)
