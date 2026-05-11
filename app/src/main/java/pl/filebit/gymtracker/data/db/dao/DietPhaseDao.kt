package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.DietPhase

@Dao
interface DietPhaseDao {

    @Query("""
        SELECT * FROM diet_phases
        WHERE startDateMs <= :nowMs AND (endDateMs IS NULL OR endDateMs > :nowMs)
        ORDER BY startDateMs DESC LIMIT 1
    """)
    suspend fun getCurrent(nowMs: Long = System.currentTimeMillis()): DietPhase?

    @Query("""
        SELECT * FROM diet_phases
        WHERE startDateMs <= :nowMs AND (endDateMs IS NULL OR endDateMs > :nowMs)
        ORDER BY startDateMs DESC LIMIT 1
    """)
    fun observeCurrent(nowMs: Long = System.currentTimeMillis()): Flow<DietPhase?>

    @Query("SELECT * FROM diet_phases ORDER BY startDateMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<DietPhase>

    @Query("SELECT * FROM diet_phases ORDER BY startDateMs DESC")
    fun observeAll(): Flow<List<DietPhase>>

    /**
     * Ostatnia zakończona/aktywna CUT phase (do liczenia długości aktualnego cutu).
     */
    @Query("""
        SELECT * FROM diet_phases
        WHERE type = 'CUT'
        ORDER BY startDateMs DESC LIMIT 1
    """)
    suspend fun getLastCut(): DietPhase?

    /**
     * v1.17.0 — startDateMs ostatniego REFEED_DAY (do reguły auto_refeed_heavy_day:
     * REFEED tylko ≥7 dni od poprzedniego).
     */
    @Query("""
        SELECT startDateMs FROM diet_phases
        WHERE type = 'REFEED_DAY'
        ORDER BY startDateMs DESC LIMIT 1
    """)
    suspend fun getLastRefeedStartMs(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(phase: DietPhase): Long

    @Update
    suspend fun update(phase: DietPhase)

    @Query("UPDATE diet_phases SET endDateMs = :endMs WHERE id = :id")
    suspend fun closePhase(id: Long, endMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM diet_phases WHERE id = :id")
    suspend fun delete(id: Long)
}
