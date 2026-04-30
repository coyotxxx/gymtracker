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
import pl.filebit.gymtracker.data.entity.AiConversation
import pl.filebit.gymtracker.data.repository.AiChatRepository
import javax.inject.Inject

data class AiConversationListItem(
    val conversation: AiConversation,
    val messageCount: Int
)

@HiltViewModel
class AiConversationsViewModel @Inject constructor(
    private val repo: AiChatRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<AiConversationListItem>> = repo.observeConversations()
        .mapLatest { list ->
            list.map { c -> AiConversationListItem(c, repo.countMessages(c.id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteConversation(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repo.deleteAllConversations() }
    }
}
