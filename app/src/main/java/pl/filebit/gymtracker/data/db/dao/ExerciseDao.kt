package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE primaryMuscle = :muscle ORDER BY name COLLATE NOCASE ASC")
    fun observeByMuscle(muscle: MuscleGroup): Flow<List<Exercise>>

    @Query("""
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun search(query: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT LOWER(name) FROM exercises")
    suspend fun allNamesLower(): List<String>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFavorites(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<Exercise>>

    @Query("UPDATE exercises SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE exercises SET isFavorite = 1 WHERE LOWER(name) = LOWER(:name)")
    suspend fun markFavoriteByName(name: String): Int

    /** Ćwiczenia które user oznaczył jako "unikaj" (np. boli kolano przy wykrokach). AI ich nie zaproponuje. */
    @Query("SELECT * FROM exercises WHERE isAvoided = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAvoided(): List<Exercise>

    @Query("UPDATE exercises SET isAvoided = :avoided WHERE id = :id")
    suspend fun setAvoided(id: Long, avoided: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: Exercise): Long

    // v1.25.0 — ExerciseDB integration
    @Query("SELECT * FROM exercises WHERE externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(externalId: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercises WHERE externalId IS NOT NULL")
    suspend fun countWithExternalId(): Int

    /**
     * Update fields z ExerciseDB (po fuzzy match). NIE nadpisujemy user fields
     * (name, primaryMuscle, equipment, isFavorite, isAvoided, isCustom, notes).
     */
    @Query("""
        UPDATE exercises
        SET externalId = :externalId,
            gifUrl = :gifUrl,
            instructionsEnJson = :instructionsEnJson,
            targetMusclesCsv = :targetMusclesCsv,
            secondaryMusclesCsv = :secondaryMusclesCsv,
            equipmentDbCsv = :equipmentDbCsv,
            bodyPartCsv = :bodyPartCsv
        WHERE id = :id AND externalId IS NULL
    """)
    suspend fun applyExerciseDbMatch(
        id: Long,
        externalId: String,
        gifUrl: String?,
        instructionsEnJson: String?,
        targetMusclesCsv: String?,
        secondaryMusclesCsv: String?,
        equipmentDbCsv: String?,
        bodyPartCsv: String?
    )

    /** Update polskich instrukcji (AI cache po tłumaczeniu). */
    @Query("UPDATE exercises SET instructionsPlJson = :pl WHERE id = :id")
    suspend fun updateInstructionsPl(id: Long, pl: String)

    /**
     * v1.25.6: re-match — wymusza nowe ExerciseDB ID i pola, nawet jeśli
     * ćwiczenie ma już externalId (do poprawiania błędnych fuzzy matchy).
     */
    @Query("""
        UPDATE exercises
        SET externalId = :externalId,
            gifUrl = :gifUrl,
            instructionsEnJson = :instructionsEnJson,
            instructionsPlJson = :instructionsPlJson,
            targetMusclesCsv = :targetMusclesCsv,
            secondaryMusclesCsv = :secondaryMusclesCsv,
            equipmentDbCsv = :equipmentDbCsv,
            bodyPartCsv = :bodyPartCsv
        WHERE id = :id
    """)
    suspend fun forceReplaceExerciseDbMatch(
        id: Long,
        externalId: String,
        gifUrl: String?,
        instructionsEnJson: String?,
        instructionsPlJson: String?,
        targetMusclesCsv: String?,
        secondaryMusclesCsv: String?,
        equipmentDbCsv: String?,
        bodyPartCsv: String?
    )

    /**
     * v1.25.7: re-link FK referencji z duplicate exercise na kanoniczne ID.
     * Po przepięciu wszystkich FK można bezpiecznie usunąć duplikat.
     * Cztery tabele odwołują się do exercises.id:
     *  - workout_sets (FK RESTRICT)
     *  - plan_exercises (FK RESTRICT)
     *  - goals.exerciseId (nullable, brak FK constraint)
     *  - training_events.exerciseId (nullable, brak FK constraint)
     */
    @Query("UPDATE workout_sets SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun relinkWorkoutSets(oldId: Long, newId: Long)

    @Query("UPDATE plan_exercises SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun relinkPlanExercises(oldId: Long, newId: Long)

    @Query("UPDATE goals SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun relinkGoals(oldId: Long, newId: Long)

    @Query("UPDATE training_events SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun relinkTrainingEvents(oldId: Long, newId: Long)

    /** v1.25.7: bezpieczne usuwanie ćwiczenia (po wcześniejszym re-link FK). */
    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
