package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("planId"), Index("exerciseId")]
)
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: Long,
    val dayOfWeek: Int = 1,         // 1=Pon ... 7=Nd ISO
    val orderIndex: Int
    // Konkretne serie (powt/waga/odp) trzymane są w tabeli plan_exercise_sets
    // jako lista PlanExerciseSet (każda seria osobno edytowalna).
)
