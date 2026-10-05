package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.agent.model.SourceCitation
import com.example.agent.model.ToolActivity
import com.example.agent.model.ToolConfirmationPayload
import com.example.agent.model.ToolStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "") val activitiesJson: String? = null,
    @ColumnInfo(defaultValue = "") val sourcesJson: String? = null,
    @ColumnInfo(defaultValue = "0") val promptTokens: Int? = null,
    @ColumnInfo(defaultValue = "0") val candidatesTokens: Int? = null,
    @ColumnInfo(defaultValue = "0") val totalTokens: Int? = null,
    @ColumnInfo(defaultValue = "") val rawResponseJson: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val activities: List<ToolActivity> = emptyList(),
    val sources: List<SourceCitation> = emptyList(),
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val rawResponseJson: String? = null
) {
    fun toEntity(): MessageEntity {
        val activitiesStr = if (activities.isNotEmpty()) {
            val array = JSONArray()
            for (act in activities) {
                array.put(
                    JSONObject().apply {
                        put("id", act.id)
                        put("toolName", act.toolName)
                        put("title", act.title)
                        put("subtitle", act.subtitle)
                        put("status", act.status.name)
                        put("resultSummary", act.resultSummary ?: "")
                        put("details", act.details ?: "")
                        if (act.confirmationPayload != null) {
                            val cp = act.confirmationPayload
                            put(
                                "confirmation",
                                JSONObject().apply {
                                    put("actionTitle", cp.actionTitle)
                                    put("target", cp.target)
                                    put("previewTitle", cp.previewTitle ?: "")
                                    put("previewContent", cp.previewContent ?: "")
                                    put("isDestructive", cp.isDestructive)
                                    put("toolName", cp.toolName)
                                    put("arguments", JSONObject(cp.arguments))
                                }
                            )
                        }
                    }
                )
            }
            array.toString()
        } else null

        val sourcesStr = if (sources.isNotEmpty()) {
            val array = JSONArray()
            for (s in sources) {
                array.put(
                    JSONObject().apply {
                        put("title", s.title)
                        put("url", s.url)
                    }
                )
            }
            array.toString()
        } else null

        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = timestamp,
            isStreaming = isStreaming,
            isError = isError,
            errorMessage = errorMessage,
            activitiesJson = activitiesStr,
            sourcesJson = sourcesStr,
            promptTokens = promptTokens,
            candidatesTokens = candidatesTokens,
            totalTokens = totalTokens,
            rawResponseJson = rawResponseJson
        )
    }

    companion object {
        fun fromEntity(entity: MessageEntity): ChatMessage {
            val parsedActivities = mutableListOf<ToolActivity>()
            if (!entity.activitiesJson.isNullOrBlank()) {
                try {
                    val array = JSONArray(entity.activitiesJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val confObj = obj.optJSONObject("confirmation")
                        val confirmationPayload = if (confObj != null) {
                            val argsMap = mutableMapOf<String, Any?>()
                            val argsJson = confObj.optJSONObject("arguments")
                            argsJson?.keys()?.forEach { k -> argsMap[k] = argsJson.get(k) }
                            ToolConfirmationPayload(
                                actionTitle = confObj.optString("actionTitle", "Action"),
                                target = confObj.optString("target", ""),
                                previewTitle = confObj.optString("previewTitle").ifBlank { null },
                                previewContent = confObj.optString("previewContent").ifBlank { null },
                                isDestructive = confObj.optBoolean("isDestructive", false),
                                toolName = confObj.optString("toolName", ""),
                                arguments = argsMap
                            )
                        } else null

                        parsedActivities.add(
                            ToolActivity(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                toolName = obj.optString("toolName", "tool"),
                                title = obj.optString("title", "Tool"),
                                subtitle = obj.optString("subtitle", ""),
                                status = try {
                                    ToolStatus.valueOf(obj.optString("status", "SUCCESS"))
                                } catch (_: Exception) {
                                    ToolStatus.SUCCESS
                                },
                                resultSummary = obj.optString("resultSummary").ifBlank { null },
                                details = obj.optString("details").ifBlank { null },
                                confirmationPayload = confirmationPayload
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            val parsedSources = mutableListOf<SourceCitation>()
            if (!entity.sourcesJson.isNullOrBlank()) {
                try {
                    val array = JSONArray(entity.sourcesJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        parsedSources.add(
                            SourceCitation(
                                title = obj.optString("title", "Source"),
                                url = obj.optString("url", "")
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            return ChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                role = entity.role,
                content = entity.content,
                timestamp = entity.timestamp,
                isStreaming = entity.isStreaming,
                isError = entity.isError,
                errorMessage = entity.errorMessage,
                activities = parsedActivities,
                sources = parsedSources,
                promptTokens = entity.promptTokens,
                candidatesTokens = entity.candidatesTokens,
                totalTokens = entity.totalTokens,
                rawResponseJson = entity.rawResponseJson
            )
        }
    }
}
