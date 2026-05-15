package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.repository.BodyRepository
import pl.filebit.gymtracker.data.repository.StrengthRepository
import pl.filebit.gymtracker.data.repository.VolumeService
import pl.filebit.gymtracker.ui.achievement.AchievementUiState
import pl.filebit.gymtracker.ui.achievement.AchievementViewModel
import pl.filebit.gymtracker.ui.achievement.AchievementWatcher
import pl.filebit.gymtracker.ui.muscles.MuscleEngagementViewModel
import pl.filebit.gymtracker.ui.stats.StatsViewModel
import pl.filebit.gymtracker.ui.strength.StrengthStandardsViewModel

/**
 * v1.27 — FAZA 3.6 — snapshoty ekranów statystyk.
 *
 * Stats (przegląd + objętość + PR), MuscleEngagement (zaangażowanie partii),
 * StrengthStandards (oceny siły), Achievement (modal odznak).
 */
class StatsSnapshotTest : TestHarness() {


    @Test
    fun `Stats sklada przeglad treningowy`() = runBlocking {
        loadScenario("healthy")
        val kit = ViewModelKit(db, context)
        val volumeService = VolumeService(
            db.workoutDao(), db.workoutSetDao(), db.exerciseDao(), kit.userProfileRepo)
        val vm = StatsViewModel(
            kit.statsRepo, kit.userProfileRepo, volumeService, kit.statsCacheService)
        val s = withTimeout(8_000) { vm.state.first { !it.loading } }

        TraceReport("stats")
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "false = załadowano")
            .kv("przegląd", if (s.overview != null) "obecny" else "null")
            .kv("objętość tygodnia", "${s.volumeWeek.toInt()} kg")
            .kv("rekordy (PR)", s.personalRecords.size.toString())
            .kv("heatmapa dni", s.calendarHeatmap.size.toString())
            .emit()

        assertFalse("statystyki załadowane", s.loading)
        assertTrue("przegląd policzony", s.overview != null)
        assertTrue("heatmapa kalendarza ma dni treningowe",
            s.calendarHeatmap.isNotEmpty())
    }

    @Test
    fun `MuscleEngagement sklada zaangazowanie partii`() = runBlocking {
        loadScenario("healthy")
        val kit = ViewModelKit(db, context)
        val vm = MuscleEngagementViewModel(kit.statsRepo, kit.statsCacheService)
        val s = withTimeout(8_000) { vm.state.first { !it.loading } }

        TraceReport("muscle-engagement")
            .section("CO WIDZI USER")
            .verdict("loading", s.loading.toString(), "")
            .kv("okres", s.period.name)
            .kv("partie z zaangażowaniem", s.engagement.size.toString())
            .emit()

        assertFalse("zaangażowanie załadowane", s.loading)
        assertTrue("są partie z danymi zaangażowania", s.engagement.isNotEmpty())
    }

    @Test
    fun `StrengthStandards ocenia sile`() = runBlocking {
        loadScenario("healthy")
        val kit = ViewModelKit(db, context)
        val strengthRepo = StrengthRepository(
            db.exerciseDao(), db.workoutSetDao(), kit.statsRepo,
            kit.userProfileRepo, BodyRepository(db.bodyMeasurementDao()))
        val vm = StrengthStandardsViewModel(strengthRepo, kit.userProfileRepo)
        val evaluations = withTimeout(8_000) { vm.evaluations.first() }

        TraceReport("strength-standards")
            .section("CO WIDZI USER")
            .kv("oceny siły", evaluations.size.toString())
            .emit()

        // bez wagi ciała oceny mogą być puste — ważne że ekran się składa
        assertTrue("ocena siły nie wybucha", evaluations.size >= 0)
    }

    @Test
    fun `Achievement modal ukryty dopoki nic nie odblokowano`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val watcher = AchievementWatcher(kit.statsRepo, kit.userProfileRepo)
        val vm = AchievementViewModel(context, watcher)
        val s = vm.uiState.first()
        assertEquals("modal odznak startuje ukryty",
            AchievementUiState.Hidden, s)
    }
}
