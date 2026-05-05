package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Codzienny zapis ZGODNOŚCI z planem dietetycznym i treningowym.
 *
 * Zgodność liczona w %:
 * - 90-110% trafienia celu = 100% adherence
 * - 70-89% lub 111-130% = warning
 * - <70% lub >130% = brak zgodności
 *
 * AdherenceCalculator wylicza ten zapis:
 * - Real-time po każdej zmianie MealEntry
 * - 23:55 codziennie (PeriodicWorker — TODO v0.89.45)
 *
 * Silnik regułowy CalorieAdjustmentEngine (v0.89.43) używa AdherenceLog
 * do decyzji "obniżyć kalorie czy NIE". Klucz: niska zgodność ≠ obniżać kcal,
 * najpierw uprościć plan.
 */
@Entity(
    tableName = "adherence_log",
    indices = [Index(value = ["dateMs"], unique = true)]
)
data class AdherenceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,                   // start dnia 00:00 lokalnie

    // === DIETA ===
    /** Cel kcal na ten dzień (z DailyMacroGoal lub override). */
    val targetKcal: Int,
    /** Faktycznie zjedzone kcal (suma MealEntry × kcalPer100g/100 × grams). */
    val actualKcal: Int,
    /** % trafienia (0-200). 100 = idealnie. */
    val kcalAdherencePct: Int,

    val targetProteinG: Int,
    val actualProteinG: Int,
    val proteinAdherencePct: Int,

    val targetCarbsG: Int,
    val actualCarbsG: Int,
    val carbsAdherencePct: Int,

    val targetFatG: Int,
    val actualFatG: Int,
    val fatAdherencePct: Int,

    /** Liczba posiłków zalogowanych. */
    val mealsLoggedCount: Int = 0,
    /** Liczba zaplanowanych slotów. */
    val mealsPlannedCount: Int = 0,

    // === TRENING ===
    /** True gdy dzień planowany jako treningowy. */
    val wasTrainingPlanned: Boolean = false,
    /** True gdy trening wykonano (z TrainingDaySummary.isTrainingDay). */
    val wasTrainingDone: Boolean = false,

    val notes: String = "",
    val computedAt: Long = System.currentTimeMillis()
) {
    /** Czy dzień to "high adherence" (≥85% kcal i białka, max 130%). */
    val isHighAdherence: Boolean
        get() = kcalAdherencePct in 85..130 && proteinAdherencePct in 80..150

    /** Ogólny score 0-100 (średnia ważona kcal+białko). */
    val overallScore: Int
        get() {
            val kcalScore = if (kcalAdherencePct > 100) (200 - kcalAdherencePct).coerceIn(0, 100)
                else kcalAdherencePct
            val proteinScore = if (proteinAdherencePct > 100) (200 - proteinAdherencePct).coerceIn(0, 100)
                else proteinAdherencePct
            // Białko ważniejsze
            return ((kcalScore * 0.4 + proteinScore * 0.6).toInt()).coerceIn(0, 100)
        }
}
