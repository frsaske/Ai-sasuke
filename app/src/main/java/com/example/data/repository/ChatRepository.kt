package com.example.data.repository

import com.example.data.local.ConversationDao
import com.example.data.local.MessageDao
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    val allConversations: Flow<List<Conversation>> = conversationDao.getAllConversations()
        .map { list -> list.map { Conversation.fromEntity(it) } }
        .flowOn(Dispatchers.IO)

    fun searchConversations(query: String): Flow<List<Conversation>> =
        conversationDao.searchConversations(query)
            .map { list -> list.map { Conversation.fromEntity(it) } }
            .flowOn(Dispatchers.IO)

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.getMessagesForConversation(conversationId)
            .map { list -> list.map { ChatMessage.fromEntity(it) } }
            .flowOn(Dispatchers.IO)

    suspend fun getMessagesList(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao.getMessagesList(conversationId).map { ChatMessage.fromEntity(it) }
    }

    suspend fun getConversationById(id: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(id)?.let { Conversation.fromEntity(it) }
    }

    suspend fun createConversation(
        title: String = "New Chat",
        model: String,
        systemPrompt: String = ""
    ): Conversation = withContext(Dispatchers.IO) {
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            model = model,
            systemPrompt = systemPrompt
        )
        conversationDao.insertConversation(conv.toEntity())
        conv
    }

    suspend fun updateConversationTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.updateTitle(id, newTitle.trim(), System.currentTimeMillis())
    }

    suspend fun touchConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.updateTimestamp(id, System.currentTimeMillis())
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteConversation(id)
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        conversationDao.deleteAllConversations()
        messageDao.deleteAllMessages()
    }

    suspend fun saveMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message.toEntity())
        touchConversation(message.conversationId)
    }

    suspend fun updateMessageStatus(
        id: String,
        content: String,
        isStreaming: Boolean,
        isError: Boolean,
        errorMessage: String? = null,
        activities: List<com.example.agent.model.ToolActivity> = emptyList(),
        sources: List<com.example.agent.model.SourceCitation> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val dummyMessage = ChatMessage(
            id = id,
            conversationId = "",
            role = com.example.data.model.MessageRole.ASSISTANT,
            content = content,
            isStreaming = isStreaming,
            isError = isError,
            errorMessage = errorMessage,
            activities = activities,
            sources = sources
        )
        val entity = dummyMessage.toEntity()
        messageDao.updateMessageStatus(
            id = id,
            content = content,
            isStreaming = isStreaming,
            isError = isError,
            errorMessage = errorMessage,
            activitiesJson = entity.activitiesJson,
            sourcesJson = entity.sourcesJson
        )
    }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessage(id)
    }

    suspend fun deleteMessagesForConversation(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
    }
}
