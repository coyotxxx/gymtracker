package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType

@Dao
interface TrainingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TrainingEvent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<TrainingEvent>): List<Long>

    /** Wszystkie eventy chronologicznie (od najnowszych). Do widoku historii. */
    @Query("SELECT * FROM training_events ORDER BY date DESC")
    fun observeAll(): Flow<List<TrainingEvent>>

    /** Eventy posortowane od najnowszych. Limit dla AI context. */
    @Query("SELECT * FROM training_events ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 15): List<TrainingEvent>

    /** Eventy konkretnego typu. */
    @Query("SELECT * FROM training_events WHERE type = :type ORDER BY date DESC LIMIT :limit")
    suspend fun getByType(type: TrainingEventType, limit: Int = 50): List<TrainingEvent>

    /** Eventy w przedziale czasowym (do period rollups w v1.11.60). */
    @Query("SELECT * FROM training_events WHERE date >= :fromMs AND date <= :toMs ORDER BY date DESC")
    suspend fun getInRange(fromMs: Long, toMs: Long): List<TrainingEvent>

    /** Eventy dla konkretnego ćwiczenia (PR-y, kontuzje). */
    @Query("SELECT * FROM training_events WHERE exerciseId = :exerciseId ORDER BY date DESC")
    suspend fun getForExercise(exerciseId: Long): List<TrainingEvent>

    /** Eventy dla konkretnego workoutu. */
    @Query("SELECT * FROM training_events WHERE workoutId = :workoutId")
    suspend fun getForWorkout(workoutId: Long): List<TrainingEvent>

    /** Czy istnieje już PR dla tego ćwiczenia z konkretnymi parametrami? Anti-duplicate. */
    @Query("""
        SELECT COUNT(*) FROM training_events
        WHERE type = 'PR_SET'
          AND exerciseId = :exerciseId
          AND weightKg = :weightKg
          AND reps = :reps
    """)
    suspend fun countExistingPr(exerciseId: Long, weightKg: Double, reps: Int): Int

    /** Ostatni event danego typu (np. ostatni DELOAD_DETECTED dla cycle math). */
    @Query("SELECT * FROM training_events WHERE type = :type ORDER BY date DESC LIMIT 1")
    suspend fun getLatestOfType(type: TrainingEventType): TrainingEvent?

    @Query("DELETE FROM training_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM training_events")
    suspend fun count(): Int
}
