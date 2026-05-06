package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct

@Dao
interface FoodProductDao {

    @Query("SELECT * FROM food_products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FoodProduct>>

    @Query("SELECT * FROM food_products WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    fun observeByCategory(category: FoodCategory): Flow<List<FoodProduct>>

    @Query("""
        SELECT * FROM food_products
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun search(query: String): Flow<List<FoodProduct>>

    @Query("SELECT * FROM food_products ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<FoodProduct>

    @Query("SELECT * FROM food_products WHERE id = :id")
    suspend fun getById(id: Long): FoodProduct?

    @Query("SELECT * FROM food_products WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FoodProduct>

    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodProduct?

    @Query("SELECT * FROM food_products WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFavorites(): List<FoodProduct>

    @Query("UPDATE food_products SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("SELECT COUNT(*) FROM food_products")
    suspend fun count(): Int

    @Query("SELECT LOWER(name) FROM food_products")
    suspend fun allNamesLower(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(products: List<FoodProduct>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: FoodProduct): Long

    @Query("DELETE FROM food_products WHERE id = :id")
    suspend fun delete(id: Long)
}
