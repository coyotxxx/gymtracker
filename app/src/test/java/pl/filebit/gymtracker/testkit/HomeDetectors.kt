package pl.filebit.gymtracker.testkit

import android.content.Context
import pl.filebit.gymtracker.ai.EventDetectorService
import pl.filebit.gymtracker.ai.MuscleRecoveryAnalyzer
import pl.filebit.gymtracker.ai.RecoveryScoreCalculator
import pl.filebit.gymtracker.ai.TrainingLoadAnalyzer
import pl.filebit.gymtracker.ai.TrainingPhaseAnalyzer
import pl.filebit.gymtracker.ai.TrainingReadinessAnalyzer
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import pl.filebit.gymtracker.data.repository.DeloadService
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.StatsCacheService
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.ui.home.HomeUiState

/**
 * v1.27 — FAZA 1.4 — buduje prawdziwe detektory ekranu Home z in-memory Room.
 *
 * Cały łańcuch zależności (12 obiektów) ręcznie z DAO bazy testowej — żaden
 * mock. `buildHomeState()` woła te same detektory co HomeViewModel i składa
 * `HomeUiState` z prawdziwych wyników. Dzięki temu test E2E wykrywa bugi
 * detektorów (nie tylko reguł kart jak pilot v1).
 */
class HomeDetectors(db: AppDatabase, context: Context) {

    // — repozytoria/serwisy pomocnicze —
    private val statsCacheService = StatsCacheService(
        db.workoutDao(), db.exerciseDao(), db.workoutSetDao()
    )
    private val statsRepo = StatsRepository(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        db.bodyMeasurementDao(), db.goalDao(), db.unlockedAchievementDao(),
        db.userProfileDao(), db.trainingMesocycleDao()
    )
    private val eventDetector = EventDetectorService(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        db.trainingEventDao(), statsRepo, statsCacheService
    )
    private val planRepo = PlanRepository(
        db.trainingPlanDao(), db.planExerciseDao(), db.planExerciseSetDao(),
        db.weeklyPlanOverrideDao(), eventDetector
    )
    private val userProfileRepo = UserProfileRepository(db.userProfileDao())
    private val deloadPrefs = DeloadPreferences(context)

    // — detektory ekranu Home —
    val deloadService = DeloadService(
        db.workoutDao(), db.workoutSetDao(), statsRepo, planRepo,
        deloadPrefs, userProfileRepo
    )
    val trainingLoadAnalyzer = TrainingLoadAnalyzer(db.workoutDao(), db.workoutSetDao())
    val recoveryScoreCalculator = RecoveryScoreCalculator(db.recoveryLogDao())
    val muscleRecoveryAnalyzer = MuscleRecoveryAnalyzer(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao()
    )
    val readinessAnalyzer = TrainingReadinessAnalyzer(
        recoveryScoreCalculator, trainingLoadAnalyzer, muscleRecoveryAnalyzer
    )
    val phaseAnalyzer = TrainingPhaseAnalyzer(db.workoutDao(), db.workoutSetDao())

    /**
     * Woła wszystkie detektory na danych scenariusza i składa `HomeUiState`
     * — tak jak robi to HomeViewModel.combine(...).
     */
    suspend fun buildHomeState(): HomeUiState {
        val phase = phaseAnalyzer.analyze()
        return HomeUiState(
            deloadCard = deloadService.cardState(),
            trainingPhase = phase,
            trainingLoad = trainingLoadAnalyzer.analyze(),
            recoveryScore = recoveryScoreCalculator.calculate(),
            trainingReadiness = readinessAnalyzer.analyze(phase.phase),
            muscleRecovery = muscleRecoveryAnalyzer.analyze()
        )
    }
}
