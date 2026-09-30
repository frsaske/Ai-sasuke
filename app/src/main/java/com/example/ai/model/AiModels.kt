package com.example.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
    val tools: List<ToolDeclaration>? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val thought: Boolean? = null,
    @Json(name = "thought_signature") val thoughtSignature: String? = null,
    @Json(name = "thoughtSignature") val thoughtSignatureCamel: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

@JsonClass(generateAdapter = true)
data class FunctionCall(
    val name: String,
    val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class FunctionResponse(
    val name: String,
    val response: Map<String, Any?>
)

fun Content.normalizedForNextTurn(): Content {
    return this.copy(
        parts = this.parts.map { part ->
            val sig = part.thoughtSignature ?: part.thoughtSignatureCamel
            part.copy(
                thoughtSignature = sig,
                thoughtSignatureCamel = null
            )
        }
    )
}

@JsonClass(generateAdapter = true)
data class ToolDeclaration(
    val functionDeclarations: List<FunctionDeclaration>? = null
)

@JsonClass(generateAdapter = true)
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = 0.7f,
    val topP: Float? = 0.95f,
    val topK: Int? = 40,
    val maxOutputTokens: Int? = 8192
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val usageMetadata: UsageMetadata? = null,
    val error: ApiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val index: Int? = null
)

@JsonClass(generateAdapter = true)
data class UsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

enum class AIErrorType {
    INVALID_API_KEY,
    NO_INTERNET,
    RATE_LIMIT,
    QUOTA_EXCEEDED,
    SERVICE_UNAVAILABLE,
    TIMEOUT,
    EMPTY_RESPONSE,
    UNKNOWN
}

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Completed(val fullText: String) : StreamEvent()
    data class Error(val type: AIErrorType, val userMessage: String) : StreamEvent()
}
