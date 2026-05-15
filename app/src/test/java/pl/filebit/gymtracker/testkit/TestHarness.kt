package pl.filebit.gymtracker.testkit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
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

    companion object {
        // v1.26.9: Dispatchers.Main ustawiany RAZ dla całego procesu testowego
        // (test dispatcher), nigdy nie resetowany. Snapshoty ViewModeli tworzą
        // viewModelScope coroutines, które przeżywają test (stateIn +
        // WhileSubscribed). Gdyby każdy test robił setMain/resetMain, reset
        // jednego testu kolidowałby z żywą coroutine innego —
        // "Dispatchers.Main is used concurrently with setting it" (flaky).
        // Jeden setMain bez resetów = zero kolizji.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val mainDispatcherInstalled: Unit = run {
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    init {
        // wymusza inicjalizację companion (setMain) zanim ruszą testy
        mainDispatcherInstalled
    }

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
     *
     * Placeholdery czasu — scenariusz jest "wieczny", daty zawsze relatywne
     * do uruchomienia testu (detektory liczą "ostatnie 14 dni" od teraz):
     *   {{NOW}}    → bieżący timestamp
     *   {{D-7}}    → 7 dni temu
     *   {{D-3H6}}  → 3 dni i 6 godzin temu
     */
    protected fun loadScenario(name: String): BackupImportResult = runBlocking {
        val stream = javaClass.classLoader!!.getResourceAsStream("scenarios/$name.json")
            ?: error("Brak scenariusza: scenarios/$name.json")
        val resolved = resolveTimePlaceholders(stream.readBytes().decodeToString())
        val tmp = File.createTempFile("scenario_$name", ".json")
        tmp.writeText(resolved)
        try {
            backupImporter.importFromFile(tmp)
        } finally {
            tmp.delete()
        }
    }

    /** Zamienia placeholdery czasu na absolutne timestampy względem teraz. */
    private fun resolveTimePlaceholders(text: String): String {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val hourMs = 3_600_000L
        var out = text.replace("{{NOW}}", now.toString())
        // {{D-3H6}} — dni i godziny
        out = Regex("""\{\{D-(\d+)H(\d+)\}\}""").replace(out) { m ->
            (now - m.groupValues[1].toLong() * dayMs - m.groupValues[2].toLong() * hourMs).toString()
        }
        // {{D-7}} — same dni
        out = Regex("""\{\{D-(\d+)\}\}""").replace(out) { m ->
            (now - m.groupValues[1].toLong() * dayMs).toString()
        }
        return out
    }
}
