package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.model.StreamEvent
import com.example.ai.service.AiService
import com.example.ai.service.GeminiAiServiceImpl
import com.example.data.local.AppSettingsManager
import com.example.data.local.SasukeXDatabase
import com.example.data.local.SecureStorageManager
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.MessageRole
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SasukeXDatabase.getInstance(application)
    val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())
    val secureStorageManager = SecureStorageManager(application)
    val appSettingsManager = AppSettingsManager(application)
    val aiService: AiService = GeminiAiServiceImpl()

    // Conversations from DB
    val conversations: StateFlow<List<Conversation>> = chatRepository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active conversation
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    // Messages in active conversation
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Composer input
    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    // Generating / Streaming state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Model & System prompt flows
    val currentModel: StateFlow<String> = appSettingsManager.modelFlow
    val currentSystemPrompt: StateFlow<String> = appSettingsManager.systemPromptFlow

    // Scroll trigger
    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent: SharedFlow<Unit> = _scrollToBottomEvent.asSharedFlow()

    private var messageObserverJob: Job? = null
    private var generationJob: Job? = null

    init {
        // Automatically attach to most recent conversation if available
        viewModelScope.launch {
            chatRepository.allConversations.collectLatest { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    selectConversation(list.first().id)
                }
            }
        }
    }

    fun onComposerTextChanged(newText: String) {
        _composerText.value = newText
    }

    fun selectConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) return
        _activeConversationId.value = conversationId

        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            chatRepository.getMessagesForConversation(conversationId).collectLatest { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun startNewChat() {
        stopGeneration()
        _activeConversationId.value = null
        _messages.value = emptyList()
        _composerText.value = ""
    }

    fun sendMessage(promptText: String = _composerText.value) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        _composerText.value = ""

        viewModelScope.launch {
            var convId = _activeConversationId.value
            if (convId == null) {
                val title = if (trimmed.length > 36) trimmed.take(36) + "..." else trimmed
                val newConv = chatRepository.createConversation(
                    title = title,
                    model = currentModel.value,
                    systemPrompt = currentSystemPrompt.value
                )
                convId = newConv.id
                selectConversation(convId)
            }

            // 1. Save User Message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER,
                content = trimmed,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.saveMessage(userMsg)
            _scrollToBottomEvent.tryEmit(Unit)

            // 2. Prepare Assistant Message
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = ChatMessage(
                id = assistantMsgId,
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis(),
                isStreaming = true
            )
            chatRepository.saveMessage(initialAssistantMsg)
            _scrollToBottomEvent.tryEmit(Unit)

            // 3. Trigger streaming AI call
            executeAiGeneration(convId, assistantMsgId)
        }
    }

    fun regenerateResponse(assistantMessageId: String? = null) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            val currentList = chatRepository.getMessagesList(convId)
            if (currentList.isEmpty()) return@launch

            val targetAssistant = if (assistantMessageId != null) {
                currentList.firstOrNull { it.id == assistantMessageId }
            } else {
                currentList.lastOrNull { it.role == MessageRole.ASSISTANT }
            }

            if (targetAssistant != null) {
                // Reset assistant message to streaming empty
                chatRepository.updateMessageStatus(
                    id = targetAssistant.id,
                    content = "",
                    isStreaming = true,
                    isError = false,
                    errorMessage = null
                )
                executeAiGeneration(convId, targetAssistant.id)
            }
        }
    }

    fun retryMessage(messageId: String) {
        regenerateResponse(messageId)
    }

    private fun executeAiGeneration(conversationId: String, assistantMessageId: String) {
        generationJob?.cancel()
        _isGenerating.value = true

        generationJob = viewModelScope.launch {
            val apiKey = secureStorageManager.getApiKey()
            val model = currentModel.value
            val systemPrompt = currentSystemPrompt.value

            val history = chatRepository.getMessagesList(conversationId)
                .filter { it.id != assistantMessageId && !it.isError }

            val accumulatedText = StringBuilder()

            try {
                aiService.streamChatResponse(
                    conversationHistory = history,
                    systemPrompt = systemPrompt,
                    model = model,
                    apiKey = apiKey
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            accumulatedText.append(event.text)
                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = accumulatedText.toString(),
                                isStreaming = true,
                                isError = false
                            )
                            _scrollToBottomEvent.tryEmit(Unit)
                        }
                        is StreamEvent.Completed -> {
                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = event.fullText.ifBlank { accumulatedText.toString() },
                                isStreaming = false,
                                isError = false
                            )
                            _isGenerating.value = false
                            _scrollToBottomEvent.tryEmit(Unit)
                        }
                        is StreamEvent.Error -> {
                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = accumulatedText.toString(),
                                isStreaming = false,
                                isError = true,
                                errorMessage = event.userMessage
                            )
                            _isGenerating.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                chatRepository.updateMessageStatus(
                    id = assistantMessageId,
                    content = accumulatedText.toString(),
                    isStreaming = false,
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Generation interrupted."
                )
                _isGenerating.value = false
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        if (_isGenerating.value) {
            generationJob?.cancel()
            _isGenerating.value = false

            // Mark any streaming message in active conversation as finalized
            val convId = _activeConversationId.value
            if (convId != null) {
                viewModelScope.launch {
                    val msgs = chatRepository.getMessagesList(convId)
                    val streamingMsg = msgs.firstOrNull { it.isStreaming }
                    if (streamingMsg != null) {
                        chatRepository.updateMessageStatus(
                            id = streamingMsg.id,
                            content = streamingMsg.content,
                            isStreaming = false,
                            isError = false
                        )
                    }
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.updateConversationTitle(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                startNewChat()
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            chatRepository.deleteAllConversations()
            startNewChat()
        }
    }

    fun saveApiKey(key: String) {
        secureStorageManager.saveApiKey(key)
    }

    fun clearApiKey() {
        secureStorageManager.clearApiKey()
    }

    suspend fun testConnection(key: String, model: String): Result<String> {
        val targetKey = key.ifBlank { secureStorageManager.getApiKey() }
        return aiService.testConnection(targetKey, model)
    }

    fun selectModel(model: String) {
        appSettingsManager.setSelectedModel(model)
    }

    fun saveSystemPrompt(prompt: String) {
        appSettingsManager.setSystemPrompt(prompt)
    }

    fun resetSystemPrompt() {
        appSettingsManager.resetSystemPrompt()
    }

    fun resetAllSettings() {
        appSettingsManager.resetAllSettings()
    }
}
