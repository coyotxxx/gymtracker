package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.HydrationLog

@Dao
interface HydrationLogDao {

    @Query("SELECT * FROM hydration_logs WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY createdAt ASC")
    fun observeForDayRange(startMs: Long, endMs: Long): Flow<List<HydrationLog>>

    @Query("SELECT * FROM hydration_logs WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY createdAt ASC")
    suspend fun getForDayRange(startMs: Long, endMs: Long): List<HydrationLog>

    @Query("SELECT IFNULL(SUM(ml), 0) FROM hydration_logs WHERE dateMs >= :startMs AND dateMs < :endMs")
    suspend fun sumMlForRange(startMs: Long, endMs: Long): Int

    @Query("SELECT * FROM hydration_logs WHERE dateMs >= :sinceMs ORDER BY dateMs DESC")
    suspend fun getSince(sinceMs: Long): List<HydrationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HydrationLog): Long

    @Query("DELETE FROM hydration_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM hydration_logs")
    suspend fun deleteAll()
}
