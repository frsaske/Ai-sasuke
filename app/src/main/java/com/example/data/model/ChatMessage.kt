package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    fun toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        timestamp = timestamp,
        isStreaming = isStreaming,
        isError = isError,
        errorMessage = errorMessage
    )

    companion object {
        fun fromEntity(entity: MessageEntity): ChatMessage = ChatMessage(
            id = entity.id,
            conversationId = entity.conversationId,
            role = entity.role,
            content = entity.content,
            timestamp = entity.timestamp,
            isStreaming = entity.isStreaming,
            isError = entity.isError,
            errorMessage = entity.errorMessage
        )
    }
}
