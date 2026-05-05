package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.ShoppingList
import pl.filebit.gymtracker.data.entity.ShoppingListItem

@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): ShoppingList?

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShoppingList?

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY category ASC, productName ASC")
    fun observeItems(listId: Long): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY category ASC, productName ASC")
    suspend fun getItems(listId: Long): List<ShoppingListItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertList(list: ShoppingList): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<ShoppingListItem>)

    @Update
    suspend fun updateItem(item: ShoppingListItem)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceWithSingleList(list: ShoppingList, items: List<ShoppingListItem>): Long {
        // Trzymamy tylko 1 aktywną listę "current" (po nazwie)
        deleteAll()
        val id = insertList(list)
        insertItems(items.map { it.copy(listId = id) })
        return id
    }
}
