package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.DecisionStatus
import pl.filebit.gymtracker.data.entity.PendingPeriodizationDecision

/**
 * DAO dla decyzji periodyzacyjnych AI (v1.15.0).
 *
 * Lifecycle: PENDING → ACCEPTED/MODIFIED/DISMISSED.
 * Tylko PENDING decyzje są pokazywane na Home jako karty "AI TRENER PROPONUJE".
 */
@Dao
interface PendingPeriodizationDecisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decision: PendingPeriodizationDecision): Long

    /** Reactive watch dla karty "AI TRENER PROPONUJE" na Home — tylko PENDING. */
    @Query("SELECT * FROM pending_periodization_decisions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<PendingPeriodizationDecision>>

    @Query("SELECT * FROM pending_periodization_decisions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPending(): List<PendingPeriodizationDecision>

    /** Po id — szczegóły w UI "Wyjaśnij więcej". */
    @Query("SELECT * FROM pending_periodization_decisions WHERE id = :id")
    suspend fun getById(id: Long): PendingPeriodizationDecision?

    /** Cała historia (audit trail) — opcjonalne UI w v1.16.0+. */
    @Query("SELECT * FROM pending_periodization_decisions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<PendingPeriodizationDecision>

    /** User accept — orchestrator zastosował transition. */
    @Query("""
        UPDATE pending_periodization_decisions
        SET status = 'ACCEPTED', resolvedAt = :nowMs, resolvedAction = :note
        WHERE id = :id
    """)
    suspend fun markAccepted(id: Long, nowMs: Long, note: String = "applied")

    /** User zmodyfikował propozycję (v1.16.0+ z UI edycji). */
    @Query("""
        UPDATE pending_periodization_decisions
        SET status = 'MODIFIED', resolvedAt = :nowMs, resolvedAction = :note
        WHERE id = :id
    """)
    suspend fun markModified(id: Long, nowMs: Long, note: String = "modified")

    /** User odrzucił propozycję. */
    @Query("""
        UPDATE pending_periodization_decisions
        SET status = 'DISMISSED', resolvedAt = :nowMs, resolvedAction = :note
        WHERE id = :id
    """)
    suspend fun markDismissed(id: Long, nowMs: Long, note: String = "dismissed")

    /** Sprawdzenie czy są jakieś PENDING (do ProactiveAiCheckWorker — żeby nie spamować). */
    @Query("SELECT COUNT(*) FROM pending_periodization_decisions WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    @Query("DELETE FROM pending_periodization_decisions WHERE id = :id")
    suspend fun delete(id: Long)
}
