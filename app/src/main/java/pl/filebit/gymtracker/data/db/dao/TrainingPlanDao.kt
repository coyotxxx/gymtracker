package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.TrainingPlan

@Dao
interface TrainingPlanDao {

    @Query("SELECT * FROM training_plans ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrainingPlan>>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getById(id: Long): TrainingPlan?

    @Query("SELECT * FROM training_plans ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<TrainingPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: TrainingPlan): Long

    @Update
    suspend fun update(plan: TrainingPlan)

    @Delete
    suspend fun delete(plan: TrainingPlan)

    @Query("DELETE FROM training_plans WHERE id = :id")
    suspend fun deleteById(id: Long)
}
