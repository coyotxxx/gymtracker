package pl.filebit.gymtracker.testkit

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ui.ai.AiConversationsViewModel
import pl.filebit.gymtracker.ui.ai.AiLogViewModel
import pl.filebit.gymtracker.ui.ai.AiSettingsViewModel
import pl.filebit.gymtracker.ui.ai.AiTrainerViewModel
import pl.filebit.gymtracker.ui.ai.WeeklyReportViewModel

/**
 * v1.27 — FAZA 3.8 — snapshoty ekranów AI.
 *
 * AiTrainer, AiConversations, AiSettings, AiLog, AiWeeklyReport. Test
 * sprawdza stan początkowy bez klucza API (BYOK) — ekrany muszą się
 * składać i pokazywać "AI nieskonfigurowane" zamiast crashować.
 */
class AiSnapshotTest : TestHarness() {


    @Test
    fun `AiTrainer startuje nowa rozmowe bez klucza API`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AiTrainerViewModel(
            SavedStateHandle(), context, kit.aiClient, kit.aiPrefs,
            kit.aiContextBuilder, kit.aiPlanApplier, kit.aiChatRepo,
            kit.planRepo, kit.exerciseRepo, kit.aiToolHandler
        )
        val s = withTimeout(5_000) { vm.state.first() }

        TraceReport("ai-trainer")
            .section("CO WIDZI USER")
            .kv("conversationId", s.conversationId.toString())
            .kv("wiadomości", s.messages.size.toString())
            .verdict("isConnected", s.isConnected.toString(), "false = brak klucza BYOK")
            .emit()

        assertEquals("nowa rozmowa (id=0)", 0L, s.conversationId)
        assertFalse("bez klucza API — AI nie połączone", s.isConnected)
    }

    @Test
    fun `AiConversations empty state - brak rozmow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AiConversationsViewModel(kit.aiChatRepo, kit.aiPrefs)
        val items = withTimeout(5_000) { vm.conversations.first() }
        assertTrue("brak zapisanych rozmów AI", items.isEmpty())
    }

    @Test
    fun `AiSettings pokazuje konfiguracje BYOK`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AiSettingsViewModel(kit.aiPrefs, kit.aiClient)
        val s = withTimeout(5_000) { vm.state.first() }

        TraceReport("ai-settings")
            .section("CO WIDZI USER")
            .kv("provider", s.config.provider.name)
            .verdict("testowanie", s.testing.toString(), "false = bezczynne")
            .emit()

        assertFalse("nie testuje połączenia na starcie", s.testing)
    }

    @Test
    fun `AiLog empty state - brak logow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = AiLogViewModel(kit.aiLogRepo, kit.aiPrefs)
        val logs = withTimeout(5_000) { vm.logs.first() }
        assertTrue("brak logów AI na starcie", logs.isEmpty())
    }

    @Test
    fun `WeeklyReport empty state - brak raportow`() = runBlocking {
        val kit = ViewModelKit(db, context)
        val vm = WeeklyReportViewModel(
            kit.weeklyReportService, db.aiWeeklyReportDao(),
            kit.planRepo, kit.exerciseRepo, kit.aiPlanApplier
        )
        val s = withTimeout(5_000) { vm.state.first() }
        val reports = vm.reports.first()

        assertFalse("nie generuje raportu na starcie", s.isLoading)
        assertTrue("brak raportów tygodniowych na starcie", reports.isEmpty())
    }
}
