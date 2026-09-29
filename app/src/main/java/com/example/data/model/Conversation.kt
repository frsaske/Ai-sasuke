package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "gemini-3.5-flash",
    val systemPrompt: String = ""
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "gemini-3.5-flash",
    val systemPrompt: String = ""
) {
    fun toEntity(): ConversationEntity = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        model = model,
        systemPrompt = systemPrompt
    )

    companion object {
        fun fromEntity(entity: ConversationEntity): Conversation = Conversation(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            model = entity.model,
            systemPrompt = entity.systemPrompt
        )
    }
}
