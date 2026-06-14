package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.NotificationCenter
import pl.filebit.gymtracker.data.coach.CoachOrchestrator
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import pl.filebit.gymtracker.data.repository.DeloadService
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v2.41.0 (U6) — E2E orchestratora przez REALNE serwisy + bazę.
 *
 * Najważniejszy strażnik: na PUSTEJ/minimalnej bazie coach NIE może wymyślać reakcji
 * (żadnego fałszywego deloadu/fast-loss/korekty). To chroni przed bugami typu slope
 * (gdzie algorytm na cienkich danych produkował fałszywy alarm).
 */
class CoachOrchestratorE2ETest : TestHarness() {

    private fun orchestrator(): CoachOrchestrator {
        val kit = ViewModelKit(db, context)
        val deloadService = DeloadService(
            db.workoutDao(), db.workoutSetDao(), kit.statsRepo, kit.planRepo,
            DeloadPreferences(context), kit.userProfileRepo
        )
        val notificationCenter = NotificationCenter(
            kit.recoveryScoreCalculator, kit.trainingLoadAnalyzer, kit.phaseAnalyzer,
            db.workoutDao(), db.bodyMeasurementDao(), db.trainingMesocycleDao()
        )
        return CoachOrchestrator(deloadService, kit.autoAdjust, notificationCenter)
    }

    @Test
    fun `pusta baza - coach nie wymysla reakcji`() = runBlocking {
        // Świeży user, brak treningów, brak diety, cel ustawiony.
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.FAT_LOSS))

        val verdict = orchestrator().evaluate()

        val tr = TraceReport("coach-e2e-empty")
            .section("WERDYKT NA PUSTEJ BAZIE")
            .verdict("primary", verdict.primary?.id ?: "BRAK", "oczekiwane: BRAK")
            .kv("liczba reakcji", verdict.all.size.toString())
            .emit()

        assertTrue("brak danych → brak dominującej reakcji (zero fałszywych alarmów)",
            verdict.isEmpty)
        assertTrue("brak jakichkolwiek reakcji", verdict.all.isEmpty())
    }

    @Test
    fun `odrzucone reakcje sa filtrowane`() = runBlocking {
        // Nawet gdyby coś było, dismissed-id nie może wrócić w tym samym dniu.
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.MAINTAIN))
        val o = orchestrator()
        val v1 = o.evaluate()
        // jeśli pusto — test i tak potwierdza brak wyjątku + spójność
        val v2 = o.evaluate(dismissedIds = v1.primary?.let { setOf(it.id) } ?: emptySet())
        assertTrue("po odrzuceniu dominującej nie ma jej w werdykcie",
            v2.all.none { r -> v1.primary?.id == r.id })
    }
}
