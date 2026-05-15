package pl.filebit.gymtracker.testkit

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
import pl.filebit.gymtracker.ui.workout.ActiveWorkoutViewModel
import pl.filebit.gymtracker.ui.workout.CoachWorkoutViewModel

/**
 * v1.27 — FAZA 3.1 — snapshoty ekranów podczas treningu.
 *
 * CoachWorkout (tryb prowadzony) i ActiveWorkout (lista serii) — oba
 * obserwują aktywny trening. Test tworzy aktywny trening z 3 seriami
 * (2 ćwiczenia) i raportuje co user widzi na każdym ekranie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveTrainingSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    /** Tworzy aktywny trening: 2 serie ćwiczenia A + 1 seria ćwiczenia B. */
    private suspend fun ViewModelKit.seedActiveWorkout() {
        val exercises = db.exerciseDao().getAll().take(2)
        check(exercises.size == 2) { "scenariusz musi mieć ≥2 ćwiczenia" }
        val active = workoutRepo.startOrResume()
        workoutRepo.addPlannedSet(active.id, exercises[0].id, reps = 5, weightKg = 100.0)
        workoutRepo.addPlannedSet(active.id, exercises[0].id, reps = 5, weightKg = 100.0)
        workoutRepo.addPlannedSet(active.id, exercises[1].id, reps = 8, weightKg = 60.0)
    }

    @Test
    fun `CoachWorkout prowadzi przez serie aktywnego treningu`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        kit.seedActiveWorkout()
        val vm = CoachWorkoutViewModel(
            kit.workoutRepo, kit.planRepo, kit.exerciseRepo, kit.statsRepo,
            kit.userProfileRepo, kit.trainingDietBridge, kit.workoutAiSummary, kit.rpeOpinion
        )
        val s = withTimeout(5_000) { vm.state.first { it.workout != null } }

        TraceReport("coach-workout")
            .section("CO WIDZI USER")
            .verdict("workout", "aktywny", "")
            .kv("ćwiczeń w treningu", s.totalExercises.toString())
            .kv("serie łącznie", s.totalSets.toString())
            .kv("ukończone serie", s.completedSets.toString())
            .kv("bieżące ćwiczenie", s.currentExercise?.name ?: "—")
            .kv("bieżąca seria (w ćwiczeniu)",
                "${s.currentSetIndexInExercise + 1}/${s.totalSetsInCurrentExercise}")
            .verdict("isComplete", s.isComplete.toString(), "false = trening trwa")
            .emit()

        assertEquals("2 ćwiczenia w treningu", 2, s.totalExercises)
        assertEquals("3 serie łącznie", 3, s.totalSets)
        assertEquals("0 ukończonych na starcie", 0, s.completedSets)
        assertTrue("coach wskazuje bieżące ćwiczenie", s.currentExercise != null)
        assertFalse("trening jeszcze nie ukończony", s.isComplete)
    }

    @Test
    fun `ActiveWorkout pokazuje grupy serii aktywnego treningu`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        kit.seedActiveWorkout()
        val vm = ActiveWorkoutViewModel(
            kit.workoutRepo, kit.exerciseRepo, kit.planRepo, kit.statsRepo,
            kit.trainingDietBridge, kit.workoutAiSummary, kit.userProfileRepo
        )
        val s = withTimeout(5_000) { vm.state.first { it.workout != null } }

        TraceReport("active-workout")
            .section("CO WIDZI USER")
            .verdict("workout", "aktywny", "")
            .kv("grupy ćwiczeń", s.groups.size.toString())
            .emit()

        assertTrue("aktywny trening widoczny", s.workout != null)
        assertEquals("2 grupy ćwiczeń", 2, s.groups.size)
    }
}
