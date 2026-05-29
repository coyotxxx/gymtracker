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
    internal val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN isAvoided INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migracja 50→51 (v1.7.2): rozszerzony RecoveryLog o metryki ze smartwatcha
     * (tętno spoczynkowe, SpO2, HRV, VO2Max, kroki, kalorie aktywne).
     * Wszystkie kolumny nullable — brak kasowania danych.
     */
    internal val MIGRATION_50_51 = object : Migration(50, 51) {
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
    internal val MIGRATION_51_52 = object : Migration(51, 52) {
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
    internal val MIGRATION_52_53 = object : Migration(52, 53) {
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
    internal val MIGRATION_53_54 = object : Migration(53, 54) {
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
    internal val MIGRATION_54_55 = object : Migration(54, 55) {
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

    /**
     * v1.20.0 — ADDITIVE migration: CREATE INDEX dla wyszukiwań w bibliotece ćwiczeń i diecie.
     * Wszystkie indices dodawane przez `CREATE INDEX IF NOT EXISTS` — idempotentne, bezpieczne
     * dla danych usera (żadne ALTER TABLE / DROP / data manipulation).
     */
    internal val MIGRATION_55_56 = object : Migration(55, 56) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Exercise — search po nazwie (Library), filter po muscle, ulubione w AI generatorze
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_primaryMuscle` ON `exercises` (`primaryMuscle`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_isFavorite` ON `exercises` (`isFavorite`)")
            // FoodProduct — search po nazwie (Diet picker), filter category, ulubione, barcode lookup
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_name` ON `food_products` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_category` ON `food_products` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_isFavorite` ON `food_products` (`isFavorite`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_barcode` ON `food_products` (`barcode`)")
        }
    }

    /**
     * v1.24.12 — ADDITIVE: TrainingPlan.isActive (jeden user, jeden aktywny plan).
     * Schema dodaje kolumnę z DEFAULT 0. Backfill: najnowszy plan (max createdAt)
     * staje się aktywnym — żeby user który ma już plany nie obudził się z brakiem
     * "aktywnego" po update.
     */
    internal val MIGRATION_56_57 = object : Migration(56, 57) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `training_plans` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
            // Backfill: oznacz najnowszy plan jako aktywny (jeśli istnieje)
            db.execSQL(
                """
                UPDATE `training_plans`
                SET `isActive` = 1
                WHERE `id` = (SELECT `id` FROM `training_plans` ORDER BY `createdAt` DESC LIMIT 1)
                """.trimIndent()
            )
        }
    }

    /**
     * v1.25.0 (audit 2026-05-14): ExerciseDB integracja — 1500 ćwiczeń z GIFami z CDN.
     * Dodaje 8 nowych kolumn do `exercises` + index na `externalId` dla szybkiego lookupu.
     * Bootstrap przy starcie wczyta `assets/exercisedb_v1.json` i fuzzy-zmatchuje
     * istniejące ćwiczenia z bazą EN — nadpisując externalId/gifUrl/instructions.
     * Istniejące dane usera (name, primaryMuscle, equipment, isFavorite, isAvoided) zostają.
     */
    internal val MIGRATION_57_58 = object : Migration(57, 58) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `externalId` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `gifUrl` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `instructionsEnJson` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `instructionsPlJson` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `targetMusclesCsv` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `secondaryMusclesCsv` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `equipmentDbCsv` TEXT")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `bodyPartCsv` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_externalId` ON `exercises` (`externalId`)")
        }
    }

    // v1.26.2 — preferowane grupy mięśniowe ("obszar zainteresowania")
    internal val MIGRATION_58_59 = object : Migration(58, 59) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `preferredMuscleGroupsCsv` TEXT NOT NULL DEFAULT ''")
        }
    }

    // v1.26.5 — praktyczne kategorie sprzętu (EquipmentCategory)
    internal val MIGRATION_59_60 = object : Migration(59, 60) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `equipmentCategoriesCsv` TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v1.28 (refaktor "jedno źródło prawdy", Etap 1 — docs/CONFIG-UNIFICATION-PLAN.md):
     * scala encje `UserProfile` + `UserDietProfile` w jedną tabelę `user_profile`.
     *
     * 1) ALTER TABLE — dodaje 22 kolumny diety do `user_profile` (wszystkie z DEFAULT,
     *    żeby istniejące wiersze były poprawne i ALTER NOT NULL przeszedł).
     * 2) UPDATE — kopiuje wiersz z `user_diet_profile` (id=1) do `user_profile` (id=1).
     *    Guard `(SELECT COUNT(*) ...) > 0` chroni usera który nigdy nie otwierał
     *    diety (brak wiersza) — bez guardu subquery zwróciłaby NULL do kolumn NOT NULL.
     *
     * Tabela `user_diet_profile` ZOSTAJE w bazie (osierocona — Room toleruje nieznane
     * tabele). Bezpiecznik: dane fizycznie są aż do Etapu 5, cofnięcie możliwe.
     */
    internal val MIGRATION_60_61 = object : Migration(60, 61) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `ageYears` INTEGER NOT NULL DEFAULT 30")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `heightCm` INTEGER NOT NULL DEFAULT 175")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `activityLevel` TEXT NOT NULL DEFAULT 'MODERATE'")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `avgStepsPerDay` INTEGER NOT NULL DEFAULT 7000")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `goalType` TEXT NOT NULL DEFAULT 'MAINTAIN'")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `paceKgPerWeek` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `customDeficitKcal` INTEGER")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dietPreference` TEXT NOT NULL DEFAULT 'STANDARD'")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `allergies` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `intolerances` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dislikedFoods` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `lovedFoods` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `cookingTimePerMealMin` INTEGER NOT NULL DEFAULT 15")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `eatsAtWork` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `hasMicrowaveAtWork` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `mealPrepInterested` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `weeklyBudgetPln` INTEGER")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `medicalConditions` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `medicalAwareness` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `usualTrainingHour` INTEGER")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dietOnboardingCompletedAt` INTEGER")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dietUpdatedAt` INTEGER NOT NULL DEFAULT 0")

            // Kopiowanie danych diety do scalonego wiersza (tylko jeśli wiersz diety istnieje).
            db.execSQL(
                """
                UPDATE `user_profile` SET
                    `ageYears` = (SELECT `ageYears` FROM `user_diet_profile` WHERE `id` = 1),
                    `heightCm` = (SELECT `heightCm` FROM `user_diet_profile` WHERE `id` = 1),
                    `activityLevel` = (SELECT `activityLevel` FROM `user_diet_profile` WHERE `id` = 1),
                    `avgStepsPerDay` = (SELECT `avgStepsPerDay` FROM `user_diet_profile` WHERE `id` = 1),
                    `goalType` = (SELECT `goalType` FROM `user_diet_profile` WHERE `id` = 1),
                    `paceKgPerWeek` = (SELECT `paceKgPerWeek` FROM `user_diet_profile` WHERE `id` = 1),
                    `customDeficitKcal` = (SELECT `customDeficitKcal` FROM `user_diet_profile` WHERE `id` = 1),
                    `dietPreference` = (SELECT `dietPreference` FROM `user_diet_profile` WHERE `id` = 1),
                    `allergies` = (SELECT `allergies` FROM `user_diet_profile` WHERE `id` = 1),
                    `intolerances` = (SELECT `intolerances` FROM `user_diet_profile` WHERE `id` = 1),
                    `dislikedFoods` = (SELECT `dislikedFoods` FROM `user_diet_profile` WHERE `id` = 1),
                    `lovedFoods` = (SELECT `lovedFoods` FROM `user_diet_profile` WHERE `id` = 1),
                    `cookingTimePerMealMin` = (SELECT `cookingTimePerMealMin` FROM `user_diet_profile` WHERE `id` = 1),
                    `eatsAtWork` = (SELECT `eatsAtWork` FROM `user_diet_profile` WHERE `id` = 1),
                    `hasMicrowaveAtWork` = (SELECT `hasMicrowaveAtWork` FROM `user_diet_profile` WHERE `id` = 1),
                    `mealPrepInterested` = (SELECT `mealPrepInterested` FROM `user_diet_profile` WHERE `id` = 1),
                    `weeklyBudgetPln` = (SELECT `weeklyBudgetPln` FROM `user_diet_profile` WHERE `id` = 1),
                    `medicalConditions` = (SELECT `medicalConditions` FROM `user_diet_profile` WHERE `id` = 1),
                    `medicalAwareness` = (SELECT `medicalAwareness` FROM `user_diet_profile` WHERE `id` = 1),
                    `usualTrainingHour` = (SELECT `usualTrainingHour` FROM `user_diet_profile` WHERE `id` = 1),
                    `dietOnboardingCompletedAt` = (SELECT `onboardingCompletedAt` FROM `user_diet_profile` WHERE `id` = 1),
                    `dietUpdatedAt` = (SELECT `updatedAt` FROM `user_diet_profile` WHERE `id` = 1)
                WHERE `id` = 1 AND (SELECT COUNT(*) FROM `user_diet_profile` WHERE `id` = 1) > 0
                """.trimIndent()
            )
        }
    }

    /**
     * v1.28.1 (refaktor "jedno źródło prawdy", Etap 2): jeden cel.
     *
     * Brak zmian SCHEMATU — migracja tylko UZGADNIA dane. Po Etapie 1 użytkownik
     * mógł mieć rozjechane pola: `weightGoalType` (cel z Ustawień treningu) vs
     * `goalType` (cel diety, często nietknięty default MAINTAIN). Od Etapu 2
     * `goalType` jest jedynym źródłem prawdy. Promujemy intencjonalny cel z
     * `weightGoalType` (CUT/BULK) do `goalType` TYLKO gdy `goalType` to wciąż
     * domyślny MAINTAIN — czyli dieta nie była świadomie konfigurowana.
     * Jeśli user ustawił świadomie cel diety (goalType != MAINTAIN) — szanujemy go.
     */
    internal val MIGRATION_61_62 = object : Migration(61, 62) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE `user_profile` SET `goalType` = CASE
                    WHEN `goalType` = 'MAINTAIN' AND `weightGoalType` = 'CUT'  THEN 'FAT_LOSS'
                    WHEN `goalType` = 'MAINTAIN' AND `weightGoalType` = 'BULK' THEN 'MUSCLE_GAIN'
                    ELSE `goalType`
                END
                WHERE `id` = 1
                """.trimIndent()
            )
            // Normalizacja legacy mirror na podstawie (uzgodnionego) goalType.
            db.execSQL(
                """
                UPDATE `user_profile` SET `weightGoalType` = CASE
                    WHEN `goalType` IN ('FAT_LOSS', 'EVENT_PREP') THEN 'CUT'
                    WHEN `goalType` = 'MUSCLE_GAIN' THEN 'BULK'
                    ELSE 'MAINTAIN'
                END
                WHERE `id` = 1
                """.trimIndent()
            )
        }
    }

    /**
     * v1.28.4 (refaktor "jedno źródło prawdy", Etap 5 — finał): usunięcie
     * osieroconej tabeli `user_diet_profile`.
     *
     * Od Etapu 1 (migracja 60→61) dane diety żyją w scalonej tabeli `user_profile`.
     * Stara tabela była celowo zostawiona jako bezpiecznik — przez 4 release'y
     * (v1.28.0-v1.28.3) potwierdzono w boju że scalenie działa. Teraz ją usuwamy.
     * `DROP TABLE IF EXISTS` — idempotentne, bezpieczne (tabela nieużywana, brak FK).
     */
    internal val MIGRATION_62_63 = object : Migration(62, 63) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `user_diet_profile`")
        }
    }

    /**
     * v1.29.6: produkty zbożowe w bazie były w formie GOTOWANEJ. Przejście na
     * SUROWE — waga ZAWSZE przed ugotowaniem (lista zakupów, instrukcje, makra).
     * Gramy istniejących wpisów posiłków przeliczamy współczynnikiem
     * cookedKcal/rawKcal, dzięki czemu kcal historycznych posiłków NIE zmieniają
     * się (np. 237 g ryżu gotowanego → 82 g surowego, te same kalorie).
     */
    internal val MIGRATION_63_64 = object : Migration(63, 64) {
        override fun migrate(db: SupportSQLiteDatabase) {
            fun toRaw(
                cooked: String, raw: String, cookedKcal: Double,
                kcal: Double, prot: Double, carbs: Double, fat: Double,
                convertGrams: Boolean
            ) {
                if (convertGrams) {
                    db.execSQL(
                        "UPDATE meal_entries SET grams = grams * ? " +
                            "WHERE productId IN (SELECT id FROM food_products WHERE name = ?)",
                        arrayOf<Any?>(cookedKcal / kcal, cooked)
                    )
                }
                db.execSQL(
                    "UPDATE food_products SET name = ?, kcalPer100g = ?, " +
                        "proteinPer100g = ?, carbsPer100g = ?, fatPer100g = ? WHERE name = ?",
                    arrayOf<Any?>(raw, kcal, prot, carbs, fat, cooked)
                )
            }
            // Zboża — duża zmiana wagi po ugotowaniu → przeliczamy gramy wpisów.
            toRaw("Ryż biały gotowany", "Ryż biały", 130.0, 350.0, 7.0, 78.0, 0.6, true)
            toRaw("Ryż brązowy gotowany", "Ryż brązowy", 123.0, 360.0, 7.5, 76.0, 2.7, true)
            toRaw("Ryż basmati gotowany", "Ryż basmati", 121.0, 350.0, 8.0, 78.0, 1.0, true)
            toRaw("Makaron pełnoziarnisty gotowany", "Makaron pełnoziarnisty", 124.0, 340.0, 13.0, 64.0, 2.5, true)
            toRaw("Makaron biały gotowany", "Makaron biały", 131.0, 360.0, 12.0, 72.0, 1.5, true)
            toRaw("Kasza gryczana gotowana", "Kasza gryczana", 92.0, 340.0, 13.0, 70.0, 3.4, true)
            toRaw("Kasza jaglana gotowana", "Kasza jaglana", 119.0, 360.0, 11.0, 72.0, 4.0, true)
            toRaw("Kasza pęczak gotowana", "Kasza pęczak", 123.0, 350.0, 10.0, 77.0, 2.3, true)
            // Warzywa / ziemniaki / krewetki — waga prawie bez zmian → bez przeliczania gramów.
            toRaw("Ziemniaki gotowane", "Ziemniaki", 87.0, 77.0, 2.0, 17.0, 0.1, false)
            toRaw("Krewetki gotowane", "Krewetki", 99.0, 85.0, 20.0, 0.0, 0.5, false)
            toRaw("Brokuły gotowane", "Brokuły", 35.0, 34.0, 2.8, 7.0, 0.4, false)
            toRaw("Kalafior gotowany", "Kalafior", 23.0, 25.0, 1.9, 5.0, 0.3, false)
            toRaw("Buraki gotowane", "Buraki", 44.0, 43.0, 1.6, 10.0, 0.2, false)
            toRaw("Brukselka gotowana", "Brukselka", 36.0, 43.0, 3.4, 9.0, 0.3, false)
        }
    }

    // v1.29.25: searchAliases — dodatkowe terminy wyszukiwania (PL↔EN).
    internal val MIGRATION_64_65 = object : Migration(64, 65) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN searchAliases TEXT")
        }
    }

    /**
     * v2.0.0 — całkowita wymiana bazy ćwiczeń (ExerciseDB → canonical exercise-db).
     *
     * Strategia: backup nazw ćwiczeń z historii (workout_sets/plan_exercises/goals/
     * training_events) do tabel `_backup_*`, czyszczenie istniejących FK referencji,
     * DELETE wszystkich row-ów exercises (1500 ExerciseDB), ADD 21 nowych kolumn
     * canonical schema. Po migracji `CanonicalExerciseBootstrap` ładuje 1317 canonical
     * z assets/exercises_canonical/ i przemapuje historię przez fuzzy match po nazwie.
     */
    internal val MIGRATION_65_66 = object : Migration(65, 66) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Backup historii z nazwami ćwiczeń (do reimportu po canonical insert)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS _backup_workout_sets AS
                SELECT ws.*, e.name AS _legacy_exercise_name
                FROM workout_sets ws
                LEFT JOIN exercises e ON ws.exerciseId = e.id
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS _backup_plan_exercises AS
                SELECT pe.*, e.name AS _legacy_exercise_name
                FROM plan_exercises pe
                LEFT JOIN exercises e ON pe.exerciseId = e.id
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS _backup_goals AS
                SELECT g.*, e.name AS _legacy_exercise_name
                FROM goals g
                LEFT JOIN exercises e ON g.exerciseId = e.id
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS _backup_training_events AS
                SELECT te.*, e.name AS _legacy_exercise_name
                FROM training_events te
                LEFT JOIN exercises e ON te.exerciseId = e.id
            """.trimIndent())

            // 2. Wyłączenie FK (workout_sets/plan_exercises mają RESTRICT)
            db.execSQL("PRAGMA foreign_keys = OFF")

            // 3. DELETE historii (zostaną reimportowane przez bootstrap)
            db.execSQL("DELETE FROM workout_sets")
            db.execSQL("DELETE FROM plan_exercises")
            db.execSQL("UPDATE goals SET exerciseId = NULL WHERE exerciseId IS NOT NULL")
            db.execSQL("UPDATE training_events SET exerciseId = NULL WHERE exerciseId IS NOT NULL")

            // 4. DELETE starych ExerciseDB rows
            db.execSQL("DELETE FROM exercises")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'exercises'")

            // 5. ADD nowe kolumny canonical (wszystkie nullable — bezpieczne)
            db.execSQL("ALTER TABLE exercises ADD COLUMN slug TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN namePl TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN descriptionPl TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN aliasesEnJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN aliasesPlJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN framesDirUrl TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN category TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN movementPattern TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN mechanic TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN force TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN kineticChain TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN plane TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN laterality TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN levelMin TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN difficulty1To10 INTEGER")
            db.execSQL("ALTER TABLE exercises ADD COLUMN muscleIntensityJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN coachingCuesJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN commonFaultsJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN contraindicationsJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN prerequisitesJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN progressionToJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN alternativesJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN tagsJson TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN equipmentOptionalCsv TEXT")

            // 6. Index na slug (UNIQUE) + category + movementPattern
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_slug ON exercises(slug)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_category ON exercises(category)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_movementPattern ON exercises(movementPattern)")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * v2.3.0 — dodaje kolumne `searchIndex` (pre-computowany index PL+EN+ASCII-fold).
     * Wartosc wypelni CanonicalExerciseBootstrap przy starcie (idempotent UPDATE).
     */
    internal val MIGRATION_66_67 = object : Migration(66, 67) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v2.3.2 FIX: TYLKO ADD COLUMN. NIE tworzyć indeksu na searchIndex —
            // entity Exercise NIE deklaruje @Index(searchIndex), więc Room schema
            // validation odrzuciłby extra index ("Migration didn't properly handle")
            // → crash przy starcie po update. Index i tak bezużyteczny dla LIKE '%q%'.
            // DROP IF EXISTS sprząta po nieudanej migracji z v2.3.0/v2.3.1.
            db.execSQL("DROP INDEX IF EXISTS index_exercises_searchIndex")
            db.execSQL("ALTER TABLE exercises ADD COLUMN searchIndex TEXT")
        }
    }

    // v2.10.0: własny dźwięk końca przerwy. Kolumna nullable (null = domyślne beepy).
    internal val MIGRATION_67_68 = object : Migration(67, 68) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `restSoundUri` TEXT")
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
                MIGRATION_54_55,
                MIGRATION_55_56,
                MIGRATION_56_57,
                MIGRATION_57_58,
                MIGRATION_58_59,
                MIGRATION_59_60,
                MIGRATION_60_61,
                MIGRATION_61_62,
                MIGRATION_62_63,
                MIGRATION_63_64,
                MIGRATION_64_65,
                MIGRATION_65_66,
                MIGRATION_66_67,
                MIGRATION_67_68
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
