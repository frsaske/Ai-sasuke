package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String
)

class AppSettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sasukex_app_settings"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"

        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val DEFAULT_SYSTEM_PROMPT =
            "You are SasukeX, a premier, articulate, and highly capable personal AI assistant. You deliver sharp, insightful, and well-structured responses. Use rich markdown formatting, clean headers, bullet points, and syntax-highlighted code blocks where applicable."

        val AVAILABLE_MODELS = listOf(
            ModelOption(
                id = "gemini-3.5-flash",
                displayName = "Gemini 3.5 Flash",
                description = "Recommended • High intelligence, fast streaming & balanced reasoning",
                badge = "Fast & Smart"
            ),
            ModelOption(
                id = "gemini-3.1-pro-preview",
                displayName = "Gemini 3.1 Pro",
                description = "Deep reasoning, advanced STEM & complex coding tasks",
                badge = "Reasoning"
            ),
            ModelOption(
                id = "gemini-3.1-flash-lite-preview",
                displayName = "Gemini 3.1 Flash Lite",
                description = "Ultra-low latency, lightweight queries & snappy text answers",
                badge = "Ultra Fast"
            )
        )
    }

    private val _modelFlow = MutableStateFlow(getSelectedModel())
    val modelFlow: StateFlow<String> = _modelFlow.asStateFlow()

    private val _systemPromptFlow = MutableStateFlow(getSystemPrompt())
    val systemPromptFlow: StateFlow<String> = _systemPromptFlow.asStateFlow()

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _modelFlow.value = model
    }

    fun getSystemPrompt(): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
        _systemPromptFlow.value = prompt
    }

    fun resetSystemPrompt() {
        setSystemPrompt(DEFAULT_SYSTEM_PROMPT)
    }

    fun resetAllSettings() {
        prefs.edit().clear().apply()
        _modelFlow.value = DEFAULT_MODEL
        _systemPromptFlow.value = DEFAULT_SYSTEM_PROMPT
    }
}
