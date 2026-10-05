package com.example.data.repository

import com.example.data.local.MemoryDao
import com.example.data.model.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    suspend fun getActiveMemories(): List<MemoryEntity> {
        return try {
            memoryDao.getActiveMemories()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveMemory(fact: String, category: String = "Personal"): MemoryEntity {
        val trimmedFact = fact.trim()
        val entity = MemoryEntity(
            fact = trimmedFact,
            category = category.trim().ifBlank { "Personal" }
        )
        try {
            memoryDao.insertMemory(entity)
        } catch (_: Exception) {}
        return entity
    }

    /**
     * Checks if a similar fact already exists. If yes, updates it; if not, inserts it.
     * Prevents duplicate tokens and keeps memory concise.
     */
    suspend fun saveMemoryIfNotExists(fact: String, category: String = "Personal"): MemoryEntity {
        val trimmedFact = fact.trim()
        val active = getActiveMemories()

        // Check if an existing memory is on the exact same topic (e.g. "Sasuke is 17" vs "Sasuke is 18")
        val isAge = trimmedFact.contains("years old", ignoreCase = true) || trimmedFact.contains("age", ignoreCase = true)
        val isGrade = trimmedFact.contains("grade", ignoreCase = true) || trimmedFact.contains("class", ignoreCase = true)

        val existingMatch = active.firstOrNull { existing ->
            when {
                existing.fact.equals(trimmedFact, ignoreCase = true) -> true
                isAge && (existing.fact.contains("years old", ignoreCase = true) || existing.fact.contains("age", ignoreCase = true)) -> true
                isGrade && (existing.fact.contains("grade", ignoreCase = true) || existing.fact.contains("class", ignoreCase = true)) -> true
                else -> false
            }
        }

        return if (existingMatch != null) {
            updateMemory(existingMatch.id, trimmedFact, category)
            existingMatch.copy(fact = trimmedFact, category = category)
        } else {
            saveMemory(trimmedFact, category)
        }
    }

    suspend fun updateMemory(id: String, newFact: String, newCategory: String) {
        val trimmedFact = newFact.trim()
        if (trimmedFact.isBlank()) return
        val memory = MemoryEntity(
            id = id,
            fact = trimmedFact,
            category = newCategory.trim().ifBlank { "Personal" },
            timestamp = System.currentTimeMillis()
        )
        try {
            memoryDao.updateMemory(memory)
        } catch (_: Exception) {}
    }

    suspend fun deleteMemory(id: String) {
        try {
            memoryDao.deleteMemoryById(id)
        } catch (_: Exception) {}
    }

    suspend fun clearAllMemories() {
        try {
            memoryDao.clearAllMemories()
        } catch (_: Exception) {}
    }

    suspend fun setMemoryEnabled(id: String, enabled: Boolean) {
        try {
            memoryDao.setMemoryEnabled(id, enabled)
        } catch (_: Exception) {}
    }

    /**
     * Builds a token-minimal memory block to inject into the system prompt.
     * Takes only a few tokens but personalizes all responses.
     */
    suspend fun buildMemoryPromptBlock(): String {
        val active = getActiveMemories()
        if (active.isEmpty()) return ""

        return buildString {
            append("\n\n[Persistent User Profile & Background Memory]\n")
            append("Important facts remembered about the user across conversations (keep answers aligned):\n")
            active.forEach { mem ->
                append("• ").append(mem.fact).append("\n")
            }
        }
    }

    suspend fun exportMemoriesAsText(): String {
        val list = getActiveMemories()
        if (list.isEmpty()) return "No memories saved yet."

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return buildString {
            append("SasukeX User Memories (Total: ${list.size})\n")
            append("=====================================\n\n")
            list.forEachIndexed { i, m ->
                append("${i + 1}. [${m.category}] ${m.fact}\n")
                append("   Saved: ${sdf.format(Date(m.timestamp))}\n\n")
            }
        }
    }

    suspend fun exportMemoriesAsJson(): String {
        val list = getActiveMemories()
        val jsonArray = JSONArray()
        list.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("fact", m.fact)
                put("category", m.category)
                put("timestamp", m.timestamp)
                put("isEnabled", m.isEnabled)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }
}
