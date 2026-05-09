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
    private val contextBuilder: AiContextBuilder
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
            val combined = buildString {
                append("Aktualnie jestem na ekranie aplikacji: **${_state.value.screenLabel}**.\n\n")
                append("Dane użytkownika (kontekst):\n```json\n$ctx\n```\n\n")
                append("Pytanie: $question\n\n")
                append("Odpowiedz krótko (max 4-5 zdań), konkretnie i po polsku. Cytuj liczby z kontekstu jeśli pasują.")
            }
            val apiMessages = _state.value.messages.dropLast(1).map {
                AiMessage(it.role, it.text)
            } + AiMessage(AiRole.USER, combined)

            client.chat(cfg, apiMessages, source = "AiQuickAsk").fold(
                onSuccess = { response ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        messages = _state.value.messages + QuickAskMessage(AiRole.ASSISTANT, response)
                    )
                },
                onFailure = { err ->
                    Log.e("AiQuickAsk", "ask failed", err)
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
