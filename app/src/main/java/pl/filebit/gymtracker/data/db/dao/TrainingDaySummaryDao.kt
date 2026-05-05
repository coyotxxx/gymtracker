package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.TrainingDaySummary

@Dao
interface TrainingDaySummaryDao {

    @Query("SELECT * FROM training_day_summary WHERE dateMs = :dateMs LIMIT 1")
    suspend fun getForDate(dateMs: Long): TrainingDaySummary?

    @Query("SELECT * FROM training_day_summary WHERE dateMs = :dateMs LIMIT 1")
    fun observeForDate(dateMs: Long): Flow<TrainingDaySummary?>

    @Query("SELECT * FROM training_day_summary WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY dateMs ASC")
    suspend fun getRange(startMs: Long, endMs: Long): List<TrainingDaySummary>

    @Query("SELECT * FROM training_day_summary ORDER BY dateMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<TrainingDaySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: TrainingDaySummary)

    @Query("DELETE FROM training_day_summary WHERE dateMs = :dateMs")
    suspend fun deleteForDate(dateMs: Long)
}
