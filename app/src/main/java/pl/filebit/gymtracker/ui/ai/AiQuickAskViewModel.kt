package pl.filebit.gymtracker.ui.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiClient
import pl.filebit.gymtracker.ai.AiContextBuilder
import pl.filebit.gymtracker.ai.AiMessage
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.AiRole
import javax.inject.Inject

data class QuickAskMessage(
    val role: AiRole,
    val text: String
)

data class AiQuickAskUiState(
    val screenLabel: String = "",
    val messages: List<QuickAskMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Lekki VM do popupu "Zapytaj o ekran". Trzyma własny krótki czat — niezależny
 * od głównego AsystentTrainerVM (i jego persystencji w Room). Pierwsza wiadomość
 * niesie kontekst aktualnego ekranu + globalny user context (treningi, profil itd.)
 */
@HiltViewModel
class AiQuickAskViewModel @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val contextBuilder: AiContextBuilder,
    // v2.21.0 — kontekst diety (faza, adherence, ulubione produkty) + log diagnostyczny
    private val masterContextBuilder: pl.filebit.gymtracker.ai.MasterAiContextBuilder,
    private val diag: pl.filebit.gymtracker.data.repository.DiagnosticLogger,
    // v2.22.0 — narzędzia (w tym ZAPIS danych na prośbę usera)
    private val toolHandler: pl.filebit.gymtracker.ai.AiToolHandler
) : ViewModel() {

    private val _state = MutableStateFlow(AiQuickAskUiState())
    val state: StateFlow<AiQuickAskUiState> = _state.asStateFlow()

    fun setScreen(label: String) {
        // Reset przy każdym otwarciu popupu na nowym ekranie
        _state.value = AiQuickAskUiState(screenLabel = label)
    }

    fun ask(question: String) {
        if (question.isBlank()) return
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            _state.value = _state.value.copy(error = "Skonfiguruj klucz API")
            return
        }
        val userMsg = QuickAskMessage(AiRole.USER, question)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            isLoading = true,
            error = null
        )
        viewModelScope.launch {
            // Kontekst dolaczany do KAZDEGO pytania (snapshot zawsze swiezy)
            // v1.11.60: QuickAsk to mini-pytania o ekran - nie potrzebuje pelnego planu/biblioteki.
            // targetPlanId NIE ustawiany (default null), recentWorkoutsLimit z default (5).
            val ctx = runCatching { contextBuilder.buildContextJson() }
                .getOrElse { "{}" }
            // v2.21.0: kontekst DIETY (faza, adherence, profil diety, ulubione produkty) —
            // żeby asystent mógł odpowiadać konkretnie i o posiłkach, nie tylko treningu.
            val dietSection = runCatching {
                val m = masterContextBuilder.build()
                val h = pl.filebit.gymtracker.ai.MasterAiContextPromptHelper
                buildString {
                    append(h.toDietProfileSection(m))
                    append(h.toAdherenceSection(m))
                    append(h.toCurrentStateSection(m))
                    append(h.toFavoritesFoodSection(m))
                }
            }.getOrDefault("")
            val combined = buildString {
                // v2.21.0: zunifikowana persona — trener I dietetyk. Bez odsyłania do
                // "dietetyka", bo apka NIM jest (split-brain naprawiony).
                append("Jesteś moim trenerem ORAZ dietetykiem personalnym w tej aplikacji. ")
                append("Odpowiadasz konkretnie i o treningu, i o diecie/posiłkach. NIGDY nie odsyłaj ")
                append("do zewnętrznego dietetyka ani trenera — to TY nim jesteś. Gdy pytam o dietę, ")
                append("posiłek czy zamiennik — doradź konkretnie (produkty, makro, szybkie opcje), ")
                append("korzystając z kontekstu diety poniżej. ")
                // v2.23.0 (K6 fix): narzędzia zapisu działają TYLKO dla Anthropic (OpenAI =
                // fallback bez tools). Nie obiecuj zapisu, którego provider nie wykona.
                if (cfg.provider == pl.filebit.gymtracker.ai.AiProvider.ANTHROPIC) {
                    append("Możesz też ZMIENIAĆ moje dane gdy o to wprost proszę (zapis wagi, dodanie posiłku, ")
                    append("ustawienie celu kcal lub kierunku diety) — użyj do tego dostępnych narzędzi.\n\n")
                } else {
                    append("NIE masz możliwości zapisu danych w tym trybie — jeśli proszę o zmianę danych, ")
                    append("powiedz mi krótko, jak zrobić to ręcznie w aplikacji.\n\n")
                }
                append("Aktualnie jestem na ekranie aplikacji: **${_state.value.screenLabel}**.\n\n")
                append("=== KONTEKST TRENINGOWY ===\n```json\n$ctx\n```\n\n")
                if (dietSection.isNotBlank()) {
                    append("=== KONTEKST DIETY ===\n$dietSection\n")
                }
                append("Pytanie: $question\n\n")
                append("Odpowiedz krótko (max 5-6 zdań), konkretnie i po polsku. Cytuj liczby z kontekstu jeśli pasują.")
            }
            val apiMessages = _state.value.messages.dropLast(1).map {
                AiMessage(it.role, it.text)
            } + AiMessage(AiRole.USER, combined)

            client.chatWithTools(cfg, apiMessages, toolHandler, source = "AiQuickAsk").fold(
                onSuccess = { response ->
                    // v2.21.0: heurystyka deflekcji — sygnał jakości, jeśli AI odsyła "do dietetyka/trenera"
                    val deflected = Regex("(zwróć się|skonsultuj|udaj się|warto.*zwróć).{0,30}(dietetyk|trener)", RegexOption.IGNORE_CASE)
                        .containsMatchIn(response)
                    val diagCat = pl.filebit.gymtracker.data.entity.DiagnosticCategory.AI
                    if (deflected) {
                        diag.warn(diagCat, "AiQuickAsk", "ai_deflected",
                            "AI odesłał do zewnętrznego specjalisty (ekran: ${_state.value.screenLabel})")
                    } else {
                        diag.info(diagCat, "AiQuickAsk", "answered",
                            "Odpowiedź na pytanie o ekran: ${_state.value.screenLabel}", success = true)
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        messages = _state.value.messages + QuickAskMessage(AiRole.ASSISTANT, response)
                    )
                },
                onFailure = { err ->
                    Log.e("AiQuickAsk", "ask failed", err)
                    diag.warn(pl.filebit.gymtracker.data.entity.DiagnosticCategory.AI, "AiQuickAsk",
                        "ask_failed", "Błąd zapytania o ekran: ${err.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = err.message ?: "Błąd komunikacji z AI"
                    )
                }
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
