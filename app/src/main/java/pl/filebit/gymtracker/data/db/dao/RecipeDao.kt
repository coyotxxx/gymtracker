package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.Recipe

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE mealType = :mealType ORDER BY isFavorite DESC, createdAt DESC")
    fun observeByMealType(mealType: MealType): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): Recipe?

    @Query("SELECT COUNT(*) FROM recipes WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT * FROM recipes WHERE source = :source ORDER BY name ASC")
    fun observeBySource(source: String): Flow<List<Recipe>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<Recipe>)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)
}
