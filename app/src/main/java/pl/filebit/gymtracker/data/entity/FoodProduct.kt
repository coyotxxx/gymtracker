package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FoodCategory {
    CARBS,      // Węglowodany: ryż, makaron, kasze, pieczywo, bataty
    PROTEIN,    // Białko: kurczak, indyk, twaróg, jaja, ryby, tofu
    FAT,        // Tłuszcze: orzechy, awokado, oliwa, masło orzechowe
    VEGETABLE,  // Warzywa: brokuły, pomidor, ogórek (zwykle bez limitu)
    DAIRY,      // Mleko, jogurt, serek wiejski (osobne bo białko + węgle)
    FRUIT,      // Owoce (na bulk lub niedozwolone na redukcji)
    OTHER
}

/**
 * Produkt spożywczy — pozycja na liście. Makro per 100g.
 * Seed startowy ~50 z xlsx Macieja + USDA/typowe wartości.
 */
@Entity(tableName = "food_products")
data class FoodProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: FoodCategory,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    /** false = z seed-u, true = user dodał. */
    val isCustom: Boolean = false,
    /** Notatka usera (np. "z bistro", "w Lidl"). */
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
