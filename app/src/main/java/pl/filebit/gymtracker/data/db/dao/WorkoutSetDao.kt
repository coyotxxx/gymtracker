package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.WorkoutSet

@Dao
interface WorkoutSetDao {

    @Query("""
        SELECT * FROM workout_sets
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC, setNumber ASC
    """)
    fun observeForWorkout(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("""
        SELECT * FROM workout_sets
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC, setNumber ASC
    """)
    suspend fun getForWorkout(workoutId: Long): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSet?

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId")
    suspend fun getAllForExercise(exerciseId: Long): List<WorkoutSet>

    /**
     * Ostatnia (zakończona) seria danego ćwiczenia — używana do auto-fill
     * "ostatni raz robiłeś X kg × Y reps".
     */
    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workouts w ON w.id = ws.workoutId
        WHERE ws.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL
        ORDER BY ws.createdAt DESC
        LIMIT 1
    """)
    suspend fun getLastSetForExercise(exerciseId: Long): WorkoutSet?

    @Query("""
        SELECT MAX(setNumber) FROM workout_sets
        WHERE workoutId = :workoutId AND exerciseId = :exerciseId
    """)
    suspend fun getMaxSetNumber(workoutId: Long, exerciseId: Long): Int?

    @Query("""
        SELECT MAX(orderIndex) FROM workout_sets
        WHERE workoutId = :workoutId
    """)
    suspend fun getMaxOrderIndex(workoutId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: WorkoutSet): Long

    @Update
    suspend fun update(set: WorkoutSet)

    @Delete
    suspend fun delete(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteAllForExerciseInWorkout(workoutId: Long, exerciseId: Long)
}
