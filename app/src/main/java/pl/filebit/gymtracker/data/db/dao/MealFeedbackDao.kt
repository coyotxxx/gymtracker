package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealFeedback

@Dao
interface MealFeedbackDao {

    @Query("SELECT * FROM meal_feedback WHERE dishKey = :key LIMIT 1")
    suspend fun getByKey(key: String): MealFeedback?

    @Query("SELECT * FROM meal_feedback ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<MealFeedback>

    @Query("SELECT * FROM meal_feedback ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MealFeedback>>

    @Query("SELECT * FROM meal_feedback WHERE rating >= :minRating ORDER BY rating DESC, timesEaten DESC LIMIT :limit")
    suspend fun getTopFavorites(minRating: Int = 4, limit: Int = 10): List<MealFeedback>

    @Query("SELECT * FROM meal_feedback WHERE rating <= :maxRating ORDER BY rating ASC, updatedAt DESC LIMIT :limit")
    suspend fun getTopDisliked(maxRating: Int = 2, limit: Int = 5): List<MealFeedback>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feedback: MealFeedback): Long

    @Update
    suspend fun update(feedback: MealFeedback)

    @Query("DELETE FROM meal_feedback WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM meal_feedback")
    suspend fun deleteAll()
}
