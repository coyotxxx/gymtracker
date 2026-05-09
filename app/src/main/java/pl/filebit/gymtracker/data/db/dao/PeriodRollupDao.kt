package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pl.filebit.gymtracker.data.entity.MonthlyRollup
import pl.filebit.gymtracker.data.entity.QuarterlyRollup
import pl.filebit.gymtracker.data.entity.WeeklyRollup

/**
 * v1.11.66 — DAOs dla period rollups.
 *
 * Idempotency: insert ignoruje gdy już istnieje rollup dla tego okresu
 * (unique index na *StartMs). Klient sprawdza countForPeriod przed insert.
 */

@Dao
interface WeeklyRollupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rollup: WeeklyRollup): Long

    @Query("SELECT * FROM weekly_rollups WHERE weekStartMs = :weekStartMs LIMIT 1")
    suspend fun getForWeek(weekStartMs: Long): WeeklyRollup?

    @Query("SELECT COUNT(*) FROM weekly_rollups WHERE weekStartMs = :weekStartMs")
    suspend fun countForWeek(weekStartMs: Long): Int

    @Query("SELECT * FROM weekly_rollups ORDER BY weekStartMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 4): List<WeeklyRollup>

    @Query("SELECT * FROM weekly_rollups WHERE weekStartMs >= :fromMs AND weekStartMs <= :toMs ORDER BY weekStartMs ASC")
    suspend fun getRange(fromMs: Long, toMs: Long): List<WeeklyRollup>

    @Query("SELECT COUNT(*) FROM weekly_rollups")
    suspend fun count(): Int
}

@Dao
interface MonthlyRollupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rollup: MonthlyRollup): Long

    @Query("SELECT * FROM monthly_rollups WHERE monthStartMs = :monthStartMs LIMIT 1")
    suspend fun getForMonth(monthStartMs: Long): MonthlyRollup?

    @Query("SELECT COUNT(*) FROM monthly_rollups WHERE monthStartMs = :monthStartMs")
    suspend fun countForMonth(monthStartMs: Long): Int

    @Query("SELECT * FROM monthly_rollups ORDER BY monthStartMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 6): List<MonthlyRollup>

    @Query("SELECT COUNT(*) FROM monthly_rollups")
    suspend fun count(): Int
}

@Dao
interface QuarterlyRollupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rollup: QuarterlyRollup): Long

    @Query("SELECT * FROM quarterly_rollups WHERE quarterStartMs = :quarterStartMs LIMIT 1")
    suspend fun getForQuarter(quarterStartMs: Long): QuarterlyRollup?

    @Query("SELECT COUNT(*) FROM quarterly_rollups WHERE quarterStartMs = :quarterStartMs")
    suspend fun countForQuarter(quarterStartMs: Long): Int

    @Query("SELECT * FROM quarterly_rollups ORDER BY quarterStartMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 4): List<QuarterlyRollup>

    @Query("SELECT COUNT(*) FROM quarterly_rollups")
    suspend fun count(): Int
}
