package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.util.DeloadRecommendation
import pl.filebit.gymtracker.util.detectDeloadNeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper przedstawiający potrzebę deloadu na podstawie danych z DB.
 * Łączy DAO z pure-function detectDeloadNeed (testowane unit testami).
 */
@Singleton
class DeloadService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val statsRepo: StatsRepository
) {
    suspend fun check(): DeloadRecommendation? {
        val now = System.currentTimeMillis()
        val ms14d = 14L * 24 * 60 * 60 * 1000
        val ms35d = 35L * 24 * 60 * 60 * 1000

        val finishedWorkouts = workoutDao.observeAllOnce().filter { it.finishedAt != null }

        // Sesje w ostatnich 35 dniach
        val sessions35d = finishedWorkouts.count { it.startedAt >= now - ms35d }

        // Średnia RPE z ostatnich 14 dni
        val recent14dWorkouts = finishedWorkouts.filter { it.startedAt >= now - ms14d }
        val rpeValues = recent14dWorkouts.flatMap { w ->
            setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .mapNotNull { it.rpe }
        }
        val avgRpe14d = rpeValues.takeIf { it.isNotEmpty() }?.let { it.average() }

        // Liczba ćwiczeń ze stagnacją (z ostatniego treningu)
        val lastWorkoutId = finishedWorkouts.maxByOrNull { it.startedAt }?.id
        val stagnationCount = lastWorkoutId?.let { id ->
            statsRepo.detectStagnation(id, threshold = 3).size
        } ?: 0

        return detectDeloadNeed(
            avgRpe14d = avgRpe14d,
            sessionsLast35d = sessions35d,
            stagnationCount = stagnationCount
        )
    }
}
