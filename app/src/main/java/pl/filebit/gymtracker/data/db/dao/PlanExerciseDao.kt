package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.PlanExercise

@Dao
interface PlanExerciseDao {

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    fun observeForPlan(planId: Long): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getForPlan(planId: Long): List<PlanExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pe: PlanExercise): Long

    @Update
    suspend fun update(pe: PlanExercise)

    @Delete
    suspend fun delete(pe: PlanExercise)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deleteAllForPlan(planId: Long)

    @Query("SELECT MAX(orderIndex) FROM plan_exercises WHERE planId = :planId")
    suspend fun getMaxOrderIndex(planId: Long): Int?
}
