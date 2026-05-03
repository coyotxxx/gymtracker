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
    val notes: String = "",
    val aiSummary: String? = null,             // motywujące podsumowanie wygenerowane przez AI
    val aiSummaryGeneratedAt: Long? = null,    // kiedy zostało wygenerowane (epoch ms)
    // Post-workout feedback (v0.82.0)
    val wellbeingRating: Int? = null,          // 1-5 — jak się czułeś (5 = świetnie, 1 = źle)
    val painArea: String? = null,              // partia gdzie odczuwał ból (MuscleGroup.name) lub null
    val painNotes: String? = null              // krótki opis dolegliwości
) {
    val isActive: Boolean get() = finishedAt == null
    val durationMillis: Long get() = (finishedAt ?: System.currentTimeMillis()) - startedAt
}
