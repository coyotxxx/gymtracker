package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealPrepActionType {
    BOIL,           // ugotuj
    BAKE,           // upiecz
    GRILL,          // ugrilluj
    PAN_FRY,        // usmaż na patelni
    CHOP,           // pokrój
    MIX,            // wymieszaj/zmiksuj
    PORTION,        // podziel na pojemniki
    STORE,          // schowaj/zamroź
    OTHER
}

@Entity(
    tableName = "meal_prep_plans",
    indices = [Index("fromDateMs"), Index("createdAt")]
)
data class MealPrepPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromDateMs: Long,
    val toDateMs: Long,
    /** Liczba pojemników do przygotowania. */
    val containersCount: Int = 0,
    /** Sumaryczny czas przygotowania w minutach. */
    val totalMinutes: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "meal_prep_steps",
    foreignKeys = [
        ForeignKey(
            entity = MealPrepPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class MealPrepStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val orderIdx: Int,
    val action: MealPrepActionType,
    val description: String,
    val estimatedMinutes: Int,
    /** Lista nazw produktów (CSV) — które produkty dotyczą tego kroku. */
    val productNames: String = "",
    val gramsTotal: Double = 0.0,
    val isCompleted: Boolean = false
)
