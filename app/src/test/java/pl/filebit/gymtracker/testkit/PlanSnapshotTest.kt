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
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.ui.plans.PlanEditViewModel
import pl.filebit.gymtracker.ui.plans.PlanListViewModel

/**
 * v1.27 — FAZA 3.2 — snapshoty ekranów planów (Plans, PlanEdit).
 *
 * PlanList: lista planów z liczbą ćwiczeń. PlanEdit: edytor pojedynczego
 * planu (nowy lub istniejący — przez SavedStateHandle "planId").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    /** Tworzy plan "Push" z 2 ćwiczeniami w dniu 1. Zwraca planId. */
    private suspend fun ViewModelKit.seedPlan(): Long {
        val exercises = db.exerciseDao().getAll().take(2)
        val planId = planRepo.upsertPlan(
            TrainingPlan(name = "Push", daysOfWeek = listOf(1, 3), notes = "test")
        )
        exercises.forEachIndexed { idx, ex ->
            planRepo.upsertPlanExercise(
                PlanExercise(planId = planId, exerciseId = ex.id,
                    dayOfWeek = 1, orderIndex = idx)
            )
        }
        return planId
    }

    @Test
    fun `PlanList pokazuje plany z liczba cwiczen`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        kit.seedPlan()
        val vm = PlanListViewModel(
            kit.planRepo, kit.workoutRepo, kit.exerciseRepo, kit.workoutPlanAi
        )
        val items = withTimeout(5_000) { vm.plans.first { it.isNotEmpty() } }

        TraceReport("plan-list")
            .section("CO WIDZI USER")
            .kv("planów", items.size.toString())
            .apply { items.forEach { kv("  ${it.plan.name}", "${it.exerciseCount} ćwiczeń") } }
            .emit()

        assertTrue("lista planów niepusta", items.isNotEmpty())
        val push = items.first { it.plan.name == "Push" }
        assertEquals("plan Push ma 2 ćwiczenia", 2, push.exerciseCount)
    }

    @Test
    fun `PlanEdit nowy plan - pusty edytowalny stan`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val vm = PlanEditViewModel(
            kit.planRepo, kit.exerciseRepo, kit.userProfileRepo, kit.statsRepo,
            kit.planAuditService, kit.aiPlanApplier, SavedStateHandle()
        )
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }

        TraceReport("plan-edit-new")
            .section("CO WIDZI USER")
            .verdict("isNew", s.isNew.toString(), "nowy plan")
            .kv("nazwa", s.name.ifBlank { "(pusta)" })
            .kv("ćwiczenia", s.exercises.size.toString())
            .emit()

        assertTrue("nowy plan oznaczony jako isNew", s.isNew)
        assertTrue("nowy plan ma pustą nazwę", s.name.isBlank())
        assertTrue("nowy plan bez ćwiczeń", s.exercises.isEmpty())
    }

    @Test
    fun `PlanEdit istniejacy plan - zaladowany do edycji`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val planId = kit.seedPlan()
        val vm = PlanEditViewModel(
            kit.planRepo, kit.exerciseRepo, kit.userProfileRepo, kit.statsRepo,
            kit.planAuditService, kit.aiPlanApplier,
            SavedStateHandle(mapOf("planId" to planId))
        )
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }

        TraceReport("plan-edit-existing")
            .section("CO WIDZI USER")
            .verdict("isNew", s.isNew.toString(), "istniejący plan")
            .kv("nazwa", s.name)
            .kv("dni", s.daysOfWeek.sorted().joinToString())
            .kv("ćwiczenia (dzień ${s.selectedDay})", s.exercises.size.toString())
            .emit()

        assertFalse("istniejący plan nie jest nowy", s.isNew)
        assertEquals("nazwa załadowana", "Push", s.name)
        assertTrue("dni planu załadowane", s.daysOfWeek.isNotEmpty())
    }
}
