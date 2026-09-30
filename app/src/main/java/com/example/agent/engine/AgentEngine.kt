package com.example.agent.engine

import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolActivity
import com.example.agent.model.ToolConfirmationPayload
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.agent.model.ToolStatus
import com.example.ai.model.AIErrorType
import com.example.ai.model.Content
import com.example.ai.model.FunctionCall
import com.example.ai.model.FunctionResponse
import com.example.ai.model.GenerateContentRequest
import com.example.ai.model.GenerationConfig
import com.example.ai.model.Part
import com.example.ai.model.ToolDeclaration
import com.example.ai.model.normalizedForNextTurn
import com.example.ai.service.GeminiClient
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class AgentStepEvent {
    data class ToolAdded(val activity: ToolActivity) : AgentStepEvent()
    data class ToolUpdated(val activity: ToolActivity) : AgentStepEvent()
    data class ToolNeedsConfirmation(val activity: ToolActivity) : AgentStepEvent()
    data class TextChunk(val text: String) : AgentStepEvent()
    data class Completed(
        val fullText: String,
        val sources: List<SourceCitation>,
        val activities: List<ToolActivity>
    ) : AgentStepEvent()
    data class Error(val errorType: AIErrorType, val message: String) : AgentStepEvent()
}

class AgentEngine(
    private val client: GeminiClient,
    val toolRegistry: ToolRegistry
) {
    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
    }

    /**
     * Executes the conversational agent loop.
     * If Gemini returns a function call, executes the tool (or halts for confirmation if write/destructive),
     * passes the function response back to Gemini, and continues until a final answer is produced.
     */
    fun runAgent(
        conversationHistory: List<ChatMessage>,
        systemPrompt: String?,
        model: String,
        apiKey: String,
        activeTools: List<com.example.ai.model.FunctionDeclaration>,
        requireWriteConfirmation: Boolean = true,
        confirmedToolActivity: ToolActivity? = null
    ): Flow<AgentStepEvent> = flow {
        if (apiKey.isBlank()) {
            emit(AgentStepEvent.Error(AIErrorType.INVALID_API_KEY, "Gemini API key is missing. Set your key in Settings."))
            return@flow
        }

        // 1. Build initial turns from history
        val workingContents = conversationHistory.mapNotNull { msg ->
            if (msg.content.isBlank() && !msg.isStreaming) return@mapNotNull null
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> null
            }
            if (role != null) {
                Content(role = role, parts = listOf(Part(text = msg.content)))
            } else null
        }.toMutableList()

        val systemInstruction = if (!systemPrompt.isNullOrBlank()) {
            Content(parts = listOf(Part(text = systemPrompt)))
        } else null

        val toolsConfig = if (activeTools.isNotEmpty()) {
            listOf(ToolDeclaration(functionDeclarations = activeTools))
        } else null

        val collectedSources = mutableListOf<SourceCitation>()
        val activities = mutableListOf<ToolActivity>()

        // If resuming from a user confirmation action:
        if (confirmedToolActivity != null) {
            activities.add(confirmedToolActivity)
            val tool = toolRegistry.getTool(confirmedToolActivity.toolName)
            if (tool != null) {
                val runningActivity = confirmedToolActivity.copy(status = ToolStatus.RUNNING)
                emit(AgentStepEvent.ToolUpdated(runningActivity))

                val result = tool.execute(confirmedToolActivity.arguments)
                collectedSources.addAll(result.sources)

                val completedActivity = runningActivity.copy(
                    status = if (result.success) ToolStatus.SUCCESS else ToolStatus.FAILED,
                    resultSummary = result.summary,
                    details = result.data?.toString() ?: result.error?.message,
                    sources = result.sources
                )
                emit(AgentStepEvent.ToolUpdated(completedActivity))

                // Append functionCall turn and functionResponse turn to resume model conversation
                val callName = confirmedToolActivity.rawToolName ?: confirmedToolActivity.toolName
                workingContents.add(
                    Content(
                        role = "model",
                        parts = listOf(
                            Part(
                                functionCall = FunctionCall(
                                    name = callName,
                                    args = confirmedToolActivity.arguments
                                ),
                                thoughtSignature = confirmedToolActivity.thoughtSignature
                            )
                        )
                    )
                )
                workingContents.add(
                    Content(
                        role = "function",
                        parts = listOf(Part(functionResponse = FunctionResponse(callName, result.toGeminiResponse())))
                    )
                )
            }
        }

        var iteration = 0
        var finalAnswerProduced = false

        while (iteration < MAX_TOOL_ITERATIONS && !finalAnswerProduced) {
            iteration++

            val request = GenerateContentRequest(
                contents = workingContents,
                systemInstruction = systemInstruction,
                generationConfig = GenerationConfig(
                    temperature = 0.5f,
                    topP = 0.95f,
                    topK = 40,
                    maxOutputTokens = 8192
                ),
                tools = toolsConfig
            )

            val response = try {
                client.apiService.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(AgentStepEvent.Error(AIErrorType.UNKNOWN, e.localizedMessage ?: "Failed to connect to Gemini."))
                return@flow
            }

            if (!response.isSuccessful) {
                val err = response.errorBody()?.string() ?: ""
                emit(AgentStepEvent.Error(AIErrorType.UNKNOWN, "Gemini returned HTTP ${response.code()}: $err"))
                return@flow
            }

            val candidate = response.body()?.candidates?.firstOrNull()
            val candidateContent = candidate?.content

            if (candidateContent == null) {
                emit(AgentStepEvent.Error(AIErrorType.EMPTY_RESPONSE, "Gemini returned an empty candidate response."))
                return@flow
            }

            // Check if model returned any functionCalls
            val functionCallParts = candidateContent.parts.filter { it.functionCall != null }
            if (functionCallParts.isNotEmpty()) {
                // Preserve candidateContent with thought_signatures and all parts
                val normalizedCandidate = candidateContent.normalizedForNextTurn()
                workingContents.add(normalizedCandidate)

                // Check if any tool needs user confirmation
                val confirmationPart = functionCallParts.firstOrNull { part ->
                    val call = part.functionCall!!
                    val toolName = call.name.removePrefix("default_api:").substringAfterLast(":")
                    val tool = toolRegistry.getTool(toolName) ?: toolRegistry.getTool(call.name)
                    requireWriteConfirmation && tool?.requiresConfirmation == true
                }

                if (confirmationPart != null) {
                    val call = confirmationPart.functionCall!!
                    val rawToolName = call.name
                    val toolName = rawToolName.removePrefix("default_api:").substringAfterLast(":")
                    val tool = toolRegistry.getTool(toolName) ?: toolRegistry.getTool(rawToolName)
                    val args = call.args ?: emptyMap()
                    val (title, subtitle) = getPresentationDetails(toolName, args)
                    val activityId = UUID.randomUUID().toString()
                    val confirmationPayload = createConfirmationPayload(toolName, args, tool?.permission ?: ToolPermission.WRITE)
                    val confirmationActivity = ToolActivity(
                        id = activityId,
                        toolName = toolName,
                        title = title,
                        subtitle = subtitle,
                        status = ToolStatus.WAITING_CONFIRMATION,
                        arguments = args,
                        rawToolName = rawToolName,
                        thoughtSignature = confirmationPart.thoughtSignature,
                        confirmationPayload = confirmationPayload
                    )
                    activities.add(confirmationActivity)
                    emit(AgentStepEvent.ToolNeedsConfirmation(confirmationActivity))
                    return@flow
                }

                // Execute all function calls (parallel execution support)
                val responseParts = mutableListOf<Part>()

                for (fcPart in functionCallParts) {
                    val functionCall = fcPart.functionCall!!
                    val rawToolName = functionCall.name
                    val toolName = rawToolName.removePrefix("default_api:").substringAfterLast(":")
                    val arguments = functionCall.args ?: emptyMap()

                    val tool = toolRegistry.getTool(toolName) ?: toolRegistry.getTool(rawToolName)
                    if (tool == null) {
                        val notFoundResponse = mapOf("error" to "Tool '$rawToolName' is not available.")
                        responseParts.add(
                            Part(functionResponse = FunctionResponse(rawToolName, notFoundResponse))
                        )
                        continue
                    }

                    val (title, subtitle) = getPresentationDetails(toolName, arguments)
                    val activityId = UUID.randomUUID().toString()
                    val initialActivity = ToolActivity(
                        id = activityId,
                        toolName = toolName,
                        title = title,
                        subtitle = subtitle,
                        status = ToolStatus.RUNNING,
                        arguments = arguments,
                        rawToolName = rawToolName,
                        thoughtSignature = fcPart.thoughtSignature
                    )
                    activities.add(initialActivity)
                    emit(AgentStepEvent.ToolAdded(initialActivity))

                    val result = tool.execute(arguments)
                    collectedSources.addAll(result.sources)

                    val updatedActivity = initialActivity.copy(
                        status = if (result.success) ToolStatus.SUCCESS else ToolStatus.FAILED,
                        resultSummary = result.summary,
                        details = result.data?.toString() ?: result.error?.message,
                        sources = result.sources
                    )
                    val existingIdx = activities.indexOfFirst { it.id == activityId }
                    if (existingIdx >= 0) activities[existingIdx] = updatedActivity
                    emit(AgentStepEvent.ToolUpdated(updatedActivity))

                    responseParts.add(
                        Part(functionResponse = FunctionResponse(rawToolName, result.toGeminiResponse()))
                    )
                }

                // Pass function responses back to Gemini turns
                workingContents.add(Content(role = "function", parts = responseParts))
                // Continue loop so Gemini can reason over tool outputs!
            } else {
                // Final textual answer!
                val textParts = candidateContent.parts.mapNotNull { it.text }
                val fullAnswer = textParts.joinToString("\n")
                emit(AgentStepEvent.TextChunk(fullAnswer))
                emit(
                    AgentStepEvent.Completed(
                        fullText = fullAnswer,
                        sources = collectedSources.distinctBy { it.url },
                        activities = activities
                    )
                )
                finalAnswerProduced = true
            }
        }

        if (!finalAnswerProduced) {
            emit(AgentStepEvent.Error(AIErrorType.UNKNOWN, "Agent reached maximum tool iterations ($MAX_TOOL_ITERATIONS)."))
        }
    }.flowOn(Dispatchers.IO)

    private fun getPresentationDetails(toolName: String, args: Map<String, Any?>): Pair<String, String> {
        return when (toolName) {
            "web_search" -> "🔎 Searching the web" to (args["query"]?.toString() ?: "Searching...")
            "fetch_url" -> "🌐 Reading webpage" to (args["url"]?.toString()?.take(50) ?: "Fetching content...")
            "github_list_repos" -> "🐙 GitHub Repositories" to "Listing accessible repositories"
            "github_repo_info" -> "🐙 GitHub Repo Info" to "${args["owner"]}/${args["repo"]}"
            "github_list_files" -> "🐙 GitHub File Tree" to "${args["owner"]}/${args["repo"]} / ${args["path"] ?: ""}"
            "github_read_file" -> "🐙 GitHub Reading File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_list_commits" -> "🐙 GitHub Commits" to "Recent commits for ${args["owner"]}/${args["repo"]}"
            "github_list_issues" -> "🐙 GitHub Issues" to "${args["owner"]}/${args["repo"]}"
            "github_list_branches" -> "🐙 GitHub Branches" to "${args["owner"]}/${args["repo"]}"
            "github_list_releases" -> "🐙 GitHub Releases" to "${args["owner"]}/${args["repo"]}"
            "github_create_issue" -> "🐙 Create Issue" to "${args["owner"]}/${args["repo"]} - ${args["title"]}"
            "github_create_file" -> "✏️ Create File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_update_file" -> "✏️ Update File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_delete_file" -> "🗑️ Delete File" to "${args["path"]} from ${args["owner"]}/${args["repo"]}"
            "github_create_branch" -> "🌿 Create Branch" to "${args["branch"]} in ${args["owner"]}/${args["repo"]}"
            "github_create_pull_request" -> "🔀 Create Pull Request" to "${args["title"]} (${args["head"]} → ${args["base"]})"
            "get_weather" -> "🌤 Weather Lookup" to (args["location"]?.toString() ?: "Current weather")
            "wikipedia_search" -> "📖 Wikipedia Search" to (args["query"]?.toString() ?: "Wikipedia")
            "exchange_rate" -> "💱 Currency Conversion" to "${args["base"]} to ${args["target"]}"
            "current_time" -> "⏰ Current Time" to (args["timezone"]?.toString() ?: "Local time")
            else -> "⚡ Tool Call" to toolName
        }
    }

    private fun createConfirmationPayload(
        toolName: String,
        arguments: Map<String, Any?>,
        permission: ToolPermission
    ): ToolConfirmationPayload {
        val owner = arguments["owner"]?.toString() ?: ""
        val repo = arguments["repo"]?.toString() ?: ""
        val path = arguments["path"]?.toString() ?: ""
        val message = arguments["message"]?.toString() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val title = arguments["title"]?.toString() ?: ""
        val body = arguments["body"]?.toString() ?: ""
        val isDestructive = permission == ToolPermission.DESTRUCTIVE

        return when (toolName) {
            "github_update_file" -> ToolConfirmationPayload(
                actionTitle = "Proposed File Update",
                target = "$owner/$repo : $path",
                previewTitle = if (message.isNotBlank()) "Commit: $message" else null,
                previewContent = content.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "github_create_file" -> ToolConfirmationPayload(
                actionTitle = "Proposed New File",
                target = "$owner/$repo : $path",
                previewTitle = if (message.isNotBlank()) "Commit: $message" else null,
                previewContent = content.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "github_delete_file" -> ToolConfirmationPayload(
                actionTitle = "Delete File (Irreversible)",
                target = "$owner/$repo : $path",
                previewTitle = "Commit: $message",
                previewContent = "Target blob SHA: ${arguments["sha"]}",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "github_create_issue" -> ToolConfirmationPayload(
                actionTitle = "Create Issue",
                target = "$owner/$repo",
                previewTitle = "Title: $title",
                previewContent = body.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "github_create_pull_request" -> ToolConfirmationPayload(
                actionTitle = "Create Pull Request",
                target = "$owner/$repo (${arguments["head"]} → ${arguments["base"]})",
                previewTitle = "Title: $title",
                previewContent = body.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "github_create_branch" -> ToolConfirmationPayload(
                actionTitle = "Create New Branch",
                target = "$owner/$repo : ${arguments["branch"]}",
                previewTitle = "Base SHA: ${arguments["from_sha"]}",
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            else -> ToolConfirmationPayload(
                actionTitle = "Confirm Action",
                target = toolName,
                isDestructive = isDestructive,
                toolName = toolName,
                arguments = arguments
            )
        }
    }
}
