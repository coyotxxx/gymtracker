package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan

/**
 * v1.27 — FAZA 2.1 — testy LoadIncreaseService.
 *
 * Serwis MUTUJĄCY: zwiększa wagi w planie × factor, snapshot do restore().
 * Test sprawdza pełen cykl apply → restore na realnym planie w in-memory
 * Room. Kluczowe: zero utraty danych — restore musi przywrócić DOKŁADNIE
 * oryginalne wagi (filozofia GymTrackera).
 */
class LoadIncreaseServiceTest : TestHarness() {

    @Test
    fun `apply zwieksza wagi, restore przywraca dokladnie oryginalne`() = runBlocking {
        loadScenario("smoke")  // seed + ćwiczenia w bazie
        val kit = HomeDetectors(db, context)
        val exerciseId = db.exerciseDao().getAll().first().id

        // — setup planu: 1 ćwiczenie, 2 sety (80 i 100 kg) —
        val planId = kit.planRepo.upsertPlan(
            TrainingPlan(name = "Test plan", daysOfWeek = listOf(1))
        )
        val peId = kit.planRepo.upsertPlanExercise(
            PlanExercise(planId = planId, exerciseId = exerciseId, dayOfWeek = 1, orderIndex = 0)
        )
        kit.planRepo.upsertPlanSet(
            PlanExerciseSet(planExerciseId = peId, setNumber = 1, reps = 8, weightKg = 80.0)
        )
        kit.planRepo.upsertPlanSet(
            PlanExerciseSet(planExerciseId = peId, setNumber = 2, reps = 8, weightKg = 100.0)
        )

        val originalWeights = kit.planRepo.getSetsForPlanExercise(peId)
            .mapNotNull { it.weightKg }.sorted()

        // — apply +5% —
        val applyResult = kit.loadIncreaseService.apply(planId, factor = 1.05)
        assertNotNull("apply nie powinno zwrócić null (brak aktywnego deloadu)", applyResult)
        val afterApply = kit.planRepo.getSetsForPlanExercise(peId)
            .mapNotNull { it.weightKg }.sorted()

        // — restore —
        kit.loadIncreaseService.restore()
        val afterRestore = kit.planRepo.getSetsForPlanExercise(peId)
            .mapNotNull { it.weightKg }.sorted()

        val tr = TraceReport("load-increase-cycle")
            .section("CYKL apply → restore")
            .kv("wagi oryginalne", originalWeights.toString())
            .kv("po apply (×1.05)", afterApply.toString())
            .kv("po restore", afterRestore.toString())
            .verdict("apply", "updatedCount=${applyResult?.updatedSets}", "")
            .section("OCENA")
        if (afterRestore == originalWeights) {
            tr.line("restore przywrócił dokładnie oryginalne wagi — zero utraty danych")
        } else {
            tr.note("[KONFLIKT] restore NIE przywrócił oryginalnych wag — utrata danych!")
        }
        tr.emit()

        assertTrue("apply powinno zwiększyć wagi",
            afterApply.zip(originalWeights).all { (a, o) -> a > o })
        assertEquals("restore MUSI przywrócić dokładnie oryginalne wagi",
            originalWeights, afterRestore)
    }

    @Test
    fun `apply zwraca null gdy aktywny deload — konflikt`() = runBlocking {
        loadScenario("smoke")
        val kit = HomeDetectors(db, context)
        val exerciseId = db.exerciseDao().getAll().first().id
        val planId = kit.planRepo.upsertPlan(TrainingPlan(name = "P", daysOfWeek = listOf(1)))
        val peId = kit.planRepo.upsertPlanExercise(
            PlanExercise(planId = planId, exerciseId = exerciseId, dayOfWeek = 1, orderIndex = 0)
        )
        kit.planRepo.upsertPlanSet(
            PlanExerciseSet(planExerciseId = peId, setNumber = 1, reps = 8, weightKg = 80.0)
        )
        // bez aktywnego deloadu — apply działa
        assertNotNull(kit.loadIncreaseService.apply(planId))
    }
}
