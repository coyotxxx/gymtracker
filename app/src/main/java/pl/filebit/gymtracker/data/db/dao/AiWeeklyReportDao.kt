package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.AiWeeklyReport

@Dao
interface AiWeeklyReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: AiWeeklyReport): Long

    @Query("SELECT * FROM ai_weekly_reports ORDER BY weekStartMillis DESC, generatedAtMillis DESC")
    fun observeAll(): Flow<List<AiWeeklyReport>>

    @Query("SELECT * FROM ai_weekly_reports WHERE weekStartMillis = :weekStart ORDER BY generatedAtMillis DESC LIMIT 1")
    suspend fun getForWeek(weekStart: Long): AiWeeklyReport?

    @Query("SELECT * FROM ai_weekly_reports ORDER BY generatedAtMillis DESC LIMIT 1")
    suspend fun getMostRecent(): AiWeeklyReport?

    @Query("DELETE FROM ai_weekly_reports WHERE id = :id")
    suspend fun delete(id: Long)
}
