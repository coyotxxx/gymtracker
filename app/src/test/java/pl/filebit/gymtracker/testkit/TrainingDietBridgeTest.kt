package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27 — FAZA 2.6 — testy TrainingDietBridge.
 *
 * Most trening↔dieta: przelicza Workout → TrainingDaySummary (objętość,
 * RPE, partie, intensywność). TrainingDaySummary zasila TDEE diety
 * i analizy obciążenia. Test sprawdza poprawność przeliczenia.
 */
class TrainingDietBridgeTest : TestHarness() {

    @Test
    fun `recomputeFromWorkout liczy summary z realnego treningu`() = runBlocking {
        loadScenario("smoke")  // 2 workouty z setami
        val kit = HomeDetectors(db, context)
        val workouts = db.workoutDao().observeAllOnce()

        workouts.forEach { kit.trainingDietBridge.recomputeFromWorkout(it.id) }
        val summaries = kit.trainingDietBridge.getRecent(30)

        val tr = TraceReport("training-diet-bridge")
            .section("DANE")
            .coverage("workouty", workouts.isNotEmpty(), "${workouts.size}")
            .section("SUMMARY (po recompute)")
        summaries.forEach { s ->
            tr.verdict("TrainingDaySummary",
                "isTrainingDay=${s.isTrainingDay}",
                "volume=${s.completedVolumeKg}kg, sety=${s.workingSetsCount}, " +
                    "rpe=${s.rpeAverage}, partie=${s.trainedMuscleGroupsCsv}")
        }
        tr.emit()

        assertEquals("2 workouty smoke → 2 summary treningowe", 2,
            summaries.count { it.isTrainingDay })
        assertTrue("każde summary treningowe ma objętość > 0",
            summaries.filter { it.isTrainingDay }.all { it.completedVolumeKg > 0 })
        assertTrue("każde summary treningowe ma policzone sety",
            summaries.filter { it.isTrainingDay }.all { it.workingSetsCount > 0 })
    }

    @Test
    fun `objetosc summary zgadza sie z setami treningu`() = runBlocking {
        loadScenario("smoke")
        val kit = HomeDetectors(db, context)
        // smoke: workout D-5 ma 1 set 5 reps × 100 kg = 500 kg objętości
        // (sortedBy startedAt rosnąco → D-5 najstarszy = first)
        val wOld = db.workoutDao().observeAllOnce().minByOrNull { it.startedAt }!!
        kit.trainingDietBridge.recomputeFromWorkout(wOld.id)
        val summary = kit.trainingDietBridge.getRecent(30)
            .firstOrNull { it.workoutId == wOld.id }
        assertTrue("summary workoutu istnieje", summary != null)
        assertEquals("objętość = 5 reps × 100 kg", 500.0, summary!!.completedVolumeKg, 0.01)
    }
}
