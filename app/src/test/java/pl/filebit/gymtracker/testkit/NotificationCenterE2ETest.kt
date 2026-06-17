package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.AppNotification
import pl.filebit.gymtracker.ai.NotificationAction
import pl.filebit.gymtracker.ai.NotificationCenter
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.RecoveryLog
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v2.63.0 („domknij wszystko") — E2E NotificationCenter (proaktywne sygnały push). Ostatni
 * komponent. Sprawdza na różnych konfiguracjach danych NIEZMIENNIKI:
 *  - ŚWIEŻY user (0 treningów, 0 wagi) NIE dostaje sygnałów „zaangażowanych" (START_WORKOUT,
 *    log regeneracji) — strażnik przeciw nagabywaniu pustej bazy (bug klasy v2.56),
 *  - każdy sygnał poprawny: id/title/message niepuste, brak duplikatów id,
 *  - brak wyjątku na żadnej konfiguracji.
 */
class NotificationCenterE2ETest : TestHarness() {

    private val now = System.currentTimeMillis()
    private val day = 24L * 3600 * 1000

    private fun nc(): NotificationCenter {
        val kit = ViewModelKit(db, context)
        return NotificationCenter(
            kit.recoveryScoreCalculator, kit.trainingLoadAnalyzer, kit.phaseAnalyzer,
            db.workoutDao(), db.bodyMeasurementDao(), db.trainingMesocycleDao(), kit.adherenceCalc
        )
    }

    private fun checkWellFormed(notifs: List<AppNotification>, ctx: String, violations: MutableList<String>) {
        for (n in notifs) {
            if (n.id.isBlank()) violations += "PUSTE_ID: $ctx"
            if (n.title.isBlank()) violations += "PUSTY_TITLE: $ctx (${n.id})"
            if (n.message.isBlank()) violations += "PUSTY_MESSAGE: $ctx (${n.id})"
        }
        if (notifs.map { it.id }.toSet().size != notifs.size)
            violations += "DUPLIKAT_ID: $ctx → ${notifs.map { it.id }}"
    }

    /** Sygnały wymagające „zaangażowania" (trening/waga) — NIE wolno ich pokazać świeżemu userowi. */
    private val engagedActions = setOf(NotificationAction.START_WORKOUT, NotificationAction.LOG_RECOVERY)

    @Test
    fun `swiezy user bez danych - zero sygnalow zaangazowanych`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.FAT_LOSS))
        val notifs = nc().computeNotifications()
        val violations = mutableListOf<String>()
        checkWellFormed(notifs, "fresh", violations)
        // świeży user (0 treningów, 0 wagi) — brak sygnałów zaangażowanych
        val engaged = notifs.filter { it.actionType in engagedActions }
        if (engaged.isNotEmpty()) violations += "NAGABYWANIE_SWIEZEGO: ${engaged.map { it.id }}"

        TraceReport("nc-fresh").section("ŚWIEŻY USER")
            .kv("sygnałów", notifs.size.toString())
            .verdict("zaangażowane", engaged.size.toString(), "oczekiwane: 0").emit()

        assertTrue("naruszenia:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `rozne konfiguracje danych - sygnaly zawsze poprawne`() = runBlocking {
        val violations = mutableListOf<String>()
        val repo = UserProfileRepository(db.userProfileDao())

        // Konfiguracja A: zaangażowany (waga), brak treningu
        repo.save(UserProfile(bodyweightKg = 84.0, gender = Gender.MALE, goalType = DietGoalType.FAT_LOSS))
        db.bodyMeasurementDao().upsert(BodyMeasurement(date = now - day, weightKg = 84.0))
        checkWellFormed(nc().computeNotifications(), "A-waga-bez-treningu", violations)

        // Konfiguracja B: trening świeży + waga
        db.workoutDao().insert(Workout(startedAt = now - day, finishedAt = now - day))
        checkWellFormed(nc().computeNotifications(), "B-trening-swiezy", violations)

        // Konfiguracja C: trening dawno (przerwa) + stara regeneracja
        db.workoutDao().insert(Workout(startedAt = now - 20 * day, finishedAt = now - 20 * day))
        db.recoveryLogDao().insert(RecoveryLog(dateMs = now - 35 * day, sleepHours = 7.0))
        checkWellFormed(nc().computeNotifications(), "C-przerwa-stara-regen", violations)

        // Konfiguracja D: wiele treningów (potencjalny load/deload)
        for (i in 1..15) db.workoutDao().insert(Workout(startedAt = now - i * day, finishedAt = now - i * day))
        checkWellFormed(nc().computeNotifications(), "D-duzo-treningow", violations)

        TraceReport("nc-configs").section("RÓŻNE KONFIGURACJE")
            .verdict("naruszeń", violations.size.toString(), "oczekiwane: 0").emit()
        assertTrue("naruszenia:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
