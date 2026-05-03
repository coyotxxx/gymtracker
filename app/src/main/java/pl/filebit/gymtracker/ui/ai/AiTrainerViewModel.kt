package pl.filebit.gymtracker.ui.ai

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import pl.filebit.gymtracker.data.entity.AiChatMessageEntity
import pl.filebit.gymtracker.data.repository.AiChatRepository
import javax.inject.Inject

data class ChatMessage(
    val id: Long = 0L,           // 0 dopóki nie zapisana w DB
    val role: AiRole,
    val text: String,
    val proposal: AiPlanProposal? = null,
    val applied: Boolean = false
)

data class PendingValidation(
    val messageId: Long,
    val validation: pl.filebit.gymtracker.ai.ProposalValidation
)

data class AiTrainerUiState(
    val conversationId: Long = 0L,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val providerName: String = "",
    val modelName: String = "",
    val planAppliedId: Long? = null,
    val pendingValidation: PendingValidation? = null
) {
    /** Ostatnia wiadomość ASSISTANT z planem, której jeszcze nie zastosowano. */
    val pendingProposalMessage: ChatMessage?
        get() = messages.lastOrNull { it.proposal != null && !it.applied }
}

enum class QuickAction(val labelKey: String, val prompt: String) {
    PROPOSE_PLAN(
        "ai_action_plan",
        "Na podstawie moich celów, doświadczenia, dostępnego sprzętu i historii treningów zaproponuj kompletny plan treningowy. " +
            "SPRAWDŹ pole `painArea` i `wellbeing` w `recent_workouts` — jeśli zgłaszałem ból konkretnej partii, UNIKAJ ćwiczeń ją mocno obciążających lub zaproponuj alternatywy. " +
            "Zwróć go jako blok JSON wewnątrz ```json ... ``` z polami: name, description, daysOfWeek, days [{dayOfWeek, exercises [{exerciseName, sets[{reps, weightKg, restSec}]}]}]. " +
            "Używaj DOKŁADNIE nazw ćwiczeń z mojej biblioteki (lista w 'available_exercises'). " +
            "Po JSON dodaj krótki opis dlaczego ten plan."
    ),
    TODAY(
        "ai_action_today",
        "Zaproponuj konkretną sesję treningową na dziś biorąc pod uwagę kiedy ostatnio trenowałem każdą partię, mój cel, mój poziom siły i ewentualne stagnacje. " +
            "SPRAWDŹ wellbeing/painArea w ostatnich `recent_workouts` — jeśli niski wellbeing (1-2) lub świeży ból, zaproponuj LŻEJSZĄ sesję lub pomiń bolące partie. " +
            "Podaj konkretne ćwiczenia, sety, powtórzenia i ciężary."
    ),
    ANALYZE_PROGRESS(
        "ai_action_progress",
        "Zrób szczegółową analizę mojego progresu z ostatnich tygodni: które ćwiczenia rosną, które stoją, czy są dysbalanse mięśniowe, jak wygląda moja objętość treningowa, czy progresja jest zdrowa. " +
            "Uwzględnij wellbeing/painArea — częsty ból lub niski wellbeing wpływa na jakość progresu. " +
            "Konkretnie cytuj liczby i daty."
    ),
    DELOAD(
        "ai_action_deload",
        "Oceń czy potrzebuję deloadu lub zmiany strategii. Patrz na: stagnacje, częstotliwość treningów, poziom RPE, jakość snu jeśli ma w notatkach, łączną objętość, " +
            "ORAZ wellbeing/painArea z `recent_workouts` — powtarzający się ból lub seria niskiego wellbeing to silny sygnał na deload. " +
            "Daj konkretną rekomendację."
    ),
    FULL_STATS(
        "ai_action_stats",
        "Zrób pełną analizę moich statystyk: PR per ćwiczenie, łączna objętość, treningi w tygodniu/miesiącu, mapa zaangażowania mięśni, poziomy siły względem standardów, streaki, postęp wagi ciała vs cel. " +
            "Zorganizuj w sekcje: Overview, Główne podnoszenia, Zaangażowanie mięśni, Pomiary ciała, Mocne strony, Słabe strony, Następne kroki."
    ),
    WEEKLY_SUMMARY(
        "ai_action_weekly",
        "Zrób podsumowanie ostatniego tygodnia treningowego: ile treningów, jakie partie, łączna objętość, najlepsze sety (PR), porównanie do tygodnia poprzedniego. Wnioski + co zrobić w nadchodzącym tygodniu."
    ),
    GOAL_PROGRESS(
        "ai_action_goal",
        "Zanalizuj mój postęp do aktywnych celów (active_goals). Dla każdego celu: czy idę zgodnie z planem, czy dotrę na czas, co konkretnie zmienić w treningu/diecie/cardio żeby przyspieszyć. Cytuj liczby i daty."
    ),
    PAIN_RECOVERY(
        "ai_action_pain_recovery",
        "Sprawdź pola painArea/painNotes/wellbeing w moich ostatnich treningach (recent_workouts). " +
            "Jeśli zgłosiłem ból (painArea) lub niskie samopoczucie (wellbeing 1-2) — zaproponuj " +
            "konkretne zmiany w planie / sesji najbliższego dnia: które ćwiczenia pominąć, czym " +
            "zastąpić, czy potrzebny deload. Bądź konkretny — cytuj nazwy ćwiczeń. " +
            "Jeśli chcesz wygenerować poprawiony plan — odpowiedz blokiem JSON jak w PROPOSE_PLAN."
    )
}

@HiltViewModel
class AiTrainerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val app: Context,
    private val client: AiClient,
    private val prefs: AiPreferences,
    private val contextBuilder: AiContextBuilder,
    private val planApplier: AiPlanApplier,
    private val chatRepo: AiChatRepository
) : ViewModel() {

    private fun toast(text: String) {
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }

    // 0L = nowa konwersacja (utworzy się przy pierwszej wiadomości)
    private val initialConversationId: Long =
        savedStateHandle.get<String>("conversationId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(AiTrainerUiState(conversationId = initialConversationId))
    val state: StateFlow<AiTrainerUiState> = _state.asStateFlow()

    init {
        refreshConnection()
        if (initialConversationId > 0L) loadConversation(initialConversationId)
    }

    private fun loadConversation(id: Long) {
        viewModelScope.launch {
            val entities = chatRepo.getMessages(id)
            val messages = entities.map { it.toChatMessage() }
            _state.value = _state.value.copy(
                conversationId = id,
                messages = messages
            )
        }
    }

    private fun AiChatMessageEntity.toChatMessage(): ChatMessage {
        val parsedRole = if (role == AiRole.USER.name) AiRole.USER else AiRole.ASSISTANT
        val proposal = if (parsedRole == AiRole.ASSISTANT) planApplier.extractProposal(text) else null
        return ChatMessage(
            id = id,
            role = parsedRole,
            text = text,
            proposal = proposal,
            applied = applied
        )
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
        val isFirstMessage = _state.value.messages.isEmpty()
        val userMsgUi = ChatMessage(role = AiRole.USER, text = prompt)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsgUi,
            isLoading = true,
            error = null,
            planAppliedId = null
        )

        viewModelScope.launch {
            val convId = ensureConversation(titleHint = if (isFirstMessage) prompt else null)

            val savedUserId = chatRepo.addMessage(
                AiChatMessageEntity(
                    conversationId = convId,
                    role = AiRole.USER.name,
                    text = prompt
                )
            )
            // przepnij ostatnią user wiadomość na zapisaną wersję z id
            _state.value = _state.value.copy(
                conversationId = convId,
                messages = _state.value.messages.toMutableList().also {
                    val idx = it.indexOfLast { m -> m.role == AiRole.USER && m.id == 0L }
                    if (idx >= 0) it[idx] = it[idx].copy(id = savedUserId)
                }
            )

            val ctx = runCatching { contextBuilder.buildContextJson(recentWorkoutsLimit = 30) }
                .getOrElse { "{}" }

            val combined = if (isFirstMessage) {
                "Dane użytkownika (kontekst):\n```json\n$ctx\n```\n\nPytanie/prośba:\n$prompt"
            } else {
                prompt
            }

            val apiMessages = _state.value.messages.dropLast(1).map {
                AiMessage(it.role, it.text)
            } + AiMessage(AiRole.USER, combined)

            val result = client.chat(cfg, apiMessages)
            result.fold(
                onSuccess = { response ->
                    val proposal = planApplier.extractProposal(response)
                    val savedAssistantId = chatRepo.addMessage(
                        AiChatMessageEntity(
                            conversationId = convId,
                            role = AiRole.ASSISTANT.name,
                            text = response
                        )
                    )
                    chatRepo.touchConversation(convId)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        messages = _state.value.messages + ChatMessage(
                            id = savedAssistantId,
                            role = AiRole.ASSISTANT,
                            text = response,
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

    private suspend fun ensureConversation(titleHint: String?): Long {
        val current = _state.value.conversationId
        if (current > 0L) {
            if (!titleHint.isNullOrBlank()) {
                val conv = chatRepo.getConversation(current)
                if (conv != null && conv.title.isBlank()) {
                    chatRepo.updateConversation(conv.copy(title = titleHint.take(60)))
                }
            }
            return current
        }
        return chatRepo.createConversation(title = titleHint?.take(60).orEmpty())
    }

    fun applyProposal(message: ChatMessage, force: Boolean = false) {
        Log.d("AiTrainerVM", "applyProposal click: msg.id=${message.id} hasProposal=${message.proposal != null} applied=${message.applied} force=$force")
        val proposal = message.proposal
        if (proposal == null) {
            Log.w("AiTrainerVM", "applyProposal: proposal is null")
            _state.value = _state.value.copy(error = "Brak danych planu w wiadomości")
            return
        }
        if (_state.value.isApplying) {
            Log.d("AiTrainerVM", "applyProposal: already applying, ignored")
            return
        }
        val targetIndex = _state.value.messages.indexOfFirst { it.id != 0L && it.id == message.id }
            .takeIf { it >= 0 }
            ?: _state.value.messages.indexOfLast { it.proposal != null && !it.applied }

        // Walidacja przed zastosowaniem — jeśli AI wymyśliło ćwiczenia spoza biblioteki,
        // pokaż user'owi ostrzeżenie i pozwól mu zdecydować (force = true → mimo to apply)
        viewModelScope.launch {
            if (!force) {
                val validation = planApplier.validateProposal(proposal)
                if (validation.unmatchedNames.isNotEmpty()) {
                    Log.w("AiTrainerVM", "validation found ${validation.unmatchedNames.size} unmatched: ${validation.unmatchedNames}")
                    _state.value = _state.value.copy(
                        pendingValidation = PendingValidation(
                            messageId = message.id,
                            validation = validation
                        )
                    )
                    return@launch
                }
            }
            toast("Zapisuję plan…")
            _state.value = _state.value.copy(isApplying = true, error = null, pendingValidation = null)
            planApplier.applyProposal(proposal).fold(
                onSuccess = { planId ->
                    Log.d("AiTrainerVM", "applyProposal SUCCESS planId=$planId")
                    val updatedList = _state.value.messages.toMutableList()
                    if (targetIndex >= 0 && targetIndex < updatedList.size) {
                        val msg = updatedList[targetIndex]
                        updatedList[targetIndex] = msg.copy(applied = true)
                        if (msg.id != 0L) chatRepo.markMessageApplied(msg.id)
                    }
                    _state.value = _state.value.copy(
                        isApplying = false,
                        planAppliedId = planId,
                        messages = updatedList
                    )
                },
                onFailure = { err ->
                    Log.e("AiTrainerVM", "applyProposal FAIL: ${err.message}", err)
                    _state.value = _state.value.copy(
                        isApplying = false,
                        error = "Błąd zapisu: ${err.message ?: err::class.simpleName ?: "?"}"
                    )
                }
            )
        }
    }

    /** User zaakceptował zastosowanie mimo niedopasowanych ćwiczeń. */
    fun confirmApplyDespiteValidation() {
        val pv = _state.value.pendingValidation ?: return
        val msg = _state.value.messages.firstOrNull {
            (it.id != 0L && it.id == pv.messageId) || (it.id == 0L && it.proposal != null && !it.applied)
        } ?: return
        _state.value = _state.value.copy(pendingValidation = null)
        applyProposal(msg, force = true)
    }

    fun cancelApplyValidation() {
        _state.value = _state.value.copy(pendingValidation = null)
    }

    /**
     * Czyści bieżącą rozmowę (kasuje konwersację z DB i restartuje stan na pustą).
     */
    fun clearChat() {
        val convId = _state.value.conversationId
        viewModelScope.launch {
            if (convId > 0L) chatRepo.deleteConversation(convId)
            _state.value = _state.value.copy(
                conversationId = 0L,
                messages = emptyList(),
                error = null,
                planAppliedId = null
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun consumePlanAppliedNav() {
        _state.value = _state.value.copy(planAppliedId = null)
    }
}
