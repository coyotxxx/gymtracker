package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.repository.MealPrepPlanner
import pl.filebit.gymtracker.data.repository.ShoppingListGenerator
import pl.filebit.gymtracker.ui.diet.AdherenceReportViewModel
import pl.filebit.gymtracker.ui.diet.AdjustmentHistoryViewModel
import pl.filebit.gymtracker.ui.diet.MealPrepViewModel
import pl.filebit.gymtracker.ui.diet.ShoppingListViewModel
import pl.filebit.gymtracker.ui.diet.recipes.RecipeBrowserViewModel

/**
 * v1.27 — FAZA 3.5 — snapshoty ekranów diety rozszerzonej.
 *
 * AdherenceReport, AdjustmentHistory, MealPrep, ShoppingList, RecipeBrowser.
 * Sprawdza stan początkowy (empty state) każdego — co user widzi zanim
 * cokolwiek wygeneruje / przy braku danych.
 */
class DietExtSnapshotTest : TestHarness() {


    @Test
    fun `AdherenceReport empty state - raport bez danych`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AdherenceReportViewModel(kit.adherenceCalc)
        val s = withTimeout(5_000) { vm.state.first { !it.loading } }

        TraceReport("adherence-report")
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "false = załadowano")
            .kv("dni w 7d / 14d", "${s.last7Days.sampleDays} / ${s.last14Days.sampleDays}")
            .kv("ostatnie dni", s.recentDays.size.toString())
            .emit()

        assertFalse("raport załadowany nawet bez danych", s.loading)
        assertTrue("bez danych — brak dni adherence", s.last14Days.sampleDays == 0)
    }

    @Test
    fun `AdjustmentHistory empty state - brak korekt`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AdjustmentHistoryViewModel(kit.autoAdjust)
        val items = withTimeout(5_000) { vm.items.first() }
        assertTrue("brak korekt diety na starcie", items.isEmpty())
    }

    @Test
    fun `MealPrep empty state - brak planu do wygenerowania`() = runBlocking {
        val planner = MealPrepPlanner(
            db.mealEntryDao(), db.foodProductDao(), db.mealPrepPlanDao())
        val vm = MealPrepViewModel(db.mealPrepPlanDao(), planner)
        val plan = vm.plan.first()
        assertTrue("brak planu meal prep na starcie", plan == null)
        assertFalse("nie generuje na starcie", vm.generating.value)
    }

    @Test
    fun `ShoppingList empty state - brak listy do wygenerowania`() = runBlocking {
        val generator = ShoppingListGenerator(
            db.mealEntryDao(), db.foodProductDao(), db.shoppingListDao())
        val vm = ShoppingListViewModel(db.shoppingListDao(), generator)
        val list = vm.list.first()
        assertTrue("brak listy zakupów na starcie", list == null)
        assertFalse("nie generuje na starcie", vm.generating.value)
    }

    @Test
    fun `RecipeBrowser laduje liste przepisow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = RecipeBrowserViewModel(db.recipeDao(), db.foodProductDao(), kit.dietRepo, pl.filebit.gymtracker.data.repository.DietPreferences(context))
        val s = withTimeout(5_000) { vm.state.first() }

        TraceReport("recipe-browser")
            .section("CO WIDZI USER")
            .kv("przepisy", s.all.size.toString())
            .kv("filtr", s.filter.name)
            .kv("po filtrze", s.filtered.size.toString())
            .emit()

        // pusta lub zaseedowana baza — ważne że ekran się składa i nie wybucha
        assertTrue("filtrowanie nie wybucha", s.filtered.size <= s.all.size)
    }
}
