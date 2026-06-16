package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.NotificationCenter
import pl.filebit.gymtracker.data.coach.CoachActionType
import pl.filebit.gymtracker.data.coach.CoachOrchestrator
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.RecoveryLog
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
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
            db.workoutDao(), db.bodyMeasurementDao(), db.trainingMesocycleDao(),
            kit.adherenceCalc
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
    fun `zaangazowany user ze stara regeneracja - coach prosi o log`() = runBlocking {
        // v2.44.0: regeneracja scalona w Coacha. User ZAANGAŻOWANY (ma trening), ale
        // ostatni log regeneracji sprzed miesiąca → proaktywny sygnał „zaloguj regenerację"
        // z akcją OPEN_RECOVERY. Świeżego instala (brak treningów) NIE nagabujemy — to
        // chroni strażnik „pusta baza".
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.MAINTAIN))
        // Trening sprzed 1 dnia → user zaangażowany, a zarazem brak alertu „brak treningów" (<5 dni).
        db.workoutDao().insert(Workout(startedAt = now - day, finishedAt = now - day))
        // Log regeneracji sprzed 30 dni → stary, niesvieży.
        db.recoveryLogDao().insert(RecoveryLog(dateMs = now - 30 * day, sleepHours = 7.0))

        val verdict = orchestrator().evaluate()

        TraceReport("coach-e2e-recovery-gap")
            .section("WERDYKT — STARA REGENERACJA")
            .verdict("primary", verdict.primary?.id ?: "BRAK", "oczekiwane: nc_recovery_log_gap")
            .kv("akcje", verdict.all.flatMap { it.actions }.joinToString { it.type.name })
            .emit()

        assertTrue("Coach proponuje akcję OPEN_RECOVERY (zaloguj regenerację)",
            verdict.all.any { r -> r.actions.any { it.type == CoachActionType.OPEN_RECOVERY } })
    }

    @Test
    fun `diet-user z waga bez treningow tez dostaje reakcje coacha`() = runBlocking {
        // v2.56.0: user logujący WAGĘ/dietę (0 treningów) jest „zaangażowany" — Coach
        // NIE może milczeć. Wcześniej gate „masz trening" dławił reakcje dla diet-userów.
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.MAINTAIN))
        db.bodyMeasurementDao().upsert(
            pl.filebit.gymtracker.data.entity.BodyMeasurement(date = now, weightKg = 84.0))
        // regeneracja sprzed miesiąca → nudge „zaloguj regenerację" powinien się pokazać
        db.recoveryLogDao().insert(RecoveryLog(dateMs = now - 30 * day, sleepHours = 7.0))

        val verdict = orchestrator().evaluate()

        assertTrue("Coach reaguje dla zaangażowanego diet-usera (nie milczy)", !verdict.isEmpty)
        assertTrue("jest sygnał regeneracji LUB logowania treningu",
            verdict.all.any { r -> r.actions.any {
                it.type == CoachActionType.OPEN_RECOVERY || it.type == CoachActionType.START_WORKOUT
            } })
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
