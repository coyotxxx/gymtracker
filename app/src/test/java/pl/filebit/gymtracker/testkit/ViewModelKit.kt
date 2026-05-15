package pl.filebit.gymtracker.testkit

import android.content.Context
import pl.filebit.gymtracker.ai.EventDetectorService
import pl.filebit.gymtracker.ai.PeriodRollupService
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsCacheService
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository

/**
 * v1.27 — FAZA 3 — buduje prawdziwe repozytoria potrzebne ViewModelom
 * z in-memory Room (żaden mock). Analog [HomeDetectors] dla warstwy
 * snapshotów ekranów: pozwala skonstruować ViewModel z realnym grafem DI.
 */
class ViewModelKit(val db: AppDatabase, val context: Context) {

    val statsCacheService = StatsCacheService(
        db.workoutDao(), db.exerciseDao(), db.workoutSetDao()
    )
    val statsRepo = StatsRepository(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        db.bodyMeasurementDao(), db.goalDao(), db.unlockedAchievementDao(),
        db.userProfileDao(), db.trainingMesocycleDao()
    )
    val eventDetector = EventDetectorService(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        db.trainingEventDao(), statsRepo, statsCacheService
    )
    val planRepo = PlanRepository(
        db.trainingPlanDao(), db.planExerciseDao(), db.planExerciseSetDao(),
        db.weeklyPlanOverrideDao(), eventDetector
    )
    private val periodRollup = PeriodRollupService(
        statsCacheService, db.bodyMeasurementDao(), db.trainingEventDao(),
        db.weeklyRollupDao(), db.monthlyRollupDao(), db.quarterlyRollupDao()
    )
    val workoutRepo = WorkoutRepository(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        eventDetector, periodRollup
    )
}
