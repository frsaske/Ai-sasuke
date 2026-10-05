package com.example.ai.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi

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

@JsonClass(generateAdapter = false)
data class Part(
    val text: String? = null,
    val thought: Boolean? = null,
    val thoughtSignature: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

class PartJsonAdapter(private val moshi: Moshi) : JsonAdapter<Part>() {
    private val options: JsonReader.Options = JsonReader.Options.of(
        "text", "thought", "thought_signature", "thoughtSignature", "functionCall", "functionResponse"
    )

    override fun fromJson(reader: JsonReader): Part {
        var text: String? = null
        var thought: Boolean? = null
        var thoughtSignature: String? = null
        var functionCall: FunctionCall? = null
        var functionResponse: FunctionResponse? = null

        val functionCallAdapter = moshi.adapter(FunctionCall::class.java)
        val functionResponseAdapter = moshi.adapter(FunctionResponse::class.java)

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.selectName(options)) {
                0 -> text = reader.nextString()
                1 -> thought = reader.nextBoolean()
                2, 3 -> thoughtSignature = reader.nextString()
                4 -> functionCall = functionCallAdapter.fromJson(reader)
                5 -> functionResponse = functionResponseAdapter.fromJson(reader)
                -1 -> {
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()

        return Part(
            text = text,
            thought = thought,
            thoughtSignature = thoughtSignature,
            functionCall = functionCall,
            functionResponse = functionResponse
        )
    }

    override fun toJson(writer: JsonWriter, value: Part?) {
        if (value == null) {
            writer.nullValue()
            return
        }

        val functionCallAdapter = moshi.adapter(FunctionCall::class.java)
        val functionResponseAdapter = moshi.adapter(FunctionResponse::class.java)

        writer.beginObject()
        if (value.text != null) {
            writer.name("text").value(value.text)
        }
        if (value.thought != null) {
            writer.name("thought").value(value.thought)
        }
        val validSignature = value.thoughtSignature?.takeIf { 
            it.isNotBlank() && it != "valid_thought_signature"
        }
        if (validSignature != null) {
            writer.name("thought_signature").value(validSignature)
        }
        if (value.functionCall != null) {
            writer.name("functionCall")
            functionCallAdapter.toJson(writer, value.functionCall)
        }
        if (value.functionResponse != null) {
            writer.name("functionResponse")
            functionResponseAdapter.toJson(writer, value.functionResponse)
        }
        writer.endObject()
    }
}

class PartJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(type: java.lang.reflect.Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type == Part::class.java) {
            return PartJsonAdapter(moshi)
        }
        return null
    }
}

fun Content.normalizedForNextTurn(): Content {
    // Find the primary thought signature in this content turn if real
    val turnSignature = this.parts.firstNotNullOfOrNull {
        it.thoughtSignature?.takeIf { s -> s.isNotBlank() && s != "valid_thought_signature" }
    }

    return this.copy(
        parts = this.parts.map { part ->
            if (part.functionCall != null) {
                val sig = part.thoughtSignature?.takeIf { it.isNotBlank() && it != "valid_thought_signature" } ?: turnSignature
                part.copy(thoughtSignature = sig)
            } else if (part.thoughtSignature == "valid_thought_signature") {
                part.copy(thoughtSignature = null)
            } else {
                part
            }
        }
    )
}

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
