package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
    QUADS, HAMSTRINGS, GLUTES, CALVES,
    CORE, CARDIO, OTHER;

    /**
     * v1.19.0 — polski label grupy mięśniowej (centralny — zamiast duplikatów w wielu miejscach).
     */
    fun displayName(): String = when (this) {
        CHEST -> "Klatka"
        BACK -> "Plecy"
        SHOULDERS -> "Barki"
        BICEPS -> "Biceps"
        TRICEPS -> "Triceps"
        QUADS -> "Czworogłowe"
        HAMSTRINGS -> "Dwugłowe"
        GLUTES -> "Pośladki"
        CALVES -> "Łydki"
        CORE -> "Brzuch"
        CARDIO -> "Cardio"
        OTHER -> "Inne"
    }
}

enum class Equipment {
    BARBELL, DUMBBELLS, MACHINE, CABLE, BODYWEIGHT, OTHER;

    /** v1.20.2 — polski label sprzętu (centralizacja, zamiast duplikatów w UI screens). */
    fun displayName(): String = when (this) {
        BARBELL -> "Sztanga"
        DUMBBELLS -> "Hantle"
        MACHINE -> "Maszyna"
        CABLE -> "Wyciąg"
        BODYWEIGHT -> "Ciężar ciała"
        OTHER -> "Inne"
    }
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

@Entity(
    tableName = "exercises",
    indices = [
        // v1.20.0 — indices dla wyszukiwań w bibliotece ćwiczeń (Exercises screen, AI filter).
        androidx.room.Index("name"),
        androidx.room.Index("primaryMuscle"),
        androidx.room.Index("isFavorite")
    ]
)
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
    val isFavorite: Boolean = false,
    /** Ćwiczenie do unikania — AI nie wstawi go do nowego planu (np. boli kolano przy wykrokach). */
    val isAvoided: Boolean = false
)
