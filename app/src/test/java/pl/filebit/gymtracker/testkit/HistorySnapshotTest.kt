package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ui.history.HistoryViewModel

/**
 * v1.27 — FAZA 3.1 — snapshot ekranu History.
 *
 * HistoryViewModel składa `List<HistoryItem>` przez observeAll().map{} —
 * dla każdego treningu liczy serie, objętość, liczbę ćwiczeń, PR, fazę
 * mesocyklu. Test sprawdza co zobaczy user na liście historii.
 */
class HistorySnapshotTest : TestHarness() {


    private fun viewModel(): HistoryViewModel {
        val kit = ViewModelKit(db, context)
        return HistoryViewModel(
            kit.workoutRepo, kit.planRepo, kit.statsRepo,
            kit.statsCacheService, db.trainingMesocycleDao()
        )
    }

    @Test
    fun `History pokazuje liste ukonczonych treningow z metrykami`() = runBlocking {
        loadScenario("healthy")
        val vm = viewModel()
        val items = withTimeout(5_000) { vm.workouts.first { it.isNotEmpty() } }

        val tr = TraceReport("history-list")
            .section("CO WIDZI USER")
            .kv("treningów na liście", items.size.toString())
        items.take(5).forEach {
            tr.kv("  ${it.workout.id}", "${it.totalSets} serii, " +
                "${it.totalVolumeKg.toInt()} kg obj., ${it.exerciseCount} ćwiczeń" +
                (if (it.hasPR) ", PR" else ""))
        }
        tr.section("OCENA")
            .verdict("wszystkie ukończone",
                if (items.all { !it.workout.isActive }) "OK" else "BŁĄD",
                "lista historii nie pokazuje aktywnych treningów")
            .emit()

        assertTrue("lista historii niepusta", items.isNotEmpty())
        assertTrue("żaden trening na liście nie jest aktywny",
            items.all { !it.workout.isActive })
        assertTrue("każdy trening ma policzone serie i objętość",
            items.all { it.totalSets > 0 && it.totalVolumeKg > 0 })
        assertTrue("każdy trening ma policzoną liczbę ćwiczeń",
            items.all { it.exerciseCount > 0 })
    }

    @Test
    fun `History dla pustej bazy - pusta lista`() = runBlocking {
        val vm = viewModel()
        // pusta baza → flow emituje initial emptyList; sprawdzamy że nie wybucha
        val items = vm.workouts.value
        assertTrue("pusta historia = pusta lista", items.isEmpty())
    }
}
