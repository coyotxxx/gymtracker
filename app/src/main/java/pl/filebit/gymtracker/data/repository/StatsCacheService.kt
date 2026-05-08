package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StatsCacheService v1.11.38 — fundament refactoru wydajności (N+1 queries).
 *
 * Aktualnie StatsRepository i AI analyzers wywołują `setDao.getForWorkout(w.id)`
 * i `exerciseDao.getById(s.exerciseId)` w pętli — przy 30 treningach × 18 setów
 * to ~600 queries SQL na pojedynczą metodę. HomeViewModel.reload() agreguje
 * ~1500 queries łącznie.
 *
 * Rozwiązanie: jedno bulk fetch (3 queries SQL) + pre-computed maps w pamięci.
 * Każda metoda używa snapshot zamiast DAO calls per-item.
 *
 * UWAGA v1.11.38: Sam plik dodany — NIKT NIE UŻYWA SNAPSHOT JESZCZE.
 * Refactor metod robi się inkrementalnie w v1.11.39+. Tu tylko fundament.
 *
 * Test bezpieczeństwa: zero zmian w istniejącym kodzie.
 */
@Singleton
class StatsCacheService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val setDao: WorkoutSetDao
) {
    /**
     * Załaduj świeży snapshot wszystkich danych potrzebnych do statystyk.
     * 3 bulk queries SQL (zamiast 600+ pojedynczych w analyzerach).
     *
     * Snapshot jest immutable — bezpieczny do użycia w wielu metodach naraz.
     * Każde wywołanie buduje świeży snapshot (brak persystowanego cache —
     * dane mogą się zmienić między wywołaniami, np. po zakończeniu treningu).
     */
    suspend fun snapshot(): StatsSnapshot {
        val allWorkouts = workoutDao.observeAllOnce()
        val allExercises = exerciseDao.getAll()
        val allSets = setDao.getAll()
        return StatsSnapshot.from(allWorkouts, allExercises, allSets)
    }
}

/**
 * Immutable snapshot — wszystkie dane potrzebne dla statystyk pre-loaded.
 * Pre-computed maps i filtered lists dla najczęstszych operacji.
 *
 * Konwencja:
 * - `all*` — surowy zbiór (wszystkie z DB)
 * - `*ById` — Map dla O(1) lookup po ID
 * - `*ByWorkoutId` — Map<workoutId, List<...>> dla iterowania per-workout
 * - `completed*` — pre-filtered: `isCompleted && setType != WARMUP`
 *   (najczęstszy filtr w analyzerach — robimy go raz tutaj)
 */
data class StatsSnapshot(
    val allWorkouts: List<Workout>,
    val finishedWorkouts: List<Workout>,
    val workoutsById: Map<Long, Workout>,

    val allExercises: List<Exercise>,
    val exercisesById: Map<Long, Exercise>,

    val allSets: List<WorkoutSet>,
    val setsByWorkoutId: Map<Long, List<WorkoutSet>>,

    /** Pre-filtered: isCompleted && setType != WARMUP (najczęstszy filtr). */
    val completedSets: List<WorkoutSet>,
    val completedSetsByWorkoutId: Map<Long, List<WorkoutSet>>
) {
    companion object {
        fun from(
            allWorkouts: List<Workout>,
            allExercises: List<Exercise>,
            allSets: List<WorkoutSet>
        ): StatsSnapshot {
            val finishedWorkouts = allWorkouts.filter { it.finishedAt != null }
            val finishedIds = finishedWorkouts.map { it.id }.toSet()
            val completed = allSets.filter {
                it.isCompleted && it.setType != SetType.WARMUP
            }
            return StatsSnapshot(
                allWorkouts = allWorkouts,
                finishedWorkouts = finishedWorkouts,
                workoutsById = allWorkouts.associateBy { it.id },
                allExercises = allExercises,
                exercisesById = allExercises.associateBy { it.id },
                allSets = allSets,
                setsByWorkoutId = allSets.groupBy { it.workoutId },
                completedSets = completed,
                completedSetsByWorkoutId = completed.groupBy { it.workoutId }
            )
        }

        /** Pusty snapshot — używany w testach lub gdy DB jest pusta. */
        val EMPTY = StatsSnapshot(
            allWorkouts = emptyList(),
            finishedWorkouts = emptyList(),
            workoutsById = emptyMap(),
            allExercises = emptyList(),
            exercisesById = emptyMap(),
            allSets = emptyList(),
            setsByWorkoutId = emptyMap(),
            completedSets = emptyList(),
            completedSetsByWorkoutId = emptyMap()
        )
    }

    /**
     * Skrót: ćwiczenie po ID setu (najczęstszy use case w pętli per-set).
     * Zwraca null jeśli set nie istnieje lub jego exercise został usunięty.
     */
    fun exerciseForSet(set: WorkoutSet): Exercise? = exercisesById[set.exerciseId]

    /**
     * Skrót: completed sets dla danego workoutu lub pusta lista.
     */
    fun completedSetsFor(workoutId: Long): List<WorkoutSet> =
        completedSetsByWorkoutId[workoutId] ?: emptyList()
}
