package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.NotificationCenter
import pl.filebit.gymtracker.data.backup.DietBackupManager
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.PeriodizationPreferences
import pl.filebit.gymtracker.data.repository.ReadNotificationsPrefs
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.service.ProactiveAiCheckScheduler
import pl.filebit.gymtracker.ui.backup.BackupViewModel
import pl.filebit.gymtracker.ui.goals.GoalsViewModel
import pl.filebit.gymtracker.ui.notifications.NotificationsViewModel
import pl.filebit.gymtracker.ui.profile.ProfileViewModel

/**
 * v1.27 — FAZA 3.9 — snapshoty ekranów profilu i ustawień.
 *
 * Profile, Goals, Backup, Notifications. TrainingSettings i Glossary
 * to ekrany bez własnego ViewModelu (Settings używa ProfileViewModel,
 * Glossary to statyczny słownik pojęć).
 */
class ProfileSnapshotTest : TestHarness() {


    @Test
    fun `Profile pokazuje dane uzytkownika`() = runBlocking {
        UserProfileRepository(db.userProfileDao()).save(
            UserProfile(displayName = "Maciej", bodyweightKg = 80.0,
                gender = Gender.MALE, daysPerWeek = 4))
        val kit = ViewModelKit(db, context)
        val vm = ProfileViewModel(
            kit.userProfileRepo, ProactiveAiCheckScheduler(context),
            PeriodizationPreferences(context), kit.dietProfileRepo)
        val profile = withTimeout(5_000) { vm.profile.first { it.displayName.isNotBlank() } }

        TraceReport("profile")
            .section("CO WIDZI USER")
            .kv("imię", profile.displayName)
            .kv("waga", "${profile.bodyweightKg} kg")
            .kv("dni/tydzień", profile.daysPerWeek.toString())
            .emit()

        assertEquals("imię użytkownika", "Maciej", profile.displayName)
        assertEquals("waga", 80.0, profile.bodyweightKg)
    }

    @Test
    fun `Goals empty state - brak celow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = GoalsViewModel(kit.goalRepo, kit.userProfileRepo)
        val progresses = withTimeout(5_000) { vm.progresses.first() }
        assertTrue("brak celów na starcie", progresses.isEmpty())
    }

    @Test
    fun `Backup ekran startuje bez statusu`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = BackupViewModel(
            context, kit.workoutRepo, kit.exerciseRepo, kit.userProfileRepo,
            AiPreferences(context), db, ExerciseSeeder(context, db.exerciseDao()),
            DietBackupManager(context, db, DietPreferences(context)), backupImporter
        )
        val status = vm.status.first()
        assertTrue("brak komunikatu statusu na starcie (bezczynny)", status == null)
    }

    @Test
    fun `Notifications empty state - brak powiadomien`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val center = NotificationCenter(
            kit.recoveryScoreCalculator, kit.trainingLoadAnalyzer, kit.phaseAnalyzer,
            db.workoutDao(), db.bodyMeasurementDao(), db.trainingMesocycleDao())
        val vm = NotificationsViewModel(center, ReadNotificationsPrefs(context))
        val items = withTimeout(5_000) { vm.items.first() }

        TraceReport("notifications")
            .section("CO WIDZI USER")
            .kv("powiadomienia", items.size.toString())
            .kv("nieprzeczytane", vm.unreadCount.value.toString())
            .emit()

        assertTrue("nowy user — brak powiadomień trenera", items.isEmpty())
    }
}
