package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Przepis — szybki i prosty. Może być AI-generowany (na żądanie z dostępnych
 * produktów) albo wpisany ręcznie przez usera. Po użyciu można dodać do
 * dziennika 1-tap (każdy składnik → MealEntry).
 *
 * Składniki w JSON: [{"productId":1,"grams":150},{"productId":7,"grams":80}].
 * JSON żeby uniknąć trzeciej tabeli (RecipeIngredient) — przepis i tak edytujemy
 * jako całość, nie per składnik.
 */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: MealType,
    /** JSON: [{"productId":Long,"grams":Double}, ...]. */
    val ingredientsJson: String,
    /** Krok po kroku, każdy krok w nowej linii. */
    val instructions: String,
    val prepMinutes: Int = 15,
    /** Wyliczone z składników * kcalPer100g. Cache (do wymiany przy edycji). */
    val kcalPerServing: Int = 0,
    val proteinPerServing: Int = 0,
    val carbsPerServing: Int = 0,
    val fatPerServing: Int = 0,
    /** true = wygenerowane przez AI, false = user wpisał. */
    val isAiGenerated: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
