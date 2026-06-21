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
 * Status konsumpcji per slot per dzień. Klucz logiczny: (dateMs, mealSlot).
 * v2.73.0 (POSIŁKI N): kluczowane po NUMERZE slotu (1..N), nie po mealType —
 * dzięki temu każdy posiłek (także wiele przekąsek) ma niezależny status
 * „zjedzone/pominięte". Wcześniej (dateMs, mealType) powodował kolizję slotów.
 */
@Entity(
    tableName = "meal_consumptions",
    indices = [Index(value = ["dateMs", "mealSlot"], unique = true)]
)
data class MealConsumption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia 00:00 lokalny. */
    val dateMs: Long,
    /** Numer slotu posiłku w dniu (1..N). */
    val mealSlot: Int,
    val status: MealConsumptionStatus,
    val notedAt: Long = System.currentTimeMillis()
)
