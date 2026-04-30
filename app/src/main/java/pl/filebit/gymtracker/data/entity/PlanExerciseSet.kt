package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = PlanExercise::class,
            parentColumns = ["id"],
            childColumns = ["planExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planExerciseId")]
)
data class PlanExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planExerciseId: Long,
    val setNumber: Int,                 // 1, 2, 3...
    val reps: Int = 8,
    val weightKg: Double? = null,
    val restSeconds: Int? = null,
    val setType: SetType = SetType.NORMAL,
    val rpe: Int? = null,
    val rir: Int? = null,
    val tempo: String? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null
)
