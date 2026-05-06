package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.AiLog

@Dao
interface AiLogDao {

    @Query("SELECT * FROM ai_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AiLog>>

    @Query("SELECT * FROM ai_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<AiLog>

    @Query("SELECT * FROM ai_logs WHERE id = :id")
    suspend fun getById(id: Long): AiLog?

    @Query("SELECT COUNT(*) FROM ai_logs")
    suspend fun count(): Int

    @Insert
    suspend fun insert(log: AiLog): Long

    @Query("DELETE FROM ai_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM ai_logs WHERE createdAt < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)

    /** Trzymaj max N najnowszych logów — usuwa starsze. */
    @Query("""
        DELETE FROM ai_logs
        WHERE id NOT IN (SELECT id FROM ai_logs ORDER BY createdAt DESC LIMIT :keep)
    """)
    suspend fun trimToMax(keep: Int)
}
