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

enum class WorkoutContext {
    NORMAL,       // posiłek niezwiązany z treningiem
    PRE_WORKOUT,  // 1-2h przed treningiem (szybkie węgle, średnie białko)
    POST_WORKOUT  // do 1h po treningu (białko + szybkie węgle)
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
    /**
     * v2.73.0 (POSIŁKI N): numer slotu posiłku w dniu (1..N) — TOŻSAMOŚĆ posiłku.
     * `mealType` pozostaje jako TAG (kontekst trening / dopasowanie przepisu / godziny).
     * Grupowanie/identyfikacja posiłku idą po `mealSlot`, nie po `mealType`.
     */
    val mealSlot: Int = 1,
    val productId: Long,
    val grams: Double,
    val notes: String = "",
    /** Kontekst treningowy posiłku (PRE/POST/NORMAL). Default NORMAL. */
    val workoutContext: WorkoutContext = WorkoutContext.NORMAL,
    /**
     * v2.11.0: czy wpis pochodzi z PLANU AI (true) czy z ręcznego dziennika (false).
     * Plan AI liczy się do adherence dopiero po jawnym potwierdzeniu (MealConsumption=CONSUMED).
     * Wpis ręczny (dodany produkt, zdjęcie, przepis) liczy się jako zjedzony, chyba że SKIPPED.
     */
    val isPlanned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
