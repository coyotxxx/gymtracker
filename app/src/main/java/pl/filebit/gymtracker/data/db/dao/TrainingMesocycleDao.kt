package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.TrainingMesocycle

/**
 * DAO dla mezo-cykli treningowych (v1.13.0 — fundament periodyzacji).
 *
 * Konwencja: tylko jeden mesocykl ze statusem ACTIVE w dowolnym momencie.
 * Logika lifecycle (PLANNED → ACTIVE → COMPLETED) zarządzana przez
 * PeriodizationOrchestrator (v1.14.0).
 */
@Dao
interface TrainingMesocycleDao {

    /** Aktualnie trwający cykl (powinien być co najwyżej jeden). */
    @Query("SELECT * FROM training_mesocycles WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActive(): TrainingMesocycle?

    /** Reactive watch na aktywny cykl — dla UI (Home, PlanCyklu). */
    @Query("SELECT * FROM training_mesocycles WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<TrainingMesocycle?>

    /** Ostatnie N cykli (najnowsze pierwsze). Dla widoku historii. */
    @Query("SELECT * FROM training_mesocycles ORDER BY startDateMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 12): List<TrainingMesocycle>

    /** Reactive watch na historię (do listy w PeriodizationPlanScreen v1.16.0). */
    @Query("SELECT * FROM training_mesocycles ORDER BY startDateMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<TrainingMesocycle>>

    /**
     * Cykl który był aktywny w danym momencie (startDateMs <= now, endDateMs >= now lub null).
     * Używany przy backfillu historycznym oraz do tagowania starych TrainingDaySummary mesocycleId.
     */
    @Query("""
        SELECT * FROM training_mesocycles
        WHERE startDateMs <= :now
          AND (endDateMs IS NULL OR endDateMs >= :now)
        ORDER BY startDateMs DESC
        LIMIT 1
    """)
    suspend fun getForDate(now: Long): TrainingMesocycle?

    /** Po id — punkt wejścia z MesocycleDetailScreen. */
    @Query("SELECT * FROM training_mesocycles WHERE id = :id")
    suspend fun getById(id: Long): TrainingMesocycle?

    /** Liczba wszystkich cykli. Używane przez MesocycleBackfillService dla idempotencji. */
    @Query("SELECT COUNT(*) FROM training_mesocycles")
    suspend fun count(): Int

    /** Insert lub replace. Zwraca nowe id przy insercie, lub bieżące przy replace. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mesocycle: TrainingMesocycle): Long

    /** Update istniejącego cyklu (np. zmiana weekInPhase, status). */
    @Update
    suspend fun update(mesocycle: TrainingMesocycle)

    /** Zakończ aktywny cykl (PeriodizationOrchestrator wywołuje przy przejściu fazy). */
    @Query("UPDATE training_mesocycles SET status = 'COMPLETED', endDateMs = :endMs WHERE id = :id")
    suspend fun complete(id: Long, endMs: Long)

    /** Oznacz jako pominięty (user odrzucił propozycję AI). */
    @Query("UPDATE training_mesocycles SET status = 'SKIPPED' WHERE id = :id")
    suspend fun skip(id: Long)

    /** Awaryjnie usuń. Używać tylko z testów / rollback. */
    @Query("DELETE FROM training_mesocycles WHERE id = :id")
    suspend fun delete(id: Long)
}
