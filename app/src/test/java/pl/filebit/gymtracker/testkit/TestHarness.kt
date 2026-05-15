package pl.filebit.gymtracker.testkit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.filebit.gymtracker.data.db.AppDatabase

/**
 * v1.27 — FAZA 0.1 — fundament headless test harness.
 *
 * Bazowa klasa testów E2E: uruchamia in-memory Room w JVM przez Robolectric
 * (CI nie ma emulatora — wszystko musi być JVM unit test).
 *
 * Wersja minimalna — sam in-memory Room + dostęp do DAO. Loader scenariuszy
 * przez `BackupImporter` dochodzi w kolejnym kroku FAZY 0.1, po potwierdzeniu
 * w CI że Robolectric + Room w ogóle działa.
 *
 * Użycie:
 * ```
 * class MojTest : TestHarness() {
 *     @Test fun cos() { db.exerciseDao()... }
 * }
 * ```
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
abstract class TestHarness {

    protected lateinit var context: Context
    protected lateinit var db: AppDatabase

    @Before
    fun setupHarness() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardownHarness() {
        db.close()
    }
}
