package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import pl.filebit.gymtracker.data.entity.NotificationHistory

@Dao
interface NotificationHistoryDao {

    @Insert
    suspend fun insert(item: NotificationHistory): Long

    @Query("SELECT * FROM notification_history ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 80): List<NotificationHistory>

    /** Dedup zapisu: czy taki sam tytuł był już zapisany od `sinceMs`. */
    @Query("SELECT COUNT(*) FROM notification_history WHERE title = :title AND timestampMs >= :sinceMs")
    suspend fun countSameTitleSince(title: String, sinceMs: Long): Int

    @Query("DELETE FROM notification_history WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
