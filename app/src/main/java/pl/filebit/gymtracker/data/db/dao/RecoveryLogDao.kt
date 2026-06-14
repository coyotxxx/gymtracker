package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.RecoveryLog

@Dao
interface RecoveryLogDao {

    @Query("SELECT * FROM recovery_logs WHERE dateMs = :dateMs LIMIT 1")
    suspend fun getForDate(dateMs: Long): RecoveryLog?

    @Query("SELECT * FROM recovery_logs WHERE dateMs = :dateMs LIMIT 1")
    fun observeForDate(dateMs: Long): Flow<RecoveryLog?>

    @Query("SELECT * FROM recovery_logs WHERE dateMs >= :startMs ORDER BY dateMs DESC LIMIT :limit")
    suspend fun getSince(startMs: Long, limit: Int = 30): List<RecoveryLog>

    /** Najnowszy log w CAŁEJ historii (do liczenia świeżości — nie tylko okno 28d). */
    @Query("SELECT * FROM recovery_logs ORDER BY dateMs DESC LIMIT 1")
    suspend fun getMostRecent(): RecoveryLog?

    @Query("SELECT * FROM recovery_logs ORDER BY dateMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 14): Flow<List<RecoveryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RecoveryLog): Long

    @Update
    suspend fun update(log: RecoveryLog)

    @Query("DELETE FROM recovery_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recovery_logs")
    suspend fun deleteAll()
}
