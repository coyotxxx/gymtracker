package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType {
    BREAKFAST,    // śniadanie
    LUNCH,        // obiad
    DINNER,       // kolacja
    SNACK         // przekąska (po treningu, drugie śniadanie)
}

/**
 * Pojedynczy wpis spożycia: produkt × gramatura w danym posiłku danego dnia.
 * Z dnia agregujemy kcal+makro.
 */
@Entity(
    tableName = "meal_entries",
    foreignKeys = [
        ForeignKey(
            entity = FoodProduct::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("productId"), Index("dateMs")]
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Data spożycia (start dnia w epoch ms). Bierzemy daty bez godziny. */
    val dateMs: Long,
    val mealType: MealType,
    val productId: Long,
    val grams: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
