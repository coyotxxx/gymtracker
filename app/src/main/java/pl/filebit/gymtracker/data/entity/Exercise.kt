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

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
    val notes: String = ""
)
