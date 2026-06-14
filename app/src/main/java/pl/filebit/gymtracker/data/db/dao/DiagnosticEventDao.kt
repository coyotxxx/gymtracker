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

    /** Historia wysłanych powiadomień (dzwonek). category = DiagnosticCategory.NOTIFICATION.name */
    @Query("SELECT * FROM diagnostic_events WHERE category = :category ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentByCategory(category: String, limit: Int = 60): List<DiagnosticEvent>

    /** Licznik nieprzeczytanych = wpisy danej kategorii nowsze niż ostatnie otwarcie dzwonka. */
    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE category = :category AND timestampMs > :sinceMs")
    suspend fun countByCategorySince(category: String, sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM diagnostic_events")
    suspend fun count(): Int

    @Query("DELETE FROM diagnostic_events WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM diagnostic_events")
    suspend fun deleteAll()
}
