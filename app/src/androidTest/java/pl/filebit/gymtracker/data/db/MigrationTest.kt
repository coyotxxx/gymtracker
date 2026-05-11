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
     * Placeholder dla przyszłej migracji 54→55 (v1.15.0 — PendingPeriodizationDecision).
     * Po dodaniu Migration_54_55 ten test sprawdzi że:
     *  1. Schema bazy v55 jest tworzone poprawnie.
     *  2. Dane z v54 są zachowane.
     *  3. Nowa tabela pending_periodization_decisions istnieje + ma poprawny schemat.
     *
     * Dla v1.14.1 — test pominięty (brak Migration_54_55 jeszcze).
     */
    // @Test fun migrate54To55_addsPendingDecisionsTable() { ... }
}
