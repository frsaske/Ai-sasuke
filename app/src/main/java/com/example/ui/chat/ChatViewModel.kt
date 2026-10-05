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
    val memoryRepository = com.example.data.repository.MemoryRepository(database.memoryDao())
    val secureStorageManager = SecureStorageManager(application)
    val appSettingsManager = AppSettingsManager(application)
    val aiService: AiService = GeminiAiServiceImpl()

    val agentEngine = com.example.agent.engine.AgentEngine(
        client = com.example.ai.service.GeminiClient(),
        toolRegistry = com.example.agent.engine.ToolRegistry(
            getTavilyKey = { secureStorageManager.getTavilyApiKey() },
            getSearxUrl = { appSettingsManager.getSearxUrl() },
            getGitHubToken = { secureStorageManager.getGitHubToken() },
            getGmailToken = { secureStorageManager.getGmailToken() },
            getGmailUserEmail = { secureStorageManager.getGmailUserEmail() },
            context = application,
            memoryRepository = memoryRepository
        )
    )

    init {
        viewModelScope.launch {
            try {
                val existing = memoryRepository.getActiveMemories()
                if (existing.isEmpty()) {
                    memoryRepository.saveMemory("User's name is Sasuke", "Personal")
                    memoryRepository.saveMemory("Sasuke is 17 years old", "Personal")
                    memoryRepository.saveMemory("Sasuke is in 11th grade studying Math", "Education")
                }
            } catch (e: Exception) {
                // Ignore initialization failures gracefully
            }
        }
    }

    // Memories from DB
    val allMemories: StateFlow<List<com.example.data.model.MemoryEntity>> = memoryRepository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveMemory(fact: String, category: String = "Personal") {
        viewModelScope.launch {
            memoryRepository.saveMemory(fact, category)
        }
    }

    fun updateMemory(id: String, fact: String, category: String) {
        viewModelScope.launch {
            memoryRepository.updateMemory(id, fact, category)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
        }
    }

    fun toggleMemory(id: String, enabled: Boolean) {
        viewModelScope.launch {
            memoryRepository.setMemoryEnabled(id, enabled)
        }
    }

    suspend fun exportMemoriesAsText(): String {
        return memoryRepository.exportMemoriesAsText()
    }

    suspend fun exportMemoriesAsJson(): String {
        return memoryRepository.exportMemoriesAsJson()
    }

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
            try {
                chatRepository.allConversations.collectLatest { list ->
                    if (_activeConversationId.value == null && list.isNotEmpty()) {
                        selectConversation(list.first().id)
                    }
                }
            } catch (e: Exception) {
                // Ignore flow collection errors gracefully
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

    fun sendMessage(
        promptText: String = _composerText.value,
        attachedFile: com.example.util.AttachedFileInfo? = null
    ) {
        val trimmed = promptText.trim()
        if ((trimmed.isEmpty() && attachedFile == null) || _isGenerating.value) return

        _composerText.value = ""

        viewModelScope.launch {
            var convId = _activeConversationId.value
            val initialTitle = when {
                trimmed.isNotEmpty() -> if (trimmed.length > 36) trimmed.take(36) + "..." else trimmed
                attachedFile != null -> "File: ${attachedFile.name}"
                else -> "New Chat"
            }

            if (convId == null) {
                val newConv = chatRepository.createConversation(
                    title = initialTitle,
                    model = currentModel.value,
                    systemPrompt = currentSystemPrompt.value
                )
                convId = newConv.id
                selectConversation(convId)
            }

            // 1. Build prompt and context
            val displayMessage = buildString {
                if (trimmed.isNotEmpty()) append(trimmed)
                if (attachedFile != null) {
                    if (isNotEmpty()) append("\n\n")
                    append("[Attached File: ${attachedFile.name} (${attachedFile.formattedSize})]")
                }
            }

            if (attachedFile != null) {
                val fileUri = android.net.Uri.fromFile(java.io.File(attachedFile.localPath))
                com.example.storage.LocalStorageManager.setLastAttachedFile(
                    com.example.storage.AttachedFileInfo(
                        uri = fileUri,
                        name = attachedFile.name,
                        mimeType = attachedFile.mimeType,
                        size = attachedFile.sizeBytes
                    )
                )
            }

            val aiMessageContext = if (attachedFile != null) {
                buildString {
                    append(displayMessage)
                    append("\n\n[System Note for Assistant: The user attached device file '${attachedFile.name}' (${attachedFile.formattedSize}). Path: ${attachedFile.localPath}. If the user asks 'isko usi folder mein daal do', 'same folder mein README bana do', or asks to copy/move/push it, perform local file or GitHub tools directly. Do NOT read or load the file content unless the user explicitly asks to view/inspect it.]")
                }
            } else {
                trimmed
            }

            // Save User Message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER,
                content = displayMessage,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.saveMessage(userMsg)
            _scrollToBottomEvent.tryEmit(Unit)

            // Auto-detect and store user background/memory facts from prompt
            if (appSettingsManager.isMemoryEnabled() && trimmed.isNotBlank()) {
                val extracted = com.example.data.util.MemoryExtractor.extractFacts(trimmed)
                extracted.forEach { item ->
                    memoryRepository.saveMemoryIfNotExists(item.fact, item.category)
                }
            }

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
            executeAiGeneration(convId, assistantMsgId, promptOverride = if (attachedFile != null) aiMessageContext else null)
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

    fun confirmToolAction(messageId: String, activity: com.example.agent.model.ToolActivity) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            chatRepository.updateMessageStatus(
                id = messageId,
                content = "",
                isStreaming = true,
                isError = false,
                errorMessage = null
            )
            executeAiGeneration(convId, messageId, confirmedToolActivity = activity)
        }
    }

    fun cancelToolAction(messageId: String, activity: com.example.agent.model.ToolActivity) {
        viewModelScope.launch {
            val updatedActivity = activity.copy(
                status = com.example.agent.model.ToolStatus.FAILED,
                resultSummary = "Action was cancelled by user"
            )
            val currentMsgs = chatRepository.getMessagesList(_activeConversationId.value ?: return@launch)
            val target = currentMsgs.firstOrNull { it.id == messageId } ?: return@launch
            val newActivities = target.activities.map { if (it.id == activity.id) updatedActivity else it }
            chatRepository.updateMessageStatus(
                id = messageId,
                content = target.content.ifBlank { "Proposed change was cancelled." },
                isStreaming = false,
                isError = false,
                activities = newActivities,
                sources = target.sources
            )
        }
    }

    private fun executeAiGeneration(
        conversationId: String,
        assistantMessageId: String,
        confirmedToolActivity: com.example.agent.model.ToolActivity? = null,
        promptOverride: String? = null
    ) {
        generationJob?.cancel()
        _isGenerating.value = true

        generationJob = viewModelScope.launch {
            val apiKey = secureStorageManager.getApiKey()
            val model = currentModel.value
            val baseSystemPrompt = currentSystemPrompt.value
            val memoryBlock = if (appSettingsManager.isMemoryEnabled()) {
                memoryRepository.buildMemoryPromptBlock()
            } else ""
            val systemPrompt = if (memoryBlock.isNotBlank()) baseSystemPrompt + memoryBlock else baseSystemPrompt

            val rawHistory = chatRepository.getMessagesList(conversationId)
                .filter { it.id != assistantMessageId && !it.isError }

            val history = if (promptOverride != null && rawHistory.isNotEmpty()) {
                val lastIdx = rawHistory.indexOfLast { it.role == MessageRole.USER }
                if (lastIdx >= 0) {
                    rawHistory.toMutableList().apply {
                        this[lastIdx] = this[lastIdx].copy(content = promptOverride)
                    }
                } else rawHistory
            } else rawHistory

            val isWebEnabled = appSettingsManager.isWebSearchEnabled()
            val isGitHubEnabled = appSettingsManager.isGitHubEnabled()
            val isGmailEnabled = appSettingsManager.isGmailEnabled()
            val isLocalFilesEnabled = appSettingsManager.isLocalFilesEnabled()
            val isMemoryEnabled = appSettingsManager.isMemoryEnabled()
            val isTermuxEnabled = appSettingsManager.isTermuxEnabled()
            val isCalendarEnabled = appSettingsManager.isCalendarEnabled()
            val isDriveEnabled = appSettingsManager.isDriveEnabled()
            val isWeatherEnabled = appSettingsManager.isWeatherEnabled()
            val isWikipediaEnabled = appSettingsManager.isWikipediaEnabled()
            val isCurrencyEnabled = appSettingsManager.isCurrencyEnabled()
            val isTimeEnabled = appSettingsManager.isTimeEnabled()
            val showAgentActivity = appSettingsManager.isShowAgentActivityEnabled()
            val requireConfirmation = appSettingsManager.isRequireConfirmationEnabled()

            val activeDeclarations = agentEngine.toolRegistry.getActiveDeclarations(
                isWebEnabled = isWebEnabled,
                isGitHubEnabled = isGitHubEnabled,
                isGmailEnabled = isGmailEnabled,
                isLocalFilesEnabled = isLocalFilesEnabled,
                isMemoryEnabled = isMemoryEnabled,
                isTermuxEnabled = isTermuxEnabled,
                isCalendarEnabled = isCalendarEnabled,
                isDriveEnabled = isDriveEnabled,
                isWeatherEnabled = isWeatherEnabled,
                isWikipediaEnabled = isWikipediaEnabled,
                isCurrencyEnabled = isCurrencyEnabled,
                isTimeEnabled = isTimeEnabled
            )

            val currentActivities = mutableListOf<com.example.agent.model.ToolActivity>()
            val currentSources = mutableListOf<com.example.agent.model.SourceCitation>()
            val accumulatedText = StringBuilder()

            try {
                agentEngine.runAgent(
                    conversationHistory = history,
                    systemPrompt = systemPrompt,
                    model = model,
                    apiKey = apiKey,
                    activeTools = activeDeclarations,
                    requireWriteConfirmation = requireConfirmation,
                    confirmedToolActivity = confirmedToolActivity
                ).collect { event ->
                    when (event) {
                        is com.example.agent.engine.AgentStepEvent.ToolAdded -> {
                            if (showAgentActivity) {
                                currentActivities.add(event.activity)
                                chatRepository.updateMessageStatus(
                                    id = assistantMessageId,
                                    content = accumulatedText.toString(),
                                    isStreaming = true,
                                    isError = false,
                                    activities = currentActivities.toList(),
                                    sources = currentSources.toList()
                                )
                                _scrollToBottomEvent.tryEmit(Unit)
                            }
                        }
                        is com.example.agent.engine.AgentStepEvent.ToolUpdated -> {
                            if (showAgentActivity) {
                                val idx = currentActivities.indexOfFirst { it.id == event.activity.id }
                                if (idx >= 0) {
                                    currentActivities[idx] = event.activity
                                } else {
                                    currentActivities.add(event.activity)
                                }
                                currentSources.addAll(event.activity.sources)
                                chatRepository.updateMessageStatus(
                                    id = assistantMessageId,
                                    content = accumulatedText.toString(),
                                    isStreaming = true,
                                    isError = false,
                                    activities = currentActivities.toList(),
                                    sources = currentSources.distinctBy { it.url }
                                )
                                _scrollToBottomEvent.tryEmit(Unit)
                            }
                        }
                        is com.example.agent.engine.AgentStepEvent.ToolNeedsConfirmation -> {
                            currentActivities.add(event.activity)
                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = accumulatedText.toString(),
                                isStreaming = false,
                                isError = false,
                                activities = currentActivities.toList(),
                                sources = currentSources.distinctBy { it.url }
                            )
                            _isGenerating.value = false
                            _scrollToBottomEvent.tryEmit(Unit)
                        }
                        is com.example.agent.engine.AgentStepEvent.TextChunk -> {
                            accumulatedText.append(event.text)
                            val currentText = accumulatedText.toString()
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        content = currentText,
                                        isStreaming = true,
                                        isError = false,
                                        activities = currentActivities.toList(),
                                        sources = currentSources.distinctBy { it.url }
                                    )
                                } else msg
                            }
                            _scrollToBottomEvent.tryEmit(Unit)
                        }
                        is com.example.agent.engine.AgentStepEvent.Completed -> {
                            val finalSources = (currentSources + event.sources).distinctBy { it.url }
                            val finalActivities = if (showAgentActivity) (currentActivities + event.activities).distinctBy { it.id } else emptyList()
                            val finalText = event.fullText.ifBlank { accumulatedText.toString() }

                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = finalText,
                                isStreaming = false,
                                isError = false,
                                activities = finalActivities,
                                sources = finalSources,
                                promptTokens = event.promptTokens,
                                candidatesTokens = event.candidatesTokens,
                                totalTokens = event.totalTokens,
                                rawResponseJson = event.rawJson
                            )
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        content = finalText,
                                        isStreaming = false,
                                        isError = false,
                                        activities = finalActivities,
                                        sources = finalSources,
                                        promptTokens = event.promptTokens,
                                        candidatesTokens = event.candidatesTokens,
                                        totalTokens = event.totalTokens,
                                        rawResponseJson = event.rawJson
                                    )
                                } else msg
                            }
                            _isGenerating.value = false
                            _scrollToBottomEvent.tryEmit(Unit)
                        }
                        is com.example.agent.engine.AgentStepEvent.Error -> {
                            chatRepository.updateMessageStatus(
                                id = assistantMessageId,
                                content = accumulatedText.toString(),
                                isStreaming = false,
                                isError = true,
                                errorMessage = event.message,
                                activities = currentActivities.toList(),
                                sources = currentSources.toList()
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
                    errorMessage = e.localizedMessage ?: "Agent execution was interrupted.",
                    activities = currentActivities.toList(),
                    sources = currentSources.toList()
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

    suspend fun testSearxSearch(query: String = "Android news"): Result<String> {
        val tool = com.example.agent.tools.web.SearxWebSearchTool { appSettingsManager.getSearxUrl() }
        val res = tool.execute(mapOf("query" to query))
        return if (res.success) {
            Result.success(res.summary)
        } else {
            Result.failure(Exception(res.error?.message ?: "SearXNG search failed"))
        }
    }

    suspend fun testTavilySearch(query: String = "Android technology news"): Result<String> {
        val tool = com.example.agent.tools.web.TavilyWebSearchTool { secureStorageManager.getTavilyApiKey() }
        val res = tool.execute(mapOf("query" to query))
        return if (res.success) {
            Result.success(res.summary)
        } else {
            Result.failure(Exception(res.error?.message ?: "Tavily search failed"))
        }
    }

    suspend fun testGitHubConnection(): Result<String> {
        return agentEngine.toolRegistry.gitHubService.testConnection()
    }

    suspend fun testGmailConnection(): Result<String> {
        val res = agentEngine.toolRegistry.gmailService.getProfile()
        return res.fold(
            onSuccess = { profile ->
                val email = profile["emailAddress"] ?: "me"
                val total = profile["messagesTotal"] ?: 0
                Result.success("Connected to Gmail as $email ($total total messages)")
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun saveGmailToken(token: String) {
        secureStorageManager.saveGmailToken(token)
    }

    fun clearGmailToken() {
        secureStorageManager.clearGmailToken()
    }

    fun saveGmailUserEmail(email: String) {
        secureStorageManager.saveGmailUserEmail(email)
    }

    fun saveGitHubToken(token: String) {
        secureStorageManager.saveGitHubToken(token)
    }

    fun clearGitHubToken() {
        secureStorageManager.clearGitHubToken()
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

    // --- Manual Local & GitHub File Operations ---
    suspend fun createLocalFile(path: String, content: String, overwrite: Boolean = false): Result<com.example.util.LocalFileItem> {
        return com.example.util.LocalWorkspaceManager.createFile(getApplication(), path, content, overwrite)
    }

    suspend fun readLocalFile(path: String): Result<String> {
        return com.example.util.LocalWorkspaceManager.readFile(getApplication(), path)
    }

    suspend fun editLocalFile(path: String, content: String, append: Boolean = false): Result<com.example.util.LocalFileItem> {
        return com.example.util.LocalWorkspaceManager.editFile(getApplication(), path, content, append)
    }

    suspend fun deleteLocalFile(path: String): Result<Boolean> {
        return com.example.util.LocalWorkspaceManager.deleteFile(getApplication(), path)
    }

    suspend fun listLocalFiles(subDir: String = ""): Result<List<com.example.util.LocalFileItem>> {
        return com.example.util.LocalWorkspaceManager.listFiles(getApplication(), subDir)
    }

    suspend fun clearLocalWorkspace(): Boolean {
        return com.example.util.LocalWorkspaceManager.clearWorkspace(getApplication())
    }

    suspend fun createGitHubFile(owner: String, repo: String, path: String, content: String, message: String, branch: String? = null): Result<Map<String, Any?>> {
        return agentEngine.toolRegistry.gitHubService.createOrUpdateFile(owner, repo, path, content, message, sha = null, branch = branch)
    }

    suspend fun updateGitHubFile(owner: String, repo: String, path: String, content: String, message: String, sha: String, branch: String? = null): Result<Map<String, Any?>> {
        return agentEngine.toolRegistry.gitHubService.createOrUpdateFile(owner, repo, path, content, message, sha = sha, branch = branch)
    }

    suspend fun deleteGitHubFile(owner: String, repo: String, path: String, message: String, sha: String, branch: String? = null): Result<Map<String, Any?>> {
        return agentEngine.toolRegistry.gitHubService.deleteFile(owner, repo, path, message, sha = sha, branch = branch)
    }

    suspend fun listGitHubFiles(owner: String, repo: String, path: String = "", ref: String? = null): Result<List<Map<String, Any?>>> {
        return agentEngine.toolRegistry.gitHubService.listFiles(owner, repo, path, ref)
    }

    suspend fun readGitHubFile(owner: String, repo: String, path: String, ref: String? = null): Result<String> {
        return agentEngine.toolRegistry.gitHubService.readFile(owner, repo, path, ref).map { it["content"]?.toString() ?: "" }
    }
}
