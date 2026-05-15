package pl.filebit.gymtracker.testkit

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.backup.DietBackupManager
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.ui.backup.BackupViewModel
import java.io.File

/**
 * v1.27 — FAZA 4.2 — backup/restore round-trip.
 *
 * Eksport → wyczyszczenie bazy → import. Stan po imporcie musi być
 * identyczny ze stanem przed eksportem — backup na Drive/USB to jedyna
 * ochrona danych usera przed utratą telefonu. Każda rozbieżność = bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRoundTripTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    private data class DbSnapshot(
        val workouts: Int, val sets: Int, val measurements: Int,
        val plans: Int, val planExercises: Int
    )

    private suspend fun snapshot() = DbSnapshot(
        workouts = db.workoutDao().observeAllOnce().size,
        sets = db.workoutSetDao().getAll().size,
        measurements = db.bodyMeasurementDao().getAllAsc().size,
        plans = db.trainingPlanDao().getAll().size,
        planExercises = db.trainingPlanDao().getAll()
            .sumOf { db.planExerciseDao().getForPlan(it.id).size }
    )

    @Test
    fun `backup round-trip zachowuje wszystkie dane`() = runBlocking {
        // overtraining_cut ma treningi + serie + pomiary wagi — pełniejszy round-trip
        loadScenario("overtraining_cut")
        val before = snapshot()
        assertTrue("scenariusz załadował treningi", before.workouts > 0)
        assertTrue("scenariusz załadował pomiary wagi", before.measurements > 0)

        val kit = ViewModelKit(db, context)
        val vm = BackupViewModel(
            context, kit.workoutRepo, kit.exerciseRepo, kit.userProfileRepo,
            AiPreferences(context), db, ExerciseSeeder(context, db.exerciseDao()),
            DietBackupManager(context, db, DietPreferences(context)), backupImporter
        )

        // EKSPORT do pliku ZIP
        val zipFile = File.createTempFile("backup_roundtrip", ".zip")
        vm.export(Uri.fromFile(zipFile))
        withTimeout(15_000) {
            vm.status.first { it != null && it.startsWith("Eksport") }
        }
        assertTrue("plik backupu powstał i nie jest pusty", zipFile.length() > 0)

        // WYCZYSZCZENIE bazy — symulacja nowego telefonu
        db.clearAllTables()
        assertEquals("baza wyczyszczona", 0, db.workoutDao().observeAllOnce().size)

        // IMPORT z pliku
        backupImporter.importFromFile(zipFile)
        val after = snapshot()
        zipFile.delete()

        val tr = TraceReport("backup-round-trip")
            .section("STAN PRZED EKSPORTEM")
            .kv("treningi", before.workouts.toString())
            .kv("serie", before.sets.toString())
            .kv("pomiary", before.measurements.toString())
            .kv("plany", before.plans.toString())
            .section("STAN PO IMPORCIE")
            .kv("treningi", after.workouts.toString())
            .kv("serie", after.sets.toString())
            .kv("pomiary", after.measurements.toString())
            .kv("plany", after.plans.toString())
            .section("OCENA")
        if (before == after) {
            tr.line("round-trip bezstratny — wszystkie liczby się zgadzają")
        } else {
            tr.note("[UTRATA DANYCH] stan przed ≠ stan po: $before vs $after")
        }
        tr.emit()

        assertEquals("treningi przetrwały round-trip", before.workouts, after.workouts)
        assertEquals("serie przetrwały round-trip", before.sets, after.sets)
        assertEquals("pomiary przetrwały round-trip", before.measurements, after.measurements)
        assertEquals("plany przetrwały round-trip", before.plans, after.plans)
        assertEquals("ćwiczenia w planach przetrwały",
            before.planExercises, after.planExercises)
    }
}
