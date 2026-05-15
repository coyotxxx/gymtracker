package pl.filebit.gymtracker.testkit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.backup.BackupImporter
import pl.filebit.gymtracker.data.backup.DietBackupManager
import pl.filebit.gymtracker.data.backup.BackupImportResult
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.MesocycleBackfillService
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import java.io.File

/**
 * v1.27 — FAZA 0.1 — fundament headless test harness.
 *
 * Bazowa klasa testów E2E: uruchamia in-memory Room w JVM przez Robolectric
 * (CI nie ma emulatora — wszystko musi być JVM unit test) i ładuje scenariusze
 * danych przez prawdziwy `BackupImporter` — tę samą ścieżkę co Debug Import
 * w aplikacji. Dzięki temu test pracuje na realnym stanie aplikacji.
 *
 * Użycie:
 * ```
 * class MojTest : TestHarness() {
 *     @Test fun cos() {
 *         loadScenario("smoke")
 *         val workouts = runBlocking { db.workoutDao()... }
 *     }
 * }
 * ```
 *
 * Scenariusze: pliki JSON (format BackupData) w app/src/test/resources/scenarios/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
abstract class TestHarness {

    protected lateinit var context: Context
    protected lateinit var db: AppDatabase
    protected lateinit var backupImporter: BackupImporter

    @Before
    fun setupHarness() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupImporter = BackupImporter(
            context,
            db,
            AiPreferences(context),
            ExerciseSeeder(context, db.exerciseDao()),
            DietBackupManager(context, db, DietPreferences(context)),
            MesocycleBackfillService(
                db.trainingMesocycleDao(),
                db.trainingEventDao(),
                db.workoutDao(),
                db.workoutSetDao()
            )
        )
    }

    @After
    fun teardownHarness() {
        db.close()
    }

    /**
     * Ładuje scenariusz z app/src/test/resources/scenarios/<name>.json
     * przez prawdziwy BackupImporter (seed + import + backfill mesocykli).
     */
    protected fun loadScenario(name: String): BackupImportResult = runBlocking {
        val stream = javaClass.classLoader!!.getResourceAsStream("scenarios/$name.json")
            ?: error("Brak scenariusza: scenarios/$name.json")
        val tmp = File.createTempFile("scenario_$name", ".json")
        tmp.writeBytes(stream.readBytes())
        try {
            backupImporter.importFromFile(tmp)
        } finally {
            tmp.delete()
        }
    }
}
