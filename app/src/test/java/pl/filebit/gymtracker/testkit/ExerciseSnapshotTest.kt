package pl.filebit.gymtracker.testkit

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.filebit.gymtracker.ui.exercises.ExerciseDetailViewModel
import pl.filebit.gymtracker.ui.exercises.ExerciseLibraryViewModel
import pl.filebit.gymtracker.ui.workout.ExercisePickerViewModel

/**
 * v1.27 — FAZA 3.3 — snapshoty ekranów ćwiczeń.
 *
 * ExerciseLibrary: przeglądarka biblioteki. ExercisePicker: wybór ćwiczenia
 * do treningu. ExerciseDetail: szczegóły ćwiczenia (przez SavedStateHandle).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    @Test
    fun `ExerciseLibrary pokazuje biblioteke cwiczen`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val vm = ExerciseLibraryViewModel(kit.exerciseRepo, kit.statsRepo)
        val exercises = withTimeout(5_000) { vm.exercises.first { it.isNotEmpty() } }

        TraceReport("exercise-library")
            .section("CO WIDZI USER")
            .kv("ćwiczeń w bibliotece", exercises.size.toString())
            .kv("przykłady", exercises.take(3).joinToString { it.name })
            .emit()

        assertTrue("biblioteka ma ćwiczenia (seed)", exercises.size > 50)
    }

    @Test
    fun `ExercisePicker pokazuje liste do wyboru`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val vm = ExercisePickerViewModel(kit.exerciseRepo, kit.workoutRepo)
        val exercises = withTimeout(5_000) { vm.exercises.first { it.isNotEmpty() } }

        assertTrue("picker pokazuje ćwiczenia do wyboru", exercises.isNotEmpty())
    }

    @Test
    fun `ExerciseDetail laduje szczegoly cwiczenia`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val exercise = db.exerciseDao().getAll().first()
        val vm = ExerciseDetailViewModel(
            kit.exerciseRepo, kit.statsRepo, kit.stagnationAnalyzer,
            kit.aiClient, kit.aiPrefs, db.exerciseDao(),
            SavedStateHandle(mapOf("exerciseId" to exercise.id))
        )
        val s = withTimeout(5_000) { vm.state.first { !it.loading } }

        TraceReport("exercise-detail")
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "false = załadowano")
            .kv("ćwiczenie", s.exercise?.name ?: "null")
            .kv("historia serii", s.history.size.toString())
            .kv("rekord (PR)", if (s.pr != null) "obecny" else "brak")
            .emit()

        assertFalse("po init loading=false", s.loading)
        assertTrue("ćwiczenie załadowane", s.exercise != null)
        assertEquals("załadowano właściwe ćwiczenie", exercise.id, s.exercise?.id)
    }
}
