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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import pl.filebit.gymtracker.data.repository.DeloadService
import pl.filebit.gymtracker.data.repository.LoadIncreasePreferences
import pl.filebit.gymtracker.data.repository.UpdateRepository
import pl.filebit.gymtracker.ui.debug.DebugViewModel
import pl.filebit.gymtracker.ui.health.HealthHistoryViewModel
import pl.filebit.gymtracker.ui.onboarding.OnboardingViewModel
import pl.filebit.gymtracker.ui.periodization.PeriodizationPlanViewModel
import pl.filebit.gymtracker.ui.update.UpdateViewModel

/**
 * v1.27 — FAZA 3.10 — snapshoty narzędzi i pozostałych ekranów.
 *
 * Onboarding (wizard pierwszego uruchomienia), PeriodizationPlan,
 * Debug, HealthHistory, Update. Kalkulatory (OneRm, PlateCalc) i skanery
 * (Barcode, FoodImage) — czyste UI / CameraX, poza zakresem headless.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsSnapshotTest : TestHarness() {

    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    @Test
    fun `Onboarding wizard startuje od pustego kroku 0`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = OnboardingViewModel(
            kit.userProfileRepo, kit.dietProfileRepo, kit.bodyRepo,
            kit.goalRepo, kit.aiPrefs)
        val s = withTimeout(5_000) { vm.state.first() }

        TraceReport("onboarding")
            .section("CO WIDZI USER")
            .kv("imię (krok 0)", s.displayName.ifBlank { "(puste)" })
            .kv("cel domyślny", s.goal.name)
            .kv("dni/tydzień", s.daysPerWeek.toString())
            .emit()

        assertTrue("wizard startuje z pustym imieniem", s.displayName.isBlank())
    }

    @Test
    fun `PeriodizationPlan empty state - brak mesocykli`() = runBlocking {
        val vm = PeriodizationPlanViewModel(db.trainingMesocycleDao())
        val s = withTimeout(5_000) { vm.state.first { !it.isLoading } }
        assertTrue("brak cykli periodyzacji na starcie", s.cycles.isEmpty())
        assertTrue("brak aktywnego cyklu", s.activeCard == null)
    }

    @Test
    fun `Debug ekran startuje gotowy`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val deloadService = DeloadService(
            db.workoutDao(), db.workoutSetDao(), kit.statsRepo, kit.planRepo,
            DeloadPreferences(context), kit.userProfileRepo)
        val vm = DebugViewModel(
            context, db, DeloadPreferences(context), LoadIncreasePreferences(context),
            kit.planRepo, deloadService, db.exerciseDao(), backupImporter)
        // pobranie statusu bez wyjątku = ekran debug się składa
        val status = vm.status.first()
        assertTrue("status to napis (gotowy/bezczynny)", status.isEmpty() || status.isNotBlank())
    }

    @Test
    fun `HealthHistory empty state - brak wpisow`() = runBlocking {
        val vm = HealthHistoryViewModel(db.recoveryLogDao(), db.bodyMeasurementDao())
        val entries = withTimeout(5_000) { vm.entries.first() }
        assertTrue("brak wpisów zdrowia na starcie", entries.isEmpty())
    }

    @Test
    fun `Update ekran startuje`() = runBlocking {
        val vm = UpdateViewModel(UpdateRepository(context))
        // pierwsza emisja stanu bez wyjątku = ekran aktualizacji się składa
        val s = withTimeout(5_000) { vm.state.first() }
        assertTrue("ekran aktualizacji wyemitował stan startowy",
            s.javaClass.simpleName.isNotEmpty())
    }
}
