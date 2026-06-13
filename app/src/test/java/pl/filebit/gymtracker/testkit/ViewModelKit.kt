package pl.filebit.gymtracker.testkit

import android.content.Context
import pl.filebit.gymtracker.ai.AiClientImpl
import pl.filebit.gymtracker.ai.AiContextBuilder
import pl.filebit.gymtracker.ai.AiDecisionExplainer
import pl.filebit.gymtracker.ai.AiMealJsonValidator
import pl.filebit.gymtracker.ai.AiPlanApplier
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.AiToolHandler
import pl.filebit.gymtracker.ai.DietAiService
import pl.filebit.gymtracker.ai.EmergencyMealGenerator
import pl.filebit.gymtracker.ai.EventDetectorService
import pl.filebit.gymtracker.ai.WeeklyReportService
import pl.filebit.gymtracker.ai.HealthInsightAnalyzer
import pl.filebit.gymtracker.ai.MasterAiContextBuilder
import pl.filebit.gymtracker.ai.MuscleRecoveryAnalyzer
import pl.filebit.gymtracker.ai.PeriodRollupService
import pl.filebit.gymtracker.ai.PlanAuditService
import pl.filebit.gymtracker.ai.RecoveryScoreCalculator
import pl.filebit.gymtracker.ai.RpeOpinionService
import pl.filebit.gymtracker.ai.StagnationAnalyzer
import pl.filebit.gymtracker.ai.TrainingLoadAnalyzer
import pl.filebit.gymtracker.ai.TrainingPhaseAnalyzer
import pl.filebit.gymtracker.ai.TrainingReadinessAnalyzer
import pl.filebit.gymtracker.ai.WorkoutAiSummaryService
import pl.filebit.gymtracker.ai.WorkoutPlanAiService
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.health.HealthConnectManager
import pl.filebit.gymtracker.data.repository.AdherenceCalculator
import pl.filebit.gymtracker.data.repository.AiChatRepository
import pl.filebit.gymtracker.data.repository.AiLogRepository
import pl.filebit.gymtracker.data.repository.ActivityRepository
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.GoalRepository
import pl.filebit.gymtracker.data.repository.ProgressPhotoRepository
import pl.filebit.gymtracker.data.repository.StrengthRepository
import pl.filebit.gymtracker.data.repository.AutoAdjustmentService
import pl.filebit.gymtracker.data.repository.CardioKcalEstimator
import pl.filebit.gymtracker.data.repository.ConstraintResolver
import pl.filebit.gymtracker.data.repository.DailyQualityScorer
import pl.filebit.gymtracker.data.repository.DamageControl
import pl.filebit.gymtracker.data.repository.DietPhaseRepository
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.DietRepository
import pl.filebit.gymtracker.data.repository.DietVolatilityAnalyzer
import pl.filebit.gymtracker.data.repository.DietaryKnowledgeRepository
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.HydrationCalculator
import pl.filebit.gymtracker.data.repository.HydrationRepository
import pl.filebit.gymtracker.data.repository.MealConsumptionRepository
import pl.filebit.gymtracker.data.repository.MealFeedbackRepository
import pl.filebit.gymtracker.data.repository.NeatAnalyzer
import pl.filebit.gymtracker.data.repository.PhaseManager
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.data.repository.QuickComposeService
import pl.filebit.gymtracker.data.repository.RecoveryAnalyzer
import pl.filebit.gymtracker.data.repository.RecoveryRepository
import pl.filebit.gymtracker.data.repository.StatsCacheService
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.SubstituteService
import pl.filebit.gymtracker.data.repository.TrainingDietBridge
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WeeklyBudgetCalculator
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import pl.filebit.gymtracker.data.repository.WorkoutTimeAnalyzer
import pl.filebit.gymtracker.service.DietAutoAdjustmentScheduler
import pl.filebit.gymtracker.service.DietReminderScheduler
import pl.filebit.gymtracker.service.HealthConnectSyncScheduler

/**
 * v1.27 — FAZA 3 — buduje prawdziwy graf DI potrzebny ViewModelom
 * z in-memory Room (żaden mock). Analog [HomeDetectors] dla warstwy
 * snapshotów ekranów. Zbudowany RAZ kompletny — także ciężkie serwisy
 * AI (MasterAiContextBuilder, 17 zależności) — żeby ekrany AI-heavy
 * (Plans, Diet, AiTrainer) dało się skonstruować bez powielania grafu.
 */
class ViewModelKit(val db: AppDatabase, val context: Context) {

    // ── statystyki / zdarzenia ────────────────────────────────────────────
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
    private val periodRollup = PeriodRollupService(
        statsCacheService, db.bodyMeasurementDao(), db.trainingEventDao(),
        db.weeklyRollupDao(), db.monthlyRollupDao(), db.quarterlyRollupDao()
    )

    // ── repozytoria ───────────────────────────────────────────────────────
    val planRepo = PlanRepository(
        db.trainingPlanDao(), db.planExerciseDao(), db.planExerciseSetDao(),
        db.weeklyPlanOverrideDao(), eventDetector
    )
    val workoutRepo = WorkoutRepository(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        eventDetector, periodRollup
    )
    val exerciseRepo = ExerciseRepository(db.exerciseDao())
    val userProfileRepo = UserProfileRepository(db.userProfileDao())
    val dietProfileRepo = UserDietProfileRepository(userProfileRepo)
    val dietRepo = DietRepository(
        db.foodProductDao(), db.mealEntryDao(), db.fastingWindowDao(), db.recipeDao()
    )
    val mealFeedbackRepo = MealFeedbackRepository(db.mealFeedbackDao())
    val dietPhaseRepo = DietPhaseRepository(db.dietPhaseDao())
    val recoveryRepo = RecoveryRepository(db.recoveryLogDao())
    val activityRepo = ActivityRepository(db.dailyActivityLogDao())
    val hydrationRepo = HydrationRepository(db.hydrationLogDao())
    val mealConsumptionRepo = MealConsumptionRepository(db.mealConsumptionDao())
    val dietPrefs = DietPreferences(context)

    // ── most trening↔dieta ────────────────────────────────────────────────
    val trainingDietBridge = TrainingDietBridge(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        planRepo, db.trainingDaySummaryDao()
    )

    // ── analyzery ─────────────────────────────────────────────────────────
    val recoveryScoreCalculator = RecoveryScoreCalculator(db.recoveryLogDao())
    val trainingLoadAnalyzer = TrainingLoadAnalyzer(db.workoutDao(), db.workoutSetDao())
    val muscleRecoveryAnalyzer = MuscleRecoveryAnalyzer(
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao()
    )
    val readinessAnalyzer = TrainingReadinessAnalyzer(
        recoveryScoreCalculator, trainingLoadAnalyzer, muscleRecoveryAnalyzer
    )
    val phaseAnalyzer = TrainingPhaseAnalyzer(db.workoutDao(), db.workoutSetDao())
    val recoveryAnalyzer = RecoveryAnalyzer()
    val hydrationCalc = HydrationCalculator()
    val stagnationAnalyzer = StagnationAnalyzer(
        db.workoutDao(), db.workoutSetDao(), db.planExerciseDao(), db.exerciseDao()
    )
    val healthConnectManager = HealthConnectManager(context)
    val healthAnalyzer = HealthInsightAnalyzer(healthConnectManager, db.recoveryLogDao())

    val adherenceCalc = AdherenceCalculator(
        dietRepo, dietPrefs, userProfileRepo, dietProfileRepo, trainingDietBridge,
        db.bodyMeasurementDao(), db.adherenceLogDao(), mealConsumptionRepo
    )

    // ── AI ────────────────────────────────────────────────────────────────
    val aiPrefs = AiPreferences(context)
    val aiClient = AiClientImpl(AiLogRepository(db.aiLogDao()), aiPrefs)
    val workoutAiSummary = WorkoutAiSummaryService(
        aiClient, aiPrefs, db.workoutDao(), db.workoutSetDao(), db.exerciseDao()
    )
    val rpeOpinion = RpeOpinionService(
        aiClient, aiPrefs, db.workoutDao(), db.workoutSetDao(), db.exerciseDao()
    )
    val masterAiContext = MasterAiContextBuilder(
        userProfileRepo, dietProfileRepo, db.bodyMeasurementDao(), dietRepo,
        mealFeedbackRepo, exerciseRepo, adherenceCalc, recoveryRepo, recoveryAnalyzer,
        activityRepo, hydrationRepo, hydrationCalc, dietPhaseRepo, trainingDietBridge,
        muscleRecoveryAnalyzer, readinessAnalyzer, statsRepo
    )
    val aiPlanApplier = AiPlanApplier(planRepo, db.exerciseDao())
    val workoutPlanAi = WorkoutPlanAiService(
        aiClient, aiPrefs, exerciseRepo, userProfileRepo, dietProfileRepo,
        planRepo, masterAiContext, db
    )
    val planAuditService = PlanAuditService(
        aiClient, aiPrefs, db.trainingPlanDao(), db.planExerciseDao(),
        db.planExerciseSetDao(), db.exerciseDao(), userProfileRepo, aiPlanApplier,
        db.workoutDao(), masterAiContext, stagnationAnalyzer, phaseAnalyzer, healthAnalyzer
    )
    private val aiDecisionExplainer = AiDecisionExplainer(aiClient, aiPrefs, masterAiContext)
    val emergencyMealGen = EmergencyMealGenerator(
        aiClient, aiPrefs, userProfileRepo, dietProfileRepo, dietRepo, masterAiContext
    )

    // ── dieta — serwisy zaawansowane (ekran Diet) ─────────────────────────
    val cardioKcalEstimator = CardioKcalEstimator(trainingDietBridge)
    val dietVolatilityAnalyzer = DietVolatilityAnalyzer(db.adherenceLogDao())
    val substituteService = SubstituteService()
    val qualityScorer = DailyQualityScorer()
    val weeklyBudgetCalc = WeeklyBudgetCalculator()
    val phaseManager = PhaseManager()
    val damageControl = DamageControl()
    private val neatAnalyzer = NeatAnalyzer()
    val quickComposeService = QuickComposeService()
    private val constraintResolver = ConstraintResolver()
    private val aiMealJsonValidator = AiMealJsonValidator(constraintResolver)
    private val workoutTimeAnalyzer = WorkoutTimeAnalyzer()
    private val dietaryKnowledgeRepo = DietaryKnowledgeRepository(context)
    val autoAdjust = AutoAdjustmentService(
        userProfileRepo, dietProfileRepo, dietPrefs, db.bodyMeasurementDao(),
        adherenceCalc, db.dietAdjustmentDao(), aiDecisionExplainer, recoveryRepo,
        recoveryAnalyzer, hydrationRepo, hydrationCalc, activityRepo, neatAnalyzer
    )
    val dietAiService = DietAiService(
        aiClient, aiPrefs, userProfileRepo, dietProfileRepo, dietRepo, workoutRepo,
        planRepo, statsRepo, trainingDietBridge, db.bodyMeasurementDao(), mealFeedbackRepo,
        constraintResolver, aiMealJsonValidator, workoutTimeAnalyzer, dietaryKnowledgeRepo,
        masterAiContext
    )
    val dietReminderScheduler = DietReminderScheduler(context)
    val dietAutoAdjustmentScheduler = DietAutoAdjustmentScheduler(context)
    val healthConnectScheduler = HealthConnectSyncScheduler(context)

    // ── AI — ekrany (Trener, rozmowy, raport tygodniowy) ──────────────────
    val bodyRepo = BodyRepository(db.bodyMeasurementDao())
    val strengthRepo = StrengthRepository(
        db.exerciseDao(), db.workoutSetDao(), statsRepo, userProfileRepo, bodyRepo
    )
    val progressPhotoRepo = ProgressPhotoRepository(db.progressPhotoDao(), context)
    val goalRepo = GoalRepository(db.goalDao(), bodyRepo, db.workoutSetDao(), statsRepo)
    val aiChatRepo = AiChatRepository(db.aiConversationDao(), db.aiChatMessageDao())
    val aiLogRepo = AiLogRepository(db.aiLogDao())
    val aiContextBuilder = AiContextBuilder(
        userProfileRepo, bodyRepo, statsRepo, strengthRepo, progressPhotoRepo, goalRepo,
        db.trainingPlanDao(), db.planExerciseDao(), db.planExerciseSetDao(),
        db.workoutDao(), db.workoutSetDao(), db.exerciseDao(), statsCacheService,
        db.trainingEventDao(), db.weeklyRollupDao(), db.monthlyRollupDao(),
        db.quarterlyRollupDao()
    )
    val aiToolHandler = AiToolHandler(
        statsCacheService, db.bodyMeasurementDao(), db.trainingEventDao(),
        db.weeklyRollupDao(), db.monthlyRollupDao(), db.quarterlyRollupDao(),
        db.trainingMesocycleDao(), db.pendingPeriodizationDecisionDao(),
        db.exerciseDao(), db.userProfileDao(),
        // v2.22.0 — narzędzia zapisu
        dietRepo,
        userProfileRepo,
        dietPrefs,
        pl.filebit.gymtracker.data.repository.DiagnosticLogger(db.diagnosticEventDao()),
        // v2.23.0 (K5) — recompute adherence po add_meal
        adherenceCalc
    )
    val weeklyReportService = WeeklyReportService(
        aiClient, aiPrefs, db.workoutDao(), db.workoutSetDao(), db.exerciseDao(),
        db.aiWeeklyReportDao(), statsRepo, userProfileRepo, planRepo,
        db.planExerciseDao(), db.planExerciseSetDao(), aiPlanApplier, masterAiContext
    )
}
