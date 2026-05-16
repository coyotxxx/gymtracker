package pl.filebit.gymtracker.testkit

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.di.AppModule
import java.io.File

/**
 * v1.27 — FAZA 4.1 — testy migracji Room.
 *
 * Filozofia GymTrackera: update aplikacji NIGDY nie traci danych usera
 * (fallbackToDestructiveMigration usunięty w v1.13.0). Test odtwarza bazę
 * na starej wersji schematu (z wyeksportowanego JSON), wstaje przez Room
 * z migracjami i sprawdza:
 *  - migracje 56→60 wykonują się bez wyjątku,
 *  - Room waliduje schemat końcowy (niekompletna migracja → wyjątek),
 *  - dane wstawione w v56 przeżywają migrację do v60.
 *
 * Schematy 49-55 nie były eksportowane (exportSchema dodane później) —
 * testujemy najnowsze 4 migracje; starsze pokryte historycznie produkcją.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(AppDatabase.NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun teardown() {
        if (dbFile.exists()) dbFile.delete()
    }

    /** Odtwarza bazę v56 z wyeksportowanego schematu JSON. */
    private fun buildV56Database() {
        val text = javaClass.classLoader!!
            .getResourceAsStream("schemas/db_v56.json")!!
            .readBytes().decodeToString()
        val schema = JSONObject(text).getJSONObject("database")
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val tn = e.getString("tableName")
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", tn))
                e.optJSONArray("indices")?.let { idx ->
                    for (j in 0 until idx.length()) {
                        raw.execSQL(idx.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", tn))
                    }
                }
            }
            raw.version = 56
        } finally {
            raw.close()
        }
    }

    private fun openWithMigrations(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppModule.MIGRATION_56_57, AppModule.MIGRATION_57_58,
                AppModule.MIGRATION_58_59, AppModule.MIGRATION_59_60,
                AppModule.MIGRATION_60_61, AppModule.MIGRATION_61_62,
                AppModule.MIGRATION_62_63
            )
            .build()

    @Test
    fun `migracja 56 do 63 wykonuje sie i waliduje schemat`() = runBlocking {
        buildV56Database()
        val db = openWithMigrations()
        // pierwsze zapytanie wymusza otwarcie + migrację + walidację schematu.
        // Niekompletna migracja → Room rzuca IllegalStateException tutaj.
        val count = db.exerciseDao().count()
        db.close()

        val tr = TraceReport("migration-56-to-63")
            .section("MIGRACJA")
            .verdict("ścieżka 56→…→63", "wykonana", "bez wyjątku")
            .verdict("walidacja schematu v63", "OK", "Room potwierdził zgodność")
            .kv("ćwiczeń po migracji", count.toString())
            .emit()

        assertEquals("pusta baza v56 → pusta po migracji", 0, count)
    }

    @Test
    fun `dane wstawione w v56 przezywaja migracje do v63`() = runBlocking {
        buildV56Database()
        // wstaw ćwiczenie do bazy v56 (surowy SQL — kolumny schematu v56)
        val raw = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL(
            "INSERT INTO exercises " +
                "(name, primaryMuscle, equipment, isCustom, notes, metricType, " +
                "description, isFavorite, isAvoided) " +
                "VALUES ('Wyciskanie testowe', 'CHEST', 'BARBELL', 0, '', " +
                "'WEIGHT_REPS', '', 0, 0)"
        )
        raw.close()

        val db = openWithMigrations()
        val exercises = db.exerciseDao().getAll()
        db.close()

        val tr = TraceReport("migration-data-survival")
            .section("OCENA — zero utraty danych")
            .kv("ćwiczeń przed migracją (v56)", "1")
            .kv("ćwiczeń po migracji (v63)", exercises.size.toString())
            .verdict("dane przetrwały update",
                if (exercises.size == 1) "OK" else "UTRATA DANYCH",
                "fallbackToDestructiveMigration usunięty — migracja musi zachować dane")
            .emit()

        assertEquals("ćwiczenie przetrwało migrację 56→63", 1, exercises.size)
        assertEquals("nazwa ćwiczenia zachowana",
            "Wyciskanie testowe", exercises.first().name)
    }

    @Test
    fun `lancuch migracji 49 do 63 jest ciagly`() {
        // Room znajduje ścieżkę migracji tylko gdy łańcuch jest ciągły.
        // Brak którejkolwiek migracji = przerwa = destructive fallback/crash.
        val migrations = listOf(
            AppModule.MIGRATION_49_50, AppModule.MIGRATION_50_51,
            AppModule.MIGRATION_51_52, AppModule.MIGRATION_52_53,
            AppModule.MIGRATION_53_54, AppModule.MIGRATION_54_55,
            AppModule.MIGRATION_55_56, AppModule.MIGRATION_56_57,
            AppModule.MIGRATION_57_58, AppModule.MIGRATION_58_59,
            AppModule.MIGRATION_59_60, AppModule.MIGRATION_60_61,
            AppModule.MIGRATION_61_62, AppModule.MIGRATION_62_63
        )
        var version = 49
        for (m in migrations) {
            assertEquals("migracja startuje od bieżącej wersji",
                version, m.startVersion)
            assertEquals("migracja podnosi o 1 wersję",
                version + 1, m.endVersion)
            version = m.endVersion
        }
        assertEquals("łańcuch kończy się na wersji bazy danych", 63, version)
        assertTrue("14 migracji 49→63", migrations.size == 14)
    }
}
