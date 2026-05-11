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
@Entity(
    tableName = "food_products",
    indices = [
        // v1.20.0 — index dla wyszukiwania po nazwie (DietScreen search).
        androidx.room.Index("name"),
        androidx.room.Index("category"),
        androidx.room.Index("isFavorite"),
        androidx.room.Index("barcode")
    ]
)
data class FoodProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: FoodCategory,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    // === Quality fields (v0.90) — wszystkie default 0.0, można uzupełnić w czasie ===
    /** Błonnik w gramach na 100g. Cel dzienny: 25-35g. */
    val fiberPer100g: Double = 0.0,
    /** Wapń w mg na 100g. Cel dzienny: 1000-1300mg. */
    val calciumMgPer100g: Double = 0.0,
    /** Potas w mg na 100g. Cel dzienny: 3500mg. */
    val potassiumMgPer100g: Double = 0.0,
    /** Sód w mg na 100g. Max dzienny: 2300mg. */
    val sodiumMgPer100g: Double = 0.0,
    /** false = z seed-u, true = user dodał. */
    val isCustom: Boolean = false,
    /** Notatka usera (np. "z bistro", "w Lidl"). */
    val notes: String = "",
    /** Kod kreskowy EAN-13 lub inny — używany przy skanowaniu. */
    val barcode: String? = null,
    /** Marka/producent (np. "Biedronka", "Lidl"). */
    val brand: String? = null,
    /** Źródło danych: "seed" / "manual" / "openfoodfacts". */
    val source: String = "manual",
    /** Ulubiony produkt — AI używa do priorytetyzacji w generowanym planie. */
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
