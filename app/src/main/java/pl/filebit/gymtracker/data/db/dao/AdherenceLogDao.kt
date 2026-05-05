package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.AdherenceLog

@Dao
interface AdherenceLogDao {

    @Query("SELECT * FROM adherence_log WHERE dateMs = :dateMs LIMIT 1")
    suspend fun getForDate(dateMs: Long): AdherenceLog?

    @Query("SELECT * FROM adherence_log WHERE dateMs = :dateMs LIMIT 1")
    fun observeForDate(dateMs: Long): Flow<AdherenceLog?>

    @Query("SELECT * FROM adherence_log WHERE dateMs >= :startMs AND dateMs < :endMs ORDER BY dateMs ASC")
    suspend fun getRange(startMs: Long, endMs: Long): List<AdherenceLog>

    @Query("SELECT * FROM adherence_log ORDER BY dateMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<AdherenceLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: AdherenceLog)

    @Query("DELETE FROM adherence_log WHERE dateMs = :dateMs")
    suspend fun deleteForDate(dateMs: Long)
}
