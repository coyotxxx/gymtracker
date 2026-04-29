package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,            // epoch millis
    val finishedAt: Long? = null,   // null = trening w toku
    val fromPlanId: Long? = null,   // null = ad-hoc, inaczej id planu z którego wystartowano
    val fromDayOfWeek: Int? = null, // dzień planu z którego startowano (1=Pon..7=Nd)
    val notes: String = ""
) {
    val isActive: Boolean get() = finishedAt == null
    val durationMillis: Long get() = (finishedAt ?: System.currentTimeMillis()) - startedAt
}
