package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lista zakupów wygenerowana z planu posiłków na zakres dat.
 */
@Entity(
    tableName = "shopping_lists",
    indices = [Index("fromDateMs"), Index("createdAt")]
)
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromDateMs: Long,
    val toDateMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Pozycja na liście zakupów — produkt × suma gramatur z zakresu dat.
 */
@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FoodProduct::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("listId"), Index("productId")]
)
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val productId: Long,
    /** Snapshot nazwy/kategorii — gdyby produkt został usunięty, lista pozostaje czytelna. */
    val productName: String,
    val category: FoodCategory,
    val grams: Double,
    /** Liczba dni w których ten produkt się pojawia w planie (np. 5/7). */
    val occurrences: Int = 1,
    val isPurchased: Boolean = false,
    val customNote: String = ""
)
