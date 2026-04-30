package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.Goal

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY achieved ASC, deadline ASC")
    fun observeAll(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY achieved ASC, deadline ASC")
    suspend fun getAll(): List<Goal>

    @Query("SELECT * FROM goals WHERE achieved = 0 ORDER BY deadline ASC")
    suspend fun getActive(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: Goal): Long

    @Delete
    suspend fun delete(goal: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)
}
