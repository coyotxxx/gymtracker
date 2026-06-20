package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "USER" | "ASSISTANT" | "SYSTEM"
    val text: String,
    val applied: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // v2.71.0: zdjęcia dołączone do wiadomości (czat z obrazami). JSON List<AiImage>
    // ({base64, mimeType}) lub null gdy brak. Używane do redisplay historii czatu.
    val imagesJson: String? = null
)
