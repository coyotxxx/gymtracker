package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_plans")
data class TrainingPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysOfWeek: List<Int> = emptyList(), // 1=Pon ... 7=Nd ISO
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdByAi: Boolean = false,
    /** v1.24.12: filozofia "jeden user, jeden aktywny plan". Tylko jeden plan
     *  w bazie ma `isActive=true` w danej chwili. Home/schedule używają go
     *  jako domyślnego, gdy user nie ma trwającego workoutu z innego planu. */
    val isActive: Boolean = false
)
