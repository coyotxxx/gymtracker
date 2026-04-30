package pl.filebit.gymtracker.ui.ai

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
import pl.filebit.gymtracker.ai.AiPlanApplier
import pl.filebit.gymtracker.ai.AiPlanProposal
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.AiRole
import javax.inject.Inject

data class ChatMessage(
    val role: AiRole,
    val text: String,
    val proposal: AiPlanProposal? = null,   // gdy AI zwrócił plan
    val applied: Boolean = false             // czy plan zastosowano
)

data class AiTrainerUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val providerName: String = "",
    val modelName: String = "",
    val planAppliedId: Long? = null
)

enum class QuickAction(val labelKey: String, val prompt: String) {
    PROPOSE_PLAN(
        "ai_action_plan",
        "Na podstawie moich celów, doświadczenia, dostępnego sprzętu i historii treningów zaproponuj kompletny plan treningowy. " +
            "Zwróć go jako blok JSON wewnątrz ```json ... ``` z polami: name, description, daysOfWeek, days [{dayOfWeek, exercises [{exerciseName, sets[{reps, weightKg, restSec}]}]}]. " +
            "Używaj DOKŁADNIE nazw ćwiczeń z mojej biblioteki (lista w 'available_exercises'). " +
            "Po JSON dodaj krótki opis dlaczego ten plan."
    ),
    TODAY(
        "ai_action_today",
        "Zaproponuj konkretną sesję treningową na dziś biorąc pod uwagę kiedy ostatnio trenowałem każdą partię, mój cel, mój poziom siły i ewentualne stagnacje. Podaj konkretne ćwiczenia, sety, powtórzenia i ciężary."
    ),
    ANALYZE_PROGRESS(
        "ai_action_progress",
        "Zrób szczegółową analizę mojego progresu z ostatnich tygodni: które ćwiczenia rosną, które stoją, czy są dysbalanse mięśniowe, jak wygląda moja objętość treningowa, czy progresja jest zdrowa. Konkretnie cytuj liczby i daty."
    ),
    DELOAD(
        "ai_action_deload",
        "Oceń czy potrzebuję deloadu lub zmiany strategii. Patrz na: stagnacje, częstotliwość treningów, poziom RPE, jakość snu jeśli ma w notatkach, łączną objętość. Daj konkretną rekomendację."
    ),
    FULL_STATS(
        "ai_action_stats",
        "Zrób pełną analizę moich statystyk: PR per ćwiczenie, łączna objętość, treningi w tygodniu/miesiącu, mapa zaangażowania mięśni, poziomy siły względem standardów, streaki, postęp wagi ciała vs cel. " +
            "Zorganizuj w sekcje: Overview, Główne podnoszenia, Zaangażowanie mięśni, Pomiary ciała, Mocne strony, Słabe strony, Następne kroki."
    ),
    WEEKLY_SUMMARY(
        "ai_action_weekly",
        "Zrób podsumowanie ostatniego tygodnia treningowego: ile treningów, jakie partie, łączna objętość, najlepsze sety (PR), porównanie do tygodnia poprzedniego. Wnioski + co zrobić w nadchodzącym tygodniu."
    )
}

@HiltViewModel
class AiTrainerViewModel @Inject constructor(
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val contextBuilder: AiContextBuilder,
    private val planApplier: AiPlanApplier
) : ViewModel() {

    private val _state = MutableStateFlow(AiTrainerUiState())
    val state: StateFlow<AiTrainerUiState> = _state.asStateFlow()

    init {
        refreshConnection()
    }

    fun refreshConnection() {
        val cfg = prefs.load()
        _state.value = _state.value.copy(
            isConnected = cfg.isConnected,
            providerName = cfg.provider.name,
            modelName = cfg.model
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        sendInternal(text)
    }

    fun runQuickAction(action: QuickAction) {
        sendInternal(action.prompt)
    }

    private fun sendInternal(prompt: String) {
        val cfg = prefs.load()
        if (!cfg.isConnected) {
            _state.value = _state.value.copy(error = "Skonfiguruj klucz API w ustawieniach")
            return
        }
        val userMsg = ChatMessage(AiRole.USER, prompt)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            isLoading = true,
            error = null,
            planAppliedId = null
        )

        viewModelScope.launch {
            // dołączamy kontekst tylko do PIERWSZEJ wiadomości (lub gdy quick action) — żeby
            // historia chatu nie nadymała tokenów; LLM ma już kontekst w pamięci sesji.
            val ctx = runCatching { contextBuilder.buildContextJson(recentWorkoutsLimit = 30) }
                .getOrElse { "{}" }

            val combined = if (_state.value.messages.size == 1) {
                "Dane użytkownika (kontekst):\n```json\n$ctx\n```\n\nPytanie/prośba:\n$prompt"
            } else {
                prompt
            }

            // historia: zamień ostatnią USER wiadomość na wersję z kontekstem (gdy pierwsza)
            val apiMessages = _state.value.messages.dropLast(1).map {
                AiMessage(it.role, it.text)
            } + AiMessage(AiRole.USER, combined)

            val result = client.chat(cfg, apiMessages)
            result.fold(
                onSuccess = { response ->
                    val proposal = planApplier.extractProposal(response)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        messages = _state.value.messages + ChatMessage(
                            AiRole.ASSISTANT,
                            response,
                            proposal = proposal
                        )
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = err.message ?: "Błąd komunikacji"
                    )
                }
            )
        }
    }

    fun applyProposal(message: ChatMessage) {
        val proposal = message.proposal ?: return
        viewModelScope.launch {
            planApplier.applyProposal(proposal).fold(
                onSuccess = { planId ->
                    _state.value = _state.value.copy(
                        planAppliedId = planId,
                        messages = _state.value.messages.map {
                            if (it === message) it.copy(applied = true) else it
                        }
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
            )
        }
    }

    fun clearChat() {
        _state.value = _state.value.copy(messages = emptyList(), error = null, planAppliedId = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun consumePlanAppliedNav() {
        _state.value = _state.value.copy(planAppliedId = null)
    }
}
