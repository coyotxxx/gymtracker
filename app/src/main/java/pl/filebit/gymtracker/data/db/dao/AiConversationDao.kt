package pl.filebit.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.entity.AiConversation

@Dao
interface AiConversationDao {

    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiConversation>>

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getById(id: Long): AiConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: AiConversation): Long

    @Update
    suspend fun update(conversation: AiConversation)

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_chat_messages WHERE conversationId = :id")
    suspend fun countMessages(id: Long): Int
}
