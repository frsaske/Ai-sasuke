package com.example.agent.tools.memory

import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.data.repository.MemoryRepository

class SaveMemoryTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name: String = "save_memory"
    override val description: String =
        "Save a key personal detail, background fact, or long-term preference about the user to persistent memory (e.g. 'User is 17 years old', 'User is in 11th grade studying Math'). Do NOT save transient chatter, only key durable facts."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "fact" to mapOf("type" to "STRING", "description" to "Concise, specific fact about the user to remember"),
            "category" to mapOf("type" to "STRING", "description" to "Category: 'Personal', 'Education', 'Preference', 'Project', 'Other'")
        ),
        "required" to listOf("fact")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fact = arguments["fact"]?.toString()?.trim() ?: ""
        val category = arguments["category"]?.toString()?.trim() ?: "Personal"

        if (fact.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "fact is required.")
        }

        return try {
            val saved = memoryRepository.saveMemory(fact, category)
            ToolResult.success(
                data = mapOf("id" to saved.id, "fact" to saved.fact, "category" to saved.category),
                summary = "Remembered: ${saved.fact}"
            )
        } catch (e: Exception) {
            ToolResult.failure("MEMORY_ERROR", e.localizedMessage ?: "Failed to save memory")
        }
    }
}
