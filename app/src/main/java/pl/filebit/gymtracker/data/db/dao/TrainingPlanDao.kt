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

    // === v1.24.12: isActive — jeden user, jeden aktywny plan ===

    @Query("SELECT * FROM training_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): TrainingPlan?

    @Query("SELECT * FROM training_plans WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<TrainingPlan?>

    @Query("UPDATE training_plans SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE training_plans SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)
}
