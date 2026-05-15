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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.filebit.gymtracker.ui.history.WorkoutDetailUiState
import pl.filebit.gymtracker.ui.history.WorkoutDetailViewModel

/**
 * v1.27 — FAZA 3.1 — snapshot ekranu WorkoutDetail.
 *
 * Buduje prawdziwy ViewModel z grafem DI ([ViewModelKit]) na danych
 * scenariusza, woła `load(id)` i raportuje `WorkoutDetailUiState` —
 * dokładnie to co zobaczy user po wejściu w trening z historii.
 *
 * viewModelScope na UnconfinedTestDispatcher (Main) + czekanie na StateFlow:
 * suspend-query Room wykonuje się na własnym executorze, więc test czeka
 * aż stan faktycznie się zmieni, zamiast advance'ować scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    private suspend fun WorkoutDetailViewModel.loadAndAwait(id: Long): WorkoutDetailUiState {
        load(id)
        return withTimeout(5_000) { state.first { !it.loading } }
    }

    @Test
    fun `WorkoutDetail pokazuje trening z grupami cwiczen`() = runBlocking {
        loadScenario("healthy")
        val kit = ViewModelKit(db, context)
        val vm = WorkoutDetailViewModel(kit.workoutRepo, kit.planRepo, kit.statsRepo)

        val workout = db.workoutDao().observeAllOnce()
            .filter { !it.isActive }
            .maxByOrNull { it.startedAt }!!
        val s = vm.loadAndAwait(workout.id)

        val tr = TraceReport("workout-detail")
            .section("WEJŚCIE")
            .kv("workoutId", workout.id.toString())
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "false = załadowano")
            .verdict("workout", if (s.workout != null) "obecny" else "null", "")
            .kv("grupy ćwiczeń", s.groups.size.toString())
            .kv("plan", s.planName ?: "ad-hoc / brak")
        s.groups.forEach { g ->
            tr.kv("  ${g.exercise.name}", "${g.sets.size} serii")
        }
        tr.section("OCENA")
            .verdict("poprzednie sesje załadowane",
                "${s.previousByExercise.size} ćwiczeń", "mapa exerciseId→PreviousSession")
            .emit()

        assertFalse("po load() loading=false", s.loading)
        assertTrue("workout załadowany", s.workout != null)
        assertTrue("trening ma grupy ćwiczeń", s.groups.isNotEmpty())
        assertTrue("każda grupa ma co najmniej 1 serię",
            s.groups.all { it.sets.isNotEmpty() })
    }

    @Test
    fun `WorkoutDetail dla nieistniejacego treningu - pusty stan`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = WorkoutDetailViewModel(kit.workoutRepo, kit.planRepo, kit.statsRepo)
        val s = vm.loadAndAwait(999_999L)

        assertFalse("load zakończony nawet dla brakującego id", s.loading)
        assertTrue("brak treningu", s.workout == null)
        assertTrue("brak grup", s.groups.isEmpty())
    }
}
