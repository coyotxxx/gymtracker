package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.FastingWindow

@Dao
interface FastingWindowDao {

    /** Aktywne okno: eatingEndMs IS NULL — user otworzył ale jeszcze nie zamknął. */
    @Query("SELECT * FROM fasting_windows WHERE eatingEndMs IS NULL ORDER BY eatingStartMs DESC LIMIT 1")
    fun observeActive(): Flow<FastingWindow?>

    @Query("SELECT * FROM fasting_windows WHERE eatingEndMs IS NULL ORDER BY eatingStartMs DESC LIMIT 1")
    suspend fun getActive(): FastingWindow?

    @Query("SELECT * FROM fasting_windows ORDER BY eatingStartMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<FastingWindow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(window: FastingWindow): Long

    @Query("DELETE FROM fasting_windows WHERE id = :id")
    suspend fun delete(id: Long)
}
