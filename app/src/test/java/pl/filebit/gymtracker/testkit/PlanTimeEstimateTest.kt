package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan

/**
 * v2.6.0 — realna estymacja czasu sesji (PlanRepository.estimateSessionMinutes).
 * Zastępuje prymitywne exerciseCount×10, które dla cardio 1×60min dawało "10 min".
 * Bug zgłoszony przez Macieja: karta planu "~10 min" vs trening "60 min".
 */
class PlanTimeEstimateTest : TestHarness() {

    @Test
    fun `cardio durationSec liczony jako faktyczny czas, nie countx10`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.planRepo
        val exId = db.exerciseDao().getAll().first().id

        val planId = repo.upsertPlan(TrainingPlan(name = "Cardio", daysOfWeek = listOf(1)))
        val peId = repo.upsertPlanExercise(
            PlanExercise(planId = planId, exerciseId = exId, dayOfWeek = 1, orderIndex = 0)
        )
        // 1 seria cardio = 3600 s (60 min)
        repo.upsertPlanSet(
            PlanExerciseSet(planExerciseId = peId, setNumber = 1, durationSec = 3600)
        )

        val perDay = repo.estimateSessionMinutes(planId, dayOfWeek = 1)
        val wholePlan = repo.estimateSessionMinutes(planId)

        // Stary kod: 1 ćwiczenie × 10 = 10 min. Nowy: 3600 s = 60 min.
        assertEquals("cardio 3600s = 60 min", 60, perDay)
        assertEquals("cały plan (1 dzień) = 60 min", 60, wholePlan)
    }

    @Test
    fun `silowe sety liczone z reps i restSeconds`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.planRepo
        val exId = db.exerciseDao().getAll().first().id

        val planId = repo.upsertPlan(TrainingPlan(name = "Sila", daysOfWeek = listOf(1)))
        val peId = repo.upsertPlanExercise(
            PlanExercise(planId = planId, exerciseId = exId, dayOfWeek = 1, orderIndex = 0)
        )
        // 3 serie × (10 reps × 4 s + 90 s rest) = 3 × 130 = 390 s = 6 min
        repeat(3) { i ->
            repo.upsertPlanSet(
                PlanExerciseSet(
                    planExerciseId = peId, setNumber = i + 1, reps = 10, restSeconds = 90
                )
            )
        }

        assertEquals("3×(10×4+90)=390s → 6 min", 6, repo.estimateSessionMinutes(planId, 1))
    }

    @Test
    fun `pusty plan zwraca 0`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.planRepo
        val planId = repo.upsertPlan(TrainingPlan(name = "Pusty", daysOfWeek = listOf(1)))
        assertEquals("pusty plan = 0 min", 0, repo.estimateSessionMinutes(planId, 1))
        assertEquals("pusty plan całość = 0 min", 0, repo.estimateSessionMinutes(planId))
    }

    @Test
    fun `caly plan dzieli przez liczbe dni z cwiczeniami`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val repo = kit.planRepo
        val exId = db.exerciseDao().getAll().first().id

        val planId = repo.upsertPlan(TrainingPlan(name = "2-day", daysOfWeek = listOf(1, 3)))
        // Dzień 1: 1800 s, Dzień 3: 1800 s → suma 3600 s, /2 dni = 1800 s = 30 min
        listOf(1, 3).forEachIndexed { idx, day ->
            val peId = repo.upsertPlanExercise(
                PlanExercise(planId = planId, exerciseId = exId, dayOfWeek = day, orderIndex = idx)
            )
            repo.upsertPlanSet(
                PlanExerciseSet(planExerciseId = peId, setNumber = 1, durationSec = 1800)
            )
        }

        assertEquals("typowa sesja = 3600s/2dni = 30 min", 30, repo.estimateSessionMinutes(planId))
        assertEquals("konkretny dzień 1 = 30 min", 30, repo.estimateSessionMinutes(planId, 1))
    }
}
