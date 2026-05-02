package pl.filebit.gymtracker.data.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.util.MuscleVolumeReport
import pl.filebit.gymtracker.util.reportWeeklyVolume
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * Liczy volume (zaliczone sety robocze) per partia mięśniowa za bieżący tydzień
 * (Pon–Nd) i klasyfikuje względem zaleceń (10-20/tyg).
 */
@Singleton
class VolumeService @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val profileRepo: UserProfileRepository
) {
    suspend fun currentWeekReport(): List<MuscleVolumeReport> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val daysFromMonday = today.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
        val mondayDate = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val weekStart = mondayDate.atStartOfDayIn(tz).toEpochMilliseconds()
        val weekEnd = weekStart + 7.days.inWholeMilliseconds

        val workouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt in weekStart until weekEnd }

        val sets = workouts.flatMap { w ->
            setDao.getForWorkout(w.id)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
        }

        // Cache: exerciseId → muscle
        val muscleCache = sets.map { it.exerciseId }.distinct()
            .associateWith { exerciseDao.getById(it)?.primaryMuscle }

        // Mapa: muscle → liczba setów
        val byMuscle: Map<MuscleGroup, Int> = sets
            .mapNotNull { muscleCache[it.exerciseId] }
            .groupingBy { it }
            .eachCount()

        val goal = profileRepo.get().goal
        return reportWeeklyVolume(byMuscle, goal)
    }
}
