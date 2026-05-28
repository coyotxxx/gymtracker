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

    /**
     * v2.3.0 — ranked search:
     *  - 100: exact slug match (np. "barbell-bench-press")
     *  - 90: exact name/namePl (case-insensitive)
     *  - 70: name/namePl starts-with (np. "wyciskanie")
     *  - 50: searchIndex zawiera token (cale słowo, " q " padding)
     *  - 30: searchIndex zawiera fragment (substring)
     *  - 0: brak match → wyfiltrowane przez WHERE
     *
     * Pole searchIndex jest pre-computowane (PL+EN+ASCII fold+tokeny+slang).
     */
    @Query("""
        SELECT * FROM exercises
        WHERE searchIndex LIKE '%' || LOWER(:query) || '%'
           OR LOWER(name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(namePl) LIKE '%' || LOWER(:query) || '%'
        ORDER BY
          CASE
            WHEN LOWER(slug) = LOWER(:query) THEN 100
            WHEN LOWER(name) = LOWER(:query) OR LOWER(namePl) = LOWER(:query) THEN 90
            WHEN LOWER(name) LIKE LOWER(:query) || '%' OR LOWER(namePl) LIKE LOWER(:query) || '%' THEN 70
            WHEN searchIndex LIKE '% ' || LOWER(:query) || ' %' THEN 50
            WHEN searchIndex LIKE '%' || LOWER(:query) || '%' THEN 30
            ELSE 10
          END DESC,
          isFavorite DESC,
          name COLLATE NOCASE ASC
        LIMIT 50
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

    /**
     * v1.29.25: ustawia searchAliases dla ćwiczenia po nazwie. Idempotentne —
     * nadpisuje wartość, więc seeder może bezpiecznie odpalać przy każdym starcie.
     */
    @Query("UPDATE exercises SET searchAliases = :aliases WHERE LOWER(name) = LOWER(:name)")
    suspend fun setSearchAliasesByName(name: String, aliases: String): Int

    /**
     * v1.29.10: ćwiczenia cardio, które przez starszą ścieżkę dodawania trafiły
     * z metricType WEIGHT_REPS — naprawiamy na DISTANCE_DURATION (czas/dystans).
     * Idempotentne — uruchamiane przy każdym starcie z seedera.
     *
     * v1.29.12: dopasowanie również po nazwie — łapie cardio z grupą inną niż
     * CARDIO (np. własne ćwiczenie "Marsz na bieżni pod górkę"), które samo
     * filtrowanie po primaryMuscle pomijało.
     */
    @Query(
        "UPDATE exercises SET metricType = 'DISTANCE_DURATION' " +
            "WHERE metricType = 'WEIGHT_REPS' AND (" +
            "primaryMuscle = 'CARDIO' " +
            "OR LOWER(name) LIKE '%bieżni%' " +
            "OR LOWER(name) LIKE '%rower%' " +
            "OR LOWER(name) LIKE '%orbitrek%' " +
            "OR LOWER(name) LIKE '%eliptyczn%' " +
            "OR LOWER(name) LIKE '%wioślar%' " +
            "OR LOWER(name) LIKE '%skakank%' " +
            "OR LOWER(name) LIKE '%spinning%')"
    )
    suspend fun fixCardioMetricType(): Int

    /**
     * v1.29.15: serie cardio w planach utworzonych zanim cardio miało osobne pola
     * mają czas trwania w `reps` (np. bieżnia "25" = 25 minut), a `durationSec` puste.
     * Przepisujemy reps → durationSec (reps traktowane jako minuty), żeby start
     * treningu z planu pokazywał zaplanowany czas. Idempotentne — tylko gdy
     * durationSec puste. Uruchamiać PO fixCardioMetricType (potrzebny poprawny metricType).
     */
    @Query(
        "UPDATE plan_exercise_sets SET durationSec = reps * 60 " +
            "WHERE durationSec IS NULL AND reps > 0 AND planExerciseId IN (" +
            "SELECT pe.id FROM plan_exercises pe " +
            "JOIN exercises e ON e.id = pe.exerciseId " +
            "WHERE e.metricType = 'DURATION' OR e.metricType = 'DISTANCE_DURATION')"
    )
    suspend fun fixCardioPlanSetDurations(): Int

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
     * v1.25.9: internal cleanup — bootstrap czyści pola ExerciseDB gdy externalId
     * wskazuje na ćwiczenie z martwym gifUrl (HTTP 404 na CDN). Zachowuje user
     * data (name, primaryMuscle, equipment, isFavorite, history). NIE jest
     * wystawione w UI.
     */
    @Query("""
        UPDATE exercises
        SET externalId = NULL,
            gifUrl = NULL,
            instructionsEnJson = NULL,
            instructionsPlJson = NULL,
            targetMusclesCsv = NULL,
            secondaryMusclesCsv = NULL,
            equipmentDbCsv = NULL,
            bodyPartCsv = NULL
        WHERE id = :id
    """)
    suspend fun clearExerciseDbFields(id: Long)

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

    // === v2.0.0 — canonical exercise-db queries ===

    @Query("SELECT * FROM exercises WHERE slug = :slug LIMIT 1")
    suspend fun findBySlug(slug: String): Exercise?

    @Query("SELECT * FROM exercises WHERE LOWER(namePl) = LOWER(:namePl) LIMIT 1")
    suspend fun findByNamePl(namePl: String): Exercise?

    /**
     * v2.3.1 — UPDATE WHERE id (przez @Update Room).
     * KRYTYCZNE: NIE używać @Insert(REPLACE) bo SQLite REPLACE robi DELETE+INSERT,
     * a workout_sets.exerciseId ma FK z ON DELETE RESTRICT → constraint failure
     * gdy user ma historię treningów wskazującą na te exercises (crash bootstrap).
     */
    @androidx.room.Update
    suspend fun updateBySlug(exercise: Exercise)

    @Query("SELECT COUNT(*) FROM exercises WHERE slug IS NOT NULL")
    suspend fun countCanonical(): Int

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    suspend fun getByCategory(category: String): List<Exercise>

    @Query("SELECT * FROM exercises WHERE movementPattern = :pattern ORDER BY name COLLATE NOCASE ASC")
    suspend fun getByMovementPattern(pattern: String): List<Exercise>

    /** v2.0.0: ustawia isFavorite=1 po canonical slug. Idempotent. */
    @Query("UPDATE exercises SET isFavorite = 1 WHERE slug = :slug")
    suspend fun markFavoriteBySlug(slug: String): Int
}
