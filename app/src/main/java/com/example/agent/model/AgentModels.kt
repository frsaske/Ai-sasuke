package com.example.agent.model

import com.example.ai.model.FunctionDeclaration
import java.util.UUID

enum class ToolPermission {
    READ_ONLY,
    WRITE,
    DESTRUCTIVE
}

enum class ToolStatus {
    PREPARING,
    RUNNING,
    SUCCESS,
    FAILED,
    WAITING_CONFIRMATION
}

data class SourceCitation(
    val title: String,
    val url: String
)

data class ToolError(
    val type: String,
    val message: String
)

data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?>? = null,
    val error: ToolError? = null,
    val sources: List<SourceCitation> = emptyList(),
    val summary: String = ""
) {
    fun toGeminiResponse(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>("success" to success)
        if (data != null) result.putAll(data)
        if (error != null) {
            result["error"] = mapOf("type" to error.type, "message" to error.message)
        }
        if (summary.isNotEmpty()) {
            result["summary"] = summary
        }
        return result
    }

    companion object {
        fun success(data: Map<String, Any?>, summary: String = "", sources: List<SourceCitation> = emptyList()): ToolResult =
            ToolResult(success = true, data = data, summary = summary, sources = sources)

        fun failure(type: String, message: String): ToolResult =
            ToolResult(success = false, error = ToolError(type, message), summary = "Failed: $message")
    }
}

data class ToolConfirmationPayload(
    val actionTitle: String,
    val target: String,
    val previewTitle: String? = null,
    val previewContent: String? = null,
    val isDestructive: Boolean = false,
    val toolName: String,
    val arguments: Map<String, Any?>
)

data class ToolActivity(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val title: String,
    val subtitle: String,
    val status: ToolStatus = ToolStatus.RUNNING,
    val resultSummary: String? = null,
    val details: String? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val rawToolName: String? = null,
    val thoughtSignature: String? = null,
    val confirmationPayload: ToolConfirmationPayload? = null,
    val resultData: Map<String, Any?>? = null,
    val sources: List<SourceCitation> = emptyList()
)

interface Tool {
    val name: String
    val description: String
    val parametersSchema: Map<String, Any?>
    val permission: ToolPermission

    val requiresConfirmation: Boolean
        get() = permission == ToolPermission.DESTRUCTIVE

    fun requiresConfirmation(arguments: Map<String, Any?>): Boolean = requiresConfirmation

    suspend fun execute(arguments: Map<String, Any?>): ToolResult

    fun toDeclaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = parametersSchema
    )
}
