package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.DailyActivityLog

@Dao
interface DailyActivityLogDao {

    @Query("SELECT * FROM daily_activity_logs WHERE dateMs = :dateMs LIMIT 1")
    suspend fun getForDate(dateMs: Long): DailyActivityLog?

    @Query("SELECT * FROM daily_activity_logs WHERE dateMs = :dateMs LIMIT 1")
    fun observeForDate(dateMs: Long): Flow<DailyActivityLog?>

    @Query("SELECT * FROM daily_activity_logs WHERE dateMs >= :startMs ORDER BY dateMs DESC LIMIT :limit")
    suspend fun getSince(startMs: Long, limit: Int = 60): List<DailyActivityLog>

    @Query("SELECT IFNULL(AVG(steps), 0) FROM daily_activity_logs WHERE dateMs >= :startMs AND dateMs < :endMs")
    suspend fun avgStepsForRange(startMs: Long, endMs: Long): Double

    @Query("SELECT * FROM daily_activity_logs ORDER BY dateMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<DailyActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DailyActivityLog): Long

    @Update
    suspend fun update(log: DailyActivityLog)

    @Query("DELETE FROM daily_activity_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM daily_activity_logs")
    suspend fun deleteAll()
}
