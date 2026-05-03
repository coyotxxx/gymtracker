package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.WeeklyPlanOverride

@Dao
interface WeeklyPlanOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(override: WeeklyPlanOverride): Long

    @Query("SELECT * FROM weekly_plan_overrides WHERE weekStartMillis = :weekStart")
    suspend fun getForWeek(weekStart: Long): List<WeeklyPlanOverride>

    @Query("SELECT * FROM weekly_plan_overrides WHERE weekStartMillis = :weekStart")
    fun observeForWeek(weekStart: Long): Flow<List<WeeklyPlanOverride>>

    /**
     * Usuwa wszystkie nadpisania dla danego planu i oryginalnego dnia w danym tygodniu.
     * Wywoływane przed `insert` żeby uniknąć duplikatów (jeden plan + dzień ma maks
     * 1 override per tydzień).
     */
    @Query("""
        DELETE FROM weekly_plan_overrides
        WHERE weekStartMillis = :weekStart
          AND planId = :planId
          AND originalDayOfWeek = :originalDay
    """)
    suspend fun deleteForOrigin(weekStart: Long, planId: Long, originalDay: Int)

    @Query("DELETE FROM weekly_plan_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM weekly_plan_overrides WHERE weekStartMillis < :cutoffMillis")
    suspend fun deleteOldOverrides(cutoffMillis: Long)
}
