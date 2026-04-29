package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pl.filebit.gymtracker.data.entity.PlanExerciseSet

@Dao
interface PlanExerciseSetDao {

    @Query("SELECT * FROM plan_exercise_sets WHERE planExerciseId = :planExerciseId ORDER BY setNumber ASC")
    suspend fun getForPlanExercise(planExerciseId: Long): List<PlanExerciseSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(set: PlanExerciseSet): Long

    @Update
    suspend fun update(set: PlanExerciseSet)

    @Delete
    suspend fun delete(set: PlanExerciseSet)

    @Query("DELETE FROM plan_exercise_sets WHERE planExerciseId = :planExerciseId")
    suspend fun deleteAllForPlanExercise(planExerciseId: Long)
}
