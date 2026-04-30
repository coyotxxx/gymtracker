package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GoalType {
    LOSE_WEIGHT,        // schudnij X kg
    GAIN_MASS,          // przybierz X kg masy mięśniowej
    IMPROVE_CARDIO,     // popraw kondycję — np. dystans biegu, czas
    INCREASE_STRENGTH,  // zwiększ 1RM dla konkretnego ćwiczenia
    CUSTOM              // dowolny — user definiuje opis i wartości
}

enum class GoalUnit {
    KG,            // dla LOSE_WEIGHT, GAIN_MASS, INCREASE_STRENGTH
    KM,            // dla IMPROVE_CARDIO (dystans)
    MINUTES,       // dla IMPROVE_CARDIO (czas)
    REPS,          // dla CUSTOM np. 100 pompek
    CUSTOM         // dowolne
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: GoalType,
    val title: String,                    // np. "Schudnąć 10 kg"
    val description: String = "",         // opcjonalnie kontekst
    val unit: GoalUnit = GoalUnit.KG,
    val startValue: Double,               // np. 95.0 (waga startowa)
    val targetValue: Double,              // np. 85.0 (waga docelowa)
    val currentValue: Double? = null,     // ostatnia odnotowana wartość; null = pobierz z auto-source
    val startDate: Long,                  // epoch ms
    val deadline: Long,                   // epoch ms
    val achieved: Boolean = false,
    val achievedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Dla INCREASE_STRENGTH: id ćwiczenia
     */
    val exerciseId: Long? = null
)
