package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.DietAdjustment

@Dao
interface DietAdjustmentDao {

    @Query("SELECT * FROM diet_adjustments ORDER BY dateMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<DietAdjustment>>

    @Query("SELECT * FROM diet_adjustments ORDER BY dateMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<DietAdjustment>

    @Query("SELECT * FROM diet_adjustments WHERE id = :id")
    suspend fun getById(id: Long): DietAdjustment?

    @Query("SELECT * FROM diet_adjustments WHERE applied = 1 ORDER BY dateMs DESC LIMIT 1")
    suspend fun getLatestApplied(): DietAdjustment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adj: DietAdjustment): Long

    @Update
    suspend fun update(adj: DietAdjustment)

    @Query("DELETE FROM diet_adjustments WHERE id = :id")
    suspend fun delete(id: Long)
}
