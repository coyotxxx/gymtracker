package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.AiChatMessageEntity

@Dao
interface AiChatMessageDao {

    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getForConversation(conversationId: Long): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: AiChatMessageEntity): Long

    @Update
    suspend fun update(message: AiChatMessageEntity)

    @Query("DELETE FROM ai_chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("UPDATE ai_chat_messages SET applied = 1 WHERE id = :id")
    suspend fun markApplied(id: Long)

    @Query("SELECT COUNT(*) FROM ai_chat_messages WHERE conversationId = :conversationId")
    suspend fun countForConversation(conversationId: Long): Int
}
