package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.AiChatMessageDao
import pl.filebit.gymtracker.data.db.dao.AiConversationDao
import pl.filebit.gymtracker.data.entity.AiChatMessageEntity
import pl.filebit.gymtracker.data.entity.AiConversation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiChatRepository @Inject constructor(
    private val conversationDao: AiConversationDao,
    private val messageDao: AiChatMessageDao
) {
    fun observeConversations(): Flow<List<AiConversation>> = conversationDao.observeAll()

    suspend fun getConversation(id: Long): AiConversation? = conversationDao.getById(id)

    suspend fun createConversation(title: String = ""): Long {
        val now = System.currentTimeMillis()
        return conversationDao.upsert(
            AiConversation(title = title, createdAt = now, updatedAt = now)
        )
    }

    suspend fun updateConversation(conversation: AiConversation) {
        conversationDao.update(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchConversation(id: Long, title: String? = null) {
        val current = conversationDao.getById(id) ?: return
        conversationDao.update(
            current.copy(
                title = title ?: current.title,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteConversation(id: Long) = conversationDao.deleteById(id)

    suspend fun deleteAllConversations() = conversationDao.deleteAll()

    suspend fun countMessages(conversationId: Long): Int =
        conversationDao.countMessages(conversationId)

    fun observeMessages(conversationId: Long): Flow<List<AiChatMessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun getMessages(conversationId: Long): List<AiChatMessageEntity> =
        messageDao.getForConversation(conversationId)

    suspend fun addMessage(message: AiChatMessageEntity): Long = messageDao.upsert(message)

    suspend fun markMessageApplied(messageId: Long) = messageDao.markApplied(messageId)
}
