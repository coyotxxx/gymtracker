package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealConsumptionStatus {
    PLANNED,    // domyślnie — slot z entries ale nie potwierdzony
    CONSUMED,   // user oznaczył jako zjedzony
    SKIPPED     // user pominął posiłek
}

/**
 * Status konsumpcji per slot per dzień. Klucz logiczny: (dateMs, mealType).
 * Dzięki temu user może explicit oznaczyć "zjedzone"/"pominięte" niezależnie od heurystyki.
 */
@Entity(
    tableName = "meal_consumptions",
    indices = [Index(value = ["dateMs", "mealType"], unique = true)]
)
data class MealConsumption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia 00:00 lokalny. */
    val dateMs: Long,
    val mealType: MealType,
    val status: MealConsumptionStatus,
    val notedAt: Long = System.currentTimeMillis()
)
