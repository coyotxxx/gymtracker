package pl.filebit.gymtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test migracji Room — safety net wprowadzone w v1.14.1 po incydencie v1.13.0
 * (Migration_53_54 dodała kolumny przez ALTER TABLE ale @Entity ich nie miały
 * → schema mismatch → IllegalStateException → crash przy starcie).
 *
 * Test infrastructure:
 *  - exportSchema=true generuje JSON dla każdej wersji DB w app/schemas/
 *  - MigrationTestHelper czyta JSON dla wersji "from", wykonuje migrację, weryfikuje schemat "to"
 *
 * Uruchomienie lokalnie:
 *  ./gradlew :app:connectedDebugAndroidTest
 *
 * Uruchomienie w CI: wymaga emulatora — obecnie pominięte w CI workflow.
 *
 * Pełne testy historycznych migracji v51→v54 wymagają schema JSON dla v51, v52, v53
 * które historycznie nie istnieją (exportSchema=false było zawsze). Możliwe podejścia:
 *  1. Ręcznie zaprojektować JSON dla v51-v53 na podstawie SQL z Migration_51_52,
 *     Migration_52_53, Migration_53_54.
 *  2. Czekać aż user (Maciej) potwierdzi że v1.13.1 i v1.14.0 zaktualizowały się OK
 *     z jego bazy v53 — to empiryczne potwierdzenie że migracje działają.
 *
 * Obecnie test pokrywa:
 *  - Cold create v54 — schema valid (KSP/Room nie rzuca IllegalStateException przy open).
 *  - Migracje v54+ (future-proof: gdy v1.15.0 doda Migration_54_55, test sprawdzi że
 *    schema zostanie poprawnie zmigrowana).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Sanity check: cold-create v54 schema na czystej bazie nie rzuca błędu.
     * Jeśli @Entity definicje są niespójne (jak v1.13.0 crash) — ten test wyłapie.
     */
    @Test
    @Throws(IOException::class)
    fun coldCreateV54_schemaIsValid() {
        val db = helper.createDatabase(TEST_DB, 54)
        // Sanity check: można otworzyć i wykonać prosty query.
        db.query("SELECT 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    /**
     * Sanity check: po cold create v54, można otworzyć bazę przez Room
     * (validateSchema() nie rzuci IllegalStateException).
     */
    @Test
    @Throws(IOException::class)
    fun openDatabaseV54_validatesSchema() {
        // Cold create przy uzyciu MigrationTestHelper (na pustej bazie).
        helper.createDatabase(TEST_DB, 54).close()
        // Re-open przez Room — robi runtime schema validation.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .allowMainThreadQueries()
            .build()
        // Sanity: można zacząć transakcję = baza działa.
        db.openHelper.writableDatabase.use { sqlite ->
            assertNotNull(sqlite)
            assertEquals(54, sqlite.version)
        }
        db.close()
        // Cleanup
        context.deleteDatabase(TEST_DB)
    }

    /**
     * v1.28 Etap 1 — refaktor "jedno źródło prawdy" (docs/CONFIG-UNIFICATION-PLAN.md).
     *
     * Migracja 60→61 scala `user_diet_profile` w `user_profile`. Test sprawdza że:
     *  1. ALTER TABLE dodaje kolumny diety i migracja nie rzuca błędu.
     *  2. Dane diety z `user_diet_profile` trafiają do scalonego wiersza `user_profile`.
     *  3. Pola treningu w `user_profile` zostają nienaruszone.
     *
     * validateDroppedTables = false — `user_diet_profile` ZOSTAJE w bazie celowo
     * (osierocona tabela, bezpiecznik do Etapu 5). Room runtime też ją toleruje.
     */
    @Test
    @Throws(IOException::class)
    fun migrate60To61_mergesDietProfileIntoUserProfile() {
        helper.createDatabase(TEST_DB, 60).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profile
                (id, displayName, goal, experience, daysPerWeek, sessionMinutes,
                 preferredUnit, defaultRestSeconds, injuriesNotes, showAdvancedSetFields,
                 weightGoalType, targetWeightKg, unfinishedWorkoutNotifyEnabled,
                 unfinishedWorkoutNotifyHours, gender, bodyweightKg, flashOnTimerEnd,
                 aiOverlayEnabled, onboardingCompleted, aiProactiveChecksEnabled,
                 availableEquipmentCsv, preferredMuscleGroupsCsv, equipmentCategoriesCsv)
                VALUES (1, 'Maciej', 'HYPERTROPHY', 'ADVANCED', 5, 75, 'KG', 120, '', 0,
                        'CUT', 78.0, 1, 3, 'MALE', 84.0, 0, 0, 1, 0, '', '', 'BARBELL')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO user_diet_profile
                (id, ageYears, heightCm, activityLevel, avgStepsPerDay, goalType,
                 paceKgPerWeek, customDeficitKcal, dietPreference, allergies, intolerances,
                 dislikedFoods, lovedFoods, cookingTimePerMealMin, eatsAtWork,
                 hasMicrowaveAtWork, mealPrepInterested, weeklyBudgetPln, medicalConditions,
                 medicalAwareness, usualTrainingHour, onboardingCompletedAt, updatedAt)
                VALUES (1, 35, 182, 'LIGHT', 8000, 'FAT_LOSS', 0.5, NULL, 'STANDARD',
                        'laktoza', '', 'brokuł', 'twaróg', 20, 1, 1, 0, 350, '', 0, 18,
                        1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 61, false, pl.filebit.gymtracker.di.AppModule.MIGRATION_60_61
        )

        // Dane diety przeniesione do user_profile
        db.query(
            "SELECT ageYears, heightCm, activityLevel, goalType, paceKgPerWeek, " +
                "allergies, lovedFoods, weeklyBudgetPln, usualTrainingHour, " +
                "dietOnboardingCompletedAt, bodyweightKg, weightGoalType " +
                "FROM user_profile WHERE id = 1"
        ).use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(35, c.getInt(0))
            assertEquals(182, c.getInt(1))
            assertEquals("LIGHT", c.getString(2))
            assertEquals("FAT_LOSS", c.getString(3))
            assertEquals(0.5, c.getDouble(4), 0.001)
            assertEquals("laktoza", c.getString(5))
            assertEquals("twaróg", c.getString(6))
            assertEquals(350, c.getInt(7))
            assertEquals(18, c.getInt(8))
            assertEquals(1700000000000L, c.getLong(9))
            // pola treningu nienaruszone
            assertEquals(84.0, c.getDouble(10), 0.001)
            assertEquals("CUT", c.getString(11))
        }
        db.close()
    }
}
