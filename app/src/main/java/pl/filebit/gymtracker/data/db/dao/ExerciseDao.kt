package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE primaryMuscle = :muscle ORDER BY name COLLATE NOCASE ASC")
    fun observeByMuscle(muscle: MuscleGroup): Flow<List<Exercise>>

    @Query("""
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun search(query: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT LOWER(name) FROM exercises")
    suspend fun allNamesLower(): List<String>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFavorites(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<Exercise>>

    @Query("UPDATE exercises SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE exercises SET isFavorite = 1 WHERE LOWER(name) = LOWER(:name)")
    suspend fun markFavoriteByName(name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: Exercise): Long
}
