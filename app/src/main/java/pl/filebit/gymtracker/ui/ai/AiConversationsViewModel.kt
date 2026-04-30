package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.entity.AiConversation
import pl.filebit.gymtracker.data.repository.AiChatRepository
import javax.inject.Inject

data class AiConversationListItem(
    val conversation: AiConversation,
    val messageCount: Int,
    val preview: String
)

@HiltViewModel
class AiConversationsViewModel @Inject constructor(
    private val repo: AiChatRepository,
    aiPrefs: AiPreferences
) : ViewModel() {

    /** Etykieta modelu pokazywana w nagłówku ("GPT-4o", "claude-3-5-sonnet…"). */
    val modelLabel: String = aiPrefs.load().model
        .removePrefix("claude-")
        .replace("-", " ")
        .ifBlank { aiPrefs.load().provider.name.lowercase().replaceFirstChar { it.uppercase() } }
        .let {
            // skróć: gpt-4o → "GPT-4o", "3 5 sonnet 20241022" → "Sonnet 3.5"
            when {
                it.contains("gpt", ignoreCase = true) -> aiPrefs.load().model.uppercase()
                it.contains("sonnet", ignoreCase = true) -> "Claude Sonnet"
                it.contains("haiku", ignoreCase = true) -> "Claude Haiku"
                it.contains("opus", ignoreCase = true) -> "Claude Opus"
                else -> aiPrefs.load().model
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<AiConversationListItem>> = repo.observeConversations()
        .mapLatest { list ->
            list.map { c ->
                val msgs = repo.getMessages(c.id)
                val firstUser = msgs.firstOrNull { it.role == "user" }?.text.orEmpty()
                AiConversationListItem(
                    conversation = c,
                    messageCount = msgs.size,
                    preview = firstUser.replace('\n', ' ').take(80)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteConversation(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repo.deleteAllConversations() }
    }
}
