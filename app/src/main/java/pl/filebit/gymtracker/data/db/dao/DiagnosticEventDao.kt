package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import pl.filebit.gymtracker.data.entity.DiagnosticEvent

@Dao
interface DiagnosticEventDao {

    @Insert
    suspend fun insert(event: DiagnosticEvent): Long

    @Query("SELECT * FROM diagnostic_events ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<DiagnosticEvent>

    @Query("SELECT COUNT(*) FROM diagnostic_events")
    suspend fun count(): Int

    @Query("DELETE FROM diagnostic_events WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM diagnostic_events")
    suspend fun deleteAll()
}
