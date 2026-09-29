package com.example.ai.service

import com.example.ai.model.Content
import com.example.ai.model.FunctionDeclaration
import com.example.ai.model.GenerateContentRequest
import com.example.ai.model.GenerationConfig
import com.example.ai.model.Part
import com.example.ai.model.StreamEvent
import com.example.ai.model.ToolDeclaration
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import kotlinx.coroutines.flow.Flow

/**
 * Pluggable Agent Tool interface designed for future capabilities:
 * GitHub, Google Drive, Gmail, Calendar, Terminal, etc.
 */
interface AgentTool {
    val name: String
    val description: String
    val parametersSchema: Map<String, Any?>

    suspend fun execute(arguments: Map<String, Any?>): Map<String, Any?>

    fun toDeclaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = parametersSchema
    )
}

/**
 * Registry to host future agent tools without modifying the chat UI.
 */
class AgentToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): AgentTool? = tools[name]

    fun getAllDeclarations(): List<FunctionDeclaration> = tools.values.map { it.toDeclaration() }

    fun isEmpty(): Boolean = tools.isEmpty()
}

/**
 * High-level AI service interface for chat generation.
 */
interface AiService {
    fun streamChatResponse(
        conversationHistory: List<ChatMessage>,
        systemPrompt: String?,
        model: String,
        apiKey: String
    ): Flow<StreamEvent>

    suspend fun testConnection(apiKey: String, model: String): Result<String>
}

class GeminiAiServiceImpl(
    private val client: GeminiClient = GeminiClient(),
    val toolRegistry: AgentToolRegistry = AgentToolRegistry()
) : AiService {

    override fun streamChatResponse(
        conversationHistory: List<ChatMessage>,
        systemPrompt: String?,
        model: String,
        apiKey: String
    ): Flow<StreamEvent> {
        // Map conversation messages to Gemini Content objects
        val geminiContents = conversationHistory.mapNotNull { msg ->
            if (msg.content.isBlank() && !msg.isStreaming) return@mapNotNull null
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> null
            }
            if (role != null) {
                Content(
                    role = role,
                    parts = listOf(Part(text = msg.content))
                )
            } else null
        }

        // Configure system instruction
        val systemInstruction = if (!systemPrompt.isNullOrBlank()) {
            Content(parts = listOf(Part(text = systemPrompt)))
        } else null

        // Tools if any registered (ready for GitHub, Gmail, Drive, Terminal)
        val toolsList = if (!toolRegistry.isEmpty()) {
            listOf(ToolDeclaration(functionDeclarations = toolRegistry.getAllDeclarations()))
        } else null

        val request = GenerateContentRequest(
            contents = geminiContents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
                maxOutputTokens = 8192
            ),
            tools = toolsList
        )

        return client.streamContent(
            model = model,
            apiKey = apiKey,
            request = request
        )
    }

    override suspend fun testConnection(apiKey: String, model: String): Result<String> {
        return client.testConnection(apiKey, model)
    }
}
