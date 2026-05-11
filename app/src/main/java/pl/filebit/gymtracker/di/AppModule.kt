package pl.filebit.gymtracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.db.dao.AiChatMessageDao
import pl.filebit.gymtracker.data.db.dao.AiConversationDao
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.UnlockedAchievementDao
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.GoalDao
import pl.filebit.gymtracker.data.db.dao.ProgressPhotoDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.db.dao.UserProfileDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.ai.AiClient
import pl.filebit.gymtracker.ai.AiClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Migracja 49→50 (v1.2.0): dodaje Exercise.isAvoided. Bez kasowania danych —
     * od tego release'u trzymamy zachowanie historyczne (treningi, logi AI, ćwiczenia).
     */
    private val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN isAvoided INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migracja 50→51 (v1.7.2): rozszerzony RecoveryLog o metryki ze smartwatcha
     * (tętno spoczynkowe, SpO2, HRV, VO2Max, kroki, kalorie aktywne).
     * Wszystkie kolumny nullable — brak kasowania danych.
     */
    private val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN restingHeartRateBpm INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN spO2Pct INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN hrvMs REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN vo2max REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN stepsCount INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE recovery_logs ADD COLUMN activeCalories INTEGER DEFAULT NULL")
        }
    }

    /**
     * Migracja 51→52 (v1.13.0 / audit 2026-05-10):
     * Tworzy 2 tabele dodane w v1.11.59-65 a brakujące w explicite migracjach:
     *  - training_events (pamięć epizodyczna AI: PR, INJURY, DELOAD, PLAN_*, GAP_RESUMED, CYCLE_MILESTONE)
     *  - ai_logs (audit trail wywołań AI: prompt + response + tokens)
     *
     * Bez tej migracji: użytkownicy z bazą v51 dostawali silent wipe przez fallbackToDestructiveMigration.
     */
    private val MIGRATION_51_52 = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // training_events — entity TrainingEvent (v1.11.59)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `workoutId` INTEGER,
                    `exerciseId` INTEGER,
                    `planId` INTEGER,
                    `exerciseName` TEXT,
                    `weightKg` REAL,
                    `reps` INTEGER,
                    `e1rmKg` REAL,
                    `area` TEXT,
                    `planName` TEXT,
                    `weeksContext` INTEGER,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_events_date` ON `training_events` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_events_type` ON `training_events` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_events_workoutId` ON `training_events` (`workoutId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_events_exerciseId` ON `training_events` (`exerciseId`)")

            // ai_logs — entity AiLog
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `service` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `fullPrompt` TEXT NOT NULL,
                    `fullResponse` TEXT NOT NULL,
                    `success` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `durationMs` INTEGER NOT NULL,
                    `inputTokens` INTEGER,
                    `outputTokens` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_logs_createdAt` ON `ai_logs` (`createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_logs_service` ON `ai_logs` (`service`)")
        }
    }

    /**
     * Migracja 52→53 (v1.13.0 / audit 2026-05-10):
     * Tworzy 4 tabele dodane w v1.11.66 a brakujące w explicite migracjach:
     *  - weekly_rollups, monthly_rollups, quarterly_rollups (period rollups dla AI context)
     *  - meal_consumptions (status posiłku per slot per dzień: PLANNED/CONSUMED/SKIPPED)
     */
    private val MIGRATION_52_53 = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // weekly_rollups
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weekly_rollups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `weekStartMs` INTEGER NOT NULL,
                    `totalVolumeKg` REAL NOT NULL,
                    `sessionsCount` INTEGER NOT NULL,
                    `totalSets` INTEGER NOT NULL,
                    `avgRpe` REAL NOT NULL,
                    `avgWellbeing` REAL,
                    `mainLiftsBestJson` TEXT NOT NULL,
                    `muscleVolumePctsJson` TEXT NOT NULL,
                    `autoNotes` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_rollups_weekStartMs` ON `weekly_rollups` (`weekStartMs`)")

            // monthly_rollups
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `monthly_rollups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `monthStartMs` INTEGER NOT NULL,
                    `totalVolumeKg` REAL NOT NULL,
                    `sessionsCount` INTEGER NOT NULL,
                    `totalSets` INTEGER NOT NULL,
                    `avgRpe` REAL NOT NULL,
                    `mainLiftsE1rmEndJson` TEXT NOT NULL,
                    `bodyWeightEndKg` REAL,
                    `bodyWeightDeltaKg` REAL,
                    `prCount` INTEGER NOT NULL,
                    `planChanges` INTEGER NOT NULL,
                    `deloadCount` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_rollups_monthStartMs` ON `monthly_rollups` (`monthStartMs`)")

            // quarterly_rollups
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quarterly_rollups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `quarterStartMs` INTEGER NOT NULL,
                    `totalVolumeKg` REAL NOT NULL,
                    `sessionsCount` INTEGER NOT NULL,
                    `mainLiftsE1rmStartJson` TEXT NOT NULL,
                    `mainLiftsE1rmEndJson` TEXT NOT NULL,
                    `bodyWeightDeltaKg` REAL,
                    `prCount` INTEGER NOT NULL,
                    `planChanges` INTEGER NOT NULL,
                    `deloadCount` INTEGER NOT NULL,
                    `highlightsJson` TEXT NOT NULL DEFAULT '[]',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quarterly_rollups_quarterStartMs` ON `quarterly_rollups` (`quarterStartMs`)")

            // meal_consumptions
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `meal_consumptions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateMs` INTEGER NOT NULL,
                    `mealType` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `notedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_consumptions_dateMs_mealType` ON `meal_consumptions` (`dateMs`, `mealType`)")
        }
    }

    /**
     * Migracja 53→54 (v1.13.0 / audit 2026-05-10):
     * Fundament periodyzacji (entity TrainingMesocycle + linkage do trzech istniejących tabel).
     *  - CREATE training_mesocycles (datowane cykle z fazami ACCUMULATION/INTENSIFICATION/DELOAD/PEAKING/RECOVERY)
     *  - ALTER training_day_summary ADD mesocycleId — powiązanie dziennego podsumowania z cyklem
     *  - ALTER diet_phases ADD linkedTrainingMesocycleId + trainingPhaseSnapshot — synchronizacja dieta↔trening
     *  - ALTER goals ADD targetMesocycleId — cel powiązany z konkretnym cyklem
     *
     * Wszystkie nowe kolumny ALTER są nullable — bezpieczne dla istniejących wierszy.
     */
    private val MIGRATION_53_54 = object : Migration(53, 54) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // training_mesocycles — fundament periodyzacji
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_mesocycles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startDateMs` INTEGER NOT NULL,
                    `endDateMs` INTEGER,
                    `plannedEndDateMs` INTEGER NOT NULL,
                    `phase` TEXT NOT NULL,
                    `weekInPhase` INTEGER NOT NULL DEFAULT 1,
                    `phaseLengthWeeks` INTEGER NOT NULL DEFAULT 3,
                    `trainingPlanId` INTEGER,
                    `volumeProgression` REAL NOT NULL DEFAULT 1.0,
                    `intensityProgression` REAL NOT NULL DEFAULT 1.0,
                    `targetRpe` INTEGER NOT NULL DEFAULT 8,
                    `triggerReason` TEXT NOT NULL DEFAULT '',
                    `aiAcceptedDecision` TEXT NOT NULL DEFAULT '',
                    `aiConfidence` REAL,
                    `status` TEXT NOT NULL DEFAULT 'PLANNED',
                    `notes` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_mesocycles_startDateMs` ON `training_mesocycles` (`startDateMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_mesocycles_status` ON `training_mesocycles` (`status`)")

            // ALTER istniejących tabel — powiązanie z TrainingMesocycle
            db.execSQL("ALTER TABLE `training_day_summary` ADD COLUMN `mesocycleId` INTEGER DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_day_summary_mesocycleId` ON `training_day_summary` (`mesocycleId`)")

            db.execSQL("ALTER TABLE `diet_phases` ADD COLUMN `linkedTrainingMesocycleId` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `diet_phases` ADD COLUMN `trainingPhaseSnapshot` TEXT DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diet_phases_linkedTrainingMesocycleId` ON `diet_phases` (`linkedTrainingMesocycleId`)")

            db.execSQL("ALTER TABLE `goals` ADD COLUMN `targetMesocycleId` INTEGER DEFAULT NULL")
        }
    }

    /**
     * Migracja 54→55 (v1.15.0 / audit 2026-05-10):
     * Dodaje entity PendingPeriodizationDecision — audit trail decyzji AI o cyklu.
     * AI proponuje, user widzi w karcie "AI TRENER PROPONUJE", explicit Apply/Modify/Dismiss.
     *
     * ADDITIVE — tylko CREATE TABLE, brak ALTER → bezpieczne dla istniejących wierszy.
     * Lekcja z v1.13.0: nowe pola wymagają entity match. Tu pole NIE jest dodawane do
     * istniejących tabel — tylko nowa tabela, więc nie ma ryzyka schema mismatch.
     */
    private val MIGRATION_54_55 = object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_periodization_decisions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `currentMesoId` INTEGER NOT NULL,
                    `algorithmProposalJson` TEXT NOT NULL,
                    `aiDecisionJson` TEXT NOT NULL,
                    `aiReasoning` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `status` TEXT NOT NULL DEFAULT 'PENDING',
                    `resolvedAt` INTEGER,
                    `resolvedAction` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_periodization_decisions_status` ON `pending_periodization_decisions` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_periodization_decisions_createdAt` ON `pending_periodization_decisions` (`createdAt`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                MIGRATION_49_50,
                MIGRATION_50_51,
                MIGRATION_51_52,
                MIGRATION_52_53,
                MIGRATION_53_54,
                MIGRATION_54_55
            )
            // v1.13.0 (audit 2026-05-10): USUNIĘTO fallbackToDestructiveMigration(true).
            // Wcześniej każda zmiana schematu bez explicite migracji = silent WIPE danych
            // (najczęściej v1.11.59-66 dodały tabele bez migracji). Po dodaniu 51→54
            // mamy kompletną ścieżkę z v49 do v54 — Room sam migruje zachowując dane.
            //
            // Jeśli user ma bazę v < 49 (bardzo stara, sprzed v1.2.0) — Room rzuci
            // IllegalStateException przy starcie. Lepsze niż utrata 3.5 lat danych.
            //
            // Awaryjny fallback przy DOWNGRADE (rare — user instaluje starszą wersję):
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideWorkoutSetDao(db: AppDatabase): WorkoutSetDao = db.workoutSetDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideTrainingPlanDao(db: AppDatabase): TrainingPlanDao = db.trainingPlanDao()
    @Provides fun providePlanExerciseDao(db: AppDatabase): PlanExerciseDao = db.planExerciseDao()
    @Provides fun providePlanExerciseSetDao(db: AppDatabase): PlanExerciseSetDao = db.planExerciseSetDao()
    @Provides fun provideBodyMeasurementDao(db: AppDatabase): BodyMeasurementDao = db.bodyMeasurementDao()
    @Provides fun provideProgressPhotoDao(db: AppDatabase): ProgressPhotoDao = db.progressPhotoDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun provideAiConversationDao(db: AppDatabase): AiConversationDao = db.aiConversationDao()
    @Provides fun provideAiChatMessageDao(db: AppDatabase): AiChatMessageDao = db.aiChatMessageDao()
    @Provides fun provideAiWeeklyReportDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao = db.aiWeeklyReportDao()
    @Provides fun provideWeeklyPlanOverrideDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.WeeklyPlanOverrideDao = db.weeklyPlanOverrideDao()
    @Provides fun provideUnlockedAchievementDao(db: AppDatabase): UnlockedAchievementDao = db.unlockedAchievementDao()
    @Provides fun provideFoodProductDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.FoodProductDao = db.foodProductDao()
    @Provides fun provideMealEntryDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MealEntryDao = db.mealEntryDao()
    @Provides fun provideFastingWindowDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.FastingWindowDao = db.fastingWindowDao()
    @Provides fun provideRecipeDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.RecipeDao = db.recipeDao()
    @Provides fun provideUserDietProfileDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.UserDietProfileDao = db.userDietProfileDao()
    @Provides fun provideTrainingDaySummaryDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.TrainingDaySummaryDao = db.trainingDaySummaryDao()
    @Provides fun provideAdherenceLogDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.AdherenceLogDao = db.adherenceLogDao()
    @Provides fun provideDietAdjustmentDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.DietAdjustmentDao = db.dietAdjustmentDao()
    @Provides fun provideMealFeedbackDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MealFeedbackDao = db.mealFeedbackDao()
    @Provides fun provideShoppingListDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.ShoppingListDao = db.shoppingListDao()
    @Provides fun provideHydrationLogDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.HydrationLogDao = db.hydrationLogDao()
    @Provides fun provideRecoveryLogDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.RecoveryLogDao = db.recoveryLogDao()
    @Provides fun provideDailyActivityLogDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.DailyActivityLogDao = db.dailyActivityLogDao()
    @Provides fun provideDietPhaseDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.DietPhaseDao = db.dietPhaseDao()
    @Provides fun provideMealPrepPlanDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MealPrepPlanDao = db.mealPrepPlanDao()
    @Provides fun provideMealConsumptionDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MealConsumptionDao = db.mealConsumptionDao()
    @Provides fun provideAiLogDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.AiLogDao = db.aiLogDao()
    @Provides fun provideTrainingEventDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.TrainingEventDao = db.trainingEventDao()
    @Provides fun provideWeeklyRollupDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.WeeklyRollupDao = db.weeklyRollupDao()
    @Provides fun provideMonthlyRollupDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MonthlyRollupDao = db.monthlyRollupDao()
    @Provides fun provideQuarterlyRollupDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.QuarterlyRollupDao = db.quarterlyRollupDao()
    @Provides fun provideTrainingMesocycleDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao = db.trainingMesocycleDao()
    @Provides fun providePendingPeriodizationDecisionDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.PendingPeriodizationDecisionDao = db.pendingPeriodizationDecisionDao()

    @Provides
    @Singleton
    fun provideExerciseSeeder(
        @ApplicationContext context: Context,
        dao: ExerciseDao
    ): ExerciseSeeder = ExerciseSeeder(context, dao)

    @Provides
    @Singleton
    fun provideFoodProductSeeder(
        @ApplicationContext context: Context,
        dao: pl.filebit.gymtracker.data.db.dao.FoodProductDao
    ): pl.filebit.gymtracker.data.seed.FoodProductSeeder =
        pl.filebit.gymtracker.data.seed.FoodProductSeeder(context, dao)

    @Provides
    @Singleton
    fun provideAiClient(impl: AiClientImpl): AiClient = impl
}
