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
        val activities: List<ToolActivity>,
        val promptTokens: Int? = null,
        val candidatesTokens: Int? = null,
        val totalTokens: Int? = null,
        val rawJson: String? = null
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
                    resultData = result.data,
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
                                thoughtSignature = confirmedToolActivity.thoughtSignature?.takeIf { 
                                    it.isNotBlank() && it != "valid_thought_signature" 
                                }
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
                client.executeGenerateContent(
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
                val (errType, friendlyMsg) = client.parseHttpError(response.code(), err)
                emit(AgentStepEvent.Error(errType, friendlyMsg))
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
                    val args = call.args ?: emptyMap()
                    tool != null && (
                        tool.requiresConfirmation(args) || 
                        (requireWriteConfirmation && tool.permission == ToolPermission.WRITE)
                    )
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
                        resultData = result.data,
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

                val usage = response.body()?.usageMetadata
                val promptTokens = usage?.promptTokenCount
                val candidateTokens = usage?.candidatesTokenCount
                val totalTokens = usage?.totalTokenCount ?: ((promptTokens ?: 0) + (candidateTokens ?: 0)).takeIf { it > 0 }

                val rawJson = try {
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .add(com.example.ai.model.PartJsonAdapterFactory())
                        .build()
                    val adapter = moshi.adapter(com.example.ai.model.GenerateContentResponse::class.java).indent("  ")
                    response.body()?.let { adapter.toJson(it) }
                } catch (_: Exception) {
                    null
                }

                emit(
                    AgentStepEvent.Completed(
                        fullText = fullAnswer,
                        sources = collectedSources.distinctBy { it.url },
                        activities = activities,
                        promptTokens = promptTokens,
                        candidatesTokens = candidateTokens,
                        totalTokens = totalTokens,
                        rawJson = rawJson
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
            "web_search" -> "Searching web" to (args["query"]?.toString() ?: "Searching...")
            "fetch_url" -> "Reading webpage" to (args["url"]?.toString()?.take(50) ?: "Fetching content...")
            "github_list_repos" -> "GitHub Repositories" to "Listing accessible repositories"
            "github_repo_info" -> "GitHub Repository" to "${args["owner"]}/${args["repo"]}"
            "github_list_files" -> "GitHub File Tree" to "${args["owner"]}/${args["repo"]} / ${args["path"] ?: ""}"
            "github_read_file" -> "Reading GitHub File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_list_commits" -> "GitHub Commits" to "Recent commits for ${args["owner"]}/${args["repo"]}"
            "github_list_issues" -> "GitHub Issues" to "${args["owner"]}/${args["repo"]}"
            "github_list_branches" -> "GitHub Branches" to "${args["owner"]}/${args["repo"]}"
            "github_list_releases" -> "GitHub Releases" to "${args["owner"]}/${args["repo"]}"
            "github_create_issue" -> "Create Issue" to "${args["owner"]}/${args["repo"]} - ${args["title"]}"
            "github_create_repo" -> "Creating Repository" to (args["name"]?.toString() ?: "New repository")
            "github_delete_repo" -> "Deleting Repository" to "${args["owner"]}/${args["repo"]}"
            "github_create_file" -> "Create File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_update_file" -> "Update File" to "${args["path"]} in ${args["owner"]}/${args["repo"]}"
            "github_delete_file" -> "Delete File" to "${args["path"]} from ${args["owner"]}/${args["repo"]}"
            "github_create_branch" -> "Create Branch" to "${args["branch"]} in ${args["owner"]}/${args["repo"]}"
            "github_create_pull_request" -> "Create Pull Request" to "${args["title"]} (${args["head"]} → ${args["base"]})"
            "github_download_file" -> "Downloading File" to "${args["path"]} from ${args["owner"]}/${args["repo"]}"
            "github_upload_local_file" -> "Uploading File" to "${args["local_file_name"]} → ${args["owner"]}/${args["repo"]}/${args["path"]}"
            "read_attached_file" -> "Reading Attached File" to "${args["file_name"]}"
            "save_memory" -> "Saving User Memory" to (args["fact"]?.toString() ?: "Memory")
            "gmail_list_messages" -> "Gmail Messages" to (args["query"]?.toString()?.ifBlank { "Listing emails" } ?: "Listing emails")
            "gmail_read_message" -> "Reading Email" to "Message: ${args["message_id"]}"
            "gmail_send_message" -> "Sending Email" to "To: ${args["to"]} • ${args["subject"]}"
            "gmail_create_draft" -> "Drafting Email" to "To: ${args["to"]} • ${args["subject"]}"
            "gmail_delete_message" -> "Deleting Email" to "Message: ${args["message_id"]}"
            "gmail_modify_labels" -> "Updating Email Labels" to "Message: ${args["message_id"]}"
            "get_weather" -> "Weather Lookup" to (args["location"]?.toString() ?: "Current weather")
            "wikipedia_search" -> "Wikipedia Search" to (args["query"]?.toString() ?: "Wikipedia")
            "exchange_rate" -> "Currency Conversion" to "${args["base"]} to ${args["target"]}"
            "current_time" -> "Current Time" to (args["timezone"]?.toString() ?: "Local time")
            "terminal_execute" -> "Terminal Command" to "$ ${args["command"]}"
            "local_file_search" -> "Local File Search" to "${args["query"]}"
            "local_file_read" -> "Reading Local File" to "${args["target"]}"
            "local_file_create" -> "Creating Local File" to "${args["file_name"] ?: args["folder"]}"
            "local_file_write" -> "Writing Local File" to "${args["target"]}"
            "local_file_delete" -> "Deleting Local File" to "${args["target"]}"
            "local_file_copy" -> "Copying File" to "${args["source"]} → ${args["destination_folder"]}"
            "local_file_move" -> "Moving File" to "${args["source"]} → ${args["destination_folder"]}"
            "local_file_rename" -> "Renaming File" to "${args["new_name"]}"
            "local_folder_create" -> "Creating Folder" to "${args["folder_name"]}"
            "local_folder_list" -> "Listing Folder" to "${args["folder"] ?: "workspace"}"
            "local_file_open" -> "Opening File" to "${args["target"]}"
            "calendar_list_events" -> "Calendar Events" to "Upcoming events"
            "calendar_search_events" -> "Calendar Search" to "${args["query"]}"
            "calendar_create_event" -> "Creating Calendar Event" to "${args["title"]} at ${args["start_time"]}"
            "calendar_delete_event" -> "Deleting Calendar Event" to "Event #${args["event_id"]}"
            "calendar_find_free_time" -> "Finding Free Slots" to "Looking for available slots"
            "calendar_list_calendars" -> "Listing Calendars" to "Device calendars"
            "drive_search" -> "Google Drive Search" to "${args["query"]}"
            "drive_list_folder" -> "Drive Folder" to "${args["folder_id"] ?: "root"}"
            "drive_get_metadata" -> "Drive Metadata" to "${args["file_id"]}"
            "drive_read_text" -> "Reading Drive Doc" to "${args["file_id"]}"
            "drive_download" -> "Downloading from Drive" to "${args["file_id"]}"
            "drive_upload" -> "Uploading to Drive" to "${args["local_path"]}"
            "drive_create_folder" -> "Creating Drive Folder" to "${args["name"]}"
            "drive_rename" -> "Renaming Drive File" to "${args["new_name"]}"
            "drive_trash" -> "Trashing Drive File" to "${args["file_id"]}"
            "drive_share_link" -> "Drive Share Link" to "${args["file_id"]}"
            "github_download_to_local" -> "GitHub Download" to "${args["repo"]}:${args["path"]}"
            "github_upload_from_local" -> "GitHub Upload" to "${args["local_path"]} → ${args["repo"]}"
            "github_clone_to_local" -> "Cloning Repository" to "${args["repo_url"]}"
            "github_pull_to_local" -> "Git Pull" to "Pulling in ${args["folder"] ?: "workspace"}"
            "github_commit_local_changes" -> "Git Commit" to "${args["message"]}"
            "github_push_local_changes" -> "Git Push" to "Pushing to remote"
            "universal_file_transfer" -> "File Transfer" to "${args["source_type"]} → ${args["dest_type"]}"
            else -> "Action" to toolName
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
            "github_create_repo" -> ToolConfirmationPayload(
                actionTitle = "Create GitHub Repository",
                target = arguments["name"]?.toString() ?: "New repo",
                previewTitle = if (arguments["private"] == true) "Visibility: Private" else "Visibility: Public",
                previewContent = arguments["description"]?.toString()?.ifBlank { "Auto-init README: ${arguments["auto_init"] ?: true}" },
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "github_delete_repo" -> ToolConfirmationPayload(
                actionTitle = "Delete Repository (Irreversible)",
                target = "$owner/$repo",
                previewTitle = "Permanent Repository Deletion",
                previewContent = "This entire repository and all its files will be permanently deleted from GitHub.",
                isDestructive = true,
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
            "github_upload_local_file" -> ToolConfirmationPayload(
                actionTitle = "Upload Attached File",
                target = "$owner/$repo : $path",
                previewTitle = "Local file: ${arguments["local_file_name"]}",
                previewContent = "Target commit: ${message.ifBlank { "Upload ${arguments["local_file_name"]}" }}",
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "gmail_send_message" -> ToolConfirmationPayload(
                actionTitle = "Send Email",
                target = "Recipient: ${arguments["to"]}",
                previewTitle = "Subject: ${arguments["subject"]}",
                previewContent = arguments["body"]?.toString()?.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "gmail_create_draft" -> ToolConfirmationPayload(
                actionTitle = "Create Draft Email",
                target = "Recipient: ${arguments["to"]}",
                previewTitle = "Subject: ${arguments["subject"]}",
                previewContent = arguments["body"]?.toString()?.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "gmail_delete_message" -> ToolConfirmationPayload(
                actionTitle = if (arguments["permanent"] == true) "Permanently Delete Email" else "Move Email to Trash",
                target = "Message ID: ${arguments["message_id"]}",
                previewTitle = "Action: ${if (arguments["permanent"] == true) "Permanent Removal" else "Trash"}",
                previewContent = "This message will be removed from your mailbox.",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "gmail_modify_labels" -> ToolConfirmationPayload(
                actionTitle = "Update Email Labels",
                target = "Message ID: ${arguments["message_id"]}",
                previewTitle = "Modify Labels",
                previewContent = "Add: ${arguments["add_labels"] ?: "[]"} | Remove: ${arguments["remove_labels"] ?: "[]"}",
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "local_create_file" -> ToolConfirmationPayload(
                actionTitle = "Create Local Workspace File",
                target = arguments["path"]?.toString() ?: "File",
                previewTitle = "Path: ${arguments["path"]}",
                previewContent = arguments["content"]?.toString()?.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "local_edit_file" -> ToolConfirmationPayload(
                actionTitle = "Edit Local Workspace File",
                target = arguments["path"]?.toString() ?: "File",
                previewTitle = if (arguments["append"] == true) "Mode: Append" else "Mode: Overwrite",
                previewContent = arguments["content"]?.toString()?.take(300),
                isDestructive = false,
                toolName = toolName,
                arguments = arguments
            )
            "local_delete_file", "local_file_delete" -> ToolConfirmationPayload(
                actionTitle = "Delete Local File (Irreversible)",
                target = arguments["target"]?.toString() ?: arguments["path"]?.toString() ?: "File",
                previewTitle = "Delete Local File",
                previewContent = "Permanently deletes '${arguments["target"] ?: arguments["path"]}' from local storage.",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "local_file_write" -> ToolConfirmationPayload(
                actionTitle = "Write / Overwrite Local File",
                target = arguments["target"]?.toString() ?: "File",
                previewTitle = if (arguments["append"] == true) "Mode: Append" else "Mode: Overwrite",
                previewContent = arguments["content"]?.toString()?.take(300),
                isDestructive = arguments["append"] != true,
                toolName = toolName,
                arguments = arguments
            )
            "terminal_execute" -> ToolConfirmationPayload(
                actionTitle = "Execute Terminal Command",
                target = "$ ${arguments["command"]}",
                previewTitle = if (com.example.termux.TermuxSafety.isDestructive(arguments["command"]?.toString() ?: "")) "⚠️ Destructive Shell Command" else "Shell Execution",
                previewContent = "Directory: ${arguments["working_directory"] ?: "workspace"}\nCommand: ${arguments["command"]}",
                isDestructive = com.example.termux.TermuxSafety.isDestructive(arguments["command"]?.toString() ?: ""),
                toolName = toolName,
                arguments = arguments
            )
            "github_push_local_changes" -> ToolConfirmationPayload(
                actionTitle = "Push Changes to GitHub Remote",
                target = "${arguments["remote"] ?: "origin"}/${arguments["branch"] ?: "main"}",
                previewTitle = "Git Push",
                previewContent = "Pushes local commits to the remote GitHub repository.",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "drive_trash" -> ToolConfirmationPayload(
                actionTitle = "Move Google Drive File to Trash",
                target = "File ID: ${arguments["file_id"]}",
                previewTitle = "Trash Drive File",
                previewContent = "The file will be moved to Google Drive Trash.",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "calendar_delete_event" -> ToolConfirmationPayload(
                actionTitle = "Delete Google Calendar Event",
                target = "Event ID: ${arguments["event_id"]}",
                previewTitle = "Delete Event",
                previewContent = "This event will be removed from your Google Calendar.",
                isDestructive = true,
                toolName = toolName,
                arguments = arguments
            )
            "calendar_create_event" -> ToolConfirmationPayload(
                actionTitle = "Create Google Calendar Event",
                target = "${arguments["title"]} (${arguments["start_time"]})",
                previewTitle = "Calendar Event Confirmation",
                previewContent = "Attendees: ${arguments["attendees"] ?: "None"}\nLocation: ${arguments["location"] ?: "None"}",
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
