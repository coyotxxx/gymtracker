package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TrainingType {
    STRENGTH,       // siłowy 3-6 reps, ciężki
    HYPERTROPHY,    // 8-12 reps, masa
    MIXED,          // siła + masa
    CARDIO,         // kardio
    HIIT,           // interwały
    DELOAD,         // tydzień regeneracyjny
    REST,           // dzień wolny
    SKIPPED         // pominięty trening planowany
}

enum class IntensityScore {
    LIGHT,          // lekki — RPE <7 lub mała objętość
    MEDIUM,         // średni — RPE 7-8.5
    HEAVY           // ciężki — RPE 9+ lub duża objętość
}

enum class PerformanceTrend {
    PROGRESS,       // siła rośnie / volumen rośnie
    STAGNATION,     // bez zmian 2+ sesje
    REGRESS         // siła spada / niedokończone reps
}

/**
 * Dzienne podsumowanie treningu — most między modułem TRENING a DIETA.
 *
 * Wyliczane przez TrainingDietBridge:
 * - po Workout.finish() (real-time update)
 * - codziennie 00:01 (ensureForDate dla planowanych ale nie wykonanych)
 *
 * DietAiService czyta z tej tabeli żeby decydować o makro
 * (więcej węgli w dni HEAVY, mniej w REST).
 */
@Entity(
    tableName = "training_day_summary",
    indices = [Index(value = ["dateMs"], unique = true)]
)
data class TrainingDaySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia w epoch ms (00:00 lokalny). Unikalne. */
    val dateMs: Long,
    val isTrainingDay: Boolean = false,
    /** ID workoutu jeśli wykonano (nullable gdy planowany ale skipped). */
    val workoutId: Long? = null,
    val trainingType: TrainingType = TrainingType.REST,
    val startTimeMs: Long? = null,
    val durationMinutes: Int = 0,
    /** CSV partii mięśniowych (np. "CHEST,TRICEPS,SHOULDERS"). */
    val trainedMuscleGroupsCsv: String = "",
    val plannedVolumeKg: Double = 0.0,
    val completedVolumeKg: Double = 0.0,
    val intensityScore: IntensityScore = IntensityScore.LIGHT,
    val rpeAverage: Double? = null,
    val rirAverage: Double? = null,
    val workingSetsCount: Int = 0,
    val repsTotal: Int = 0,
    val cardioMinutes: Int = 0,
    val cardioIntensity: IntensityScore? = null,
    /** % planu wykonany. 0=skipped, 100=wszystko, <100=skrócony. */
    val workoutCompletedPct: Int = 0,
    val performanceTrend: PerformanceTrend = PerformanceTrend.PROGRESS,
    /** 1-10. Z DeloadDetector + RPE × frekwencja ostatnich dni. */
    val fatigueScore: Int = 5,
    val computedAt: Long = System.currentTimeMillis()
) {
    fun parsedMuscleGroups(): List<String> =
        trainedMuscleGroupsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
