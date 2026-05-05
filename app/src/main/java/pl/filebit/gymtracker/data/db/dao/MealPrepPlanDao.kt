package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.MealPrepPlan
import pl.filebit.gymtracker.data.entity.MealPrepStep

@Dao
interface MealPrepPlanDao {

    @Query("SELECT * FROM meal_prep_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MealPrepPlan>>

    @Query("SELECT * FROM meal_prep_plans ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): MealPrepPlan?

    @Query("SELECT * FROM meal_prep_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealPrepPlan?

    @Query("SELECT * FROM meal_prep_steps WHERE planId = :planId ORDER BY orderIdx ASC")
    fun observeSteps(planId: Long): Flow<List<MealPrepStep>>

    @Query("SELECT * FROM meal_prep_steps WHERE planId = :planId ORDER BY orderIdx ASC")
    suspend fun getSteps(planId: Long): List<MealPrepStep>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: MealPrepPlan): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSteps(steps: List<MealPrepStep>)

    @Update
    suspend fun updateStep(step: MealPrepStep)

    @Query("DELETE FROM meal_prep_plans")
    suspend fun deleteAll()

    @Query("DELETE FROM meal_prep_plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Transaction
    suspend fun replaceWithSinglePlan(plan: MealPrepPlan, steps: List<MealPrepStep>): Long {
        deleteAll()
        val id = insertPlan(plan)
        insertSteps(steps.map { it.copy(planId = id) })
        return id
    }
}
