package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("workoutId"),
        Index("exerciseId"),
        Index(value = ["workoutId", "exerciseId", "setNumber"])
    ]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,                 // 1, 2, 3... w obrębie tego ćwiczenia
    val orderIndex: Int,                // kolejność dodania ćwiczenia w treningu (do grupowania)
    val reps: Int,
    val weightKg: Double,
    val isCompleted: Boolean = true,
    val setType: SetType = SetType.NORMAL,
    val rpe: Int? = null,               // 1-10, opcjonalne
    val rir: Int? = null,               // reps in reserve, opcjonalne
    val tempo: String? = null,          // np. "3-1-1-0" (eksc/pauza/konc/pauza)
    val createdAt: Long = System.currentTimeMillis()
) {
    val isWarmup: Boolean get() = setType == SetType.WARMUP
}
