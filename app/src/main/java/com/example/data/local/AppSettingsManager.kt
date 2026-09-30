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

        // Agent Tools keys
        private const val KEY_WEB_SEARCH_ENABLED = "tool_web_search_enabled"
        private const val KEY_SEARX_URL = "tool_searx_url"
        private const val KEY_WEATHER_ENABLED = "tool_weather_enabled"
        private const val KEY_WIKIPEDIA_ENABLED = "tool_wikipedia_enabled"
        private const val KEY_CURRENCY_ENABLED = "tool_currency_enabled"
        private const val KEY_TIME_ENABLED = "tool_time_enabled"
        private const val KEY_SHOW_AGENT_ACTIVITY = "tool_show_agent_activity"
        private const val KEY_REQUIRE_CONFIRMATION = "tool_require_confirmation"

        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val DEFAULT_SEARX_URL = "https://search.ononoki.org"
        const val DEFAULT_SYSTEM_PROMPT =
            "You are SasukeX, a premier, articulate, and highly capable personal AI assistant equipped with real-world agent tools (Web Search, Web Fetch, GitHub, Weather, Wikipedia, Currency, Time). You deliver sharp, insightful, and well-structured responses. Use rich markdown formatting, clean headers, bullet points, and code blocks where applicable."

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

    // Agent Tools Flows
    private val _webSearchEnabledFlow = MutableStateFlow(isWebSearchEnabled())
    val webSearchEnabledFlow: StateFlow<Boolean> = _webSearchEnabledFlow.asStateFlow()

    private val _searxUrlFlow = MutableStateFlow(getSearxUrl())
    val searxUrlFlow: StateFlow<String> = _searxUrlFlow.asStateFlow()

    private val _weatherEnabledFlow = MutableStateFlow(isWeatherEnabled())
    val weatherEnabledFlow: StateFlow<Boolean> = _weatherEnabledFlow.asStateFlow()

    private val _wikipediaEnabledFlow = MutableStateFlow(isWikipediaEnabled())
    val wikipediaEnabledFlow: StateFlow<Boolean> = _wikipediaEnabledFlow.asStateFlow()

    private val _currencyEnabledFlow = MutableStateFlow(isCurrencyEnabled())
    val currencyEnabledFlow: StateFlow<Boolean> = _currencyEnabledFlow.asStateFlow()

    private val _timeEnabledFlow = MutableStateFlow(isTimeEnabled())
    val timeEnabledFlow: StateFlow<Boolean> = _timeEnabledFlow.asStateFlow()

    private val _showAgentActivityFlow = MutableStateFlow(isShowAgentActivityEnabled())
    val showAgentActivityFlow: StateFlow<Boolean> = _showAgentActivityFlow.asStateFlow()

    private val _requireConfirmationFlow = MutableStateFlow(isRequireConfirmationEnabled())
    val requireConfirmationFlow: StateFlow<Boolean> = _requireConfirmationFlow.asStateFlow()

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

    // --- Agent Tools Getters & Setters ---

    fun isWebSearchEnabled(): Boolean = prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, true)
    fun setWebSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, enabled).apply()
        _webSearchEnabledFlow.value = enabled
    }

    fun getSearxUrl(): String = prefs.getString(KEY_SEARX_URL, DEFAULT_SEARX_URL) ?: DEFAULT_SEARX_URL
    fun setSearxUrl(url: String) {
        val sanitized = url.trim()
        prefs.edit().putString(KEY_SEARX_URL, sanitized).apply()
        _searxUrlFlow.value = sanitized
    }

    fun isWeatherEnabled(): Boolean = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
        _weatherEnabledFlow.value = enabled
    }

    fun isWikipediaEnabled(): Boolean = prefs.getBoolean(KEY_WIKIPEDIA_ENABLED, true)
    fun setWikipediaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIKIPEDIA_ENABLED, enabled).apply()
        _wikipediaEnabledFlow.value = enabled
    }

    fun isCurrencyEnabled(): Boolean = prefs.getBoolean(KEY_CURRENCY_ENABLED, true)
    fun setCurrencyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CURRENCY_ENABLED, enabled).apply()
        _currencyEnabledFlow.value = enabled
    }

    fun isTimeEnabled(): Boolean = prefs.getBoolean(KEY_TIME_ENABLED, true)
    fun setTimeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIME_ENABLED, enabled).apply()
        _timeEnabledFlow.value = enabled
    }

    fun isShowAgentActivityEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_AGENT_ACTIVITY, true)
    fun setShowAgentActivityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_AGENT_ACTIVITY, enabled).apply()
        _showAgentActivityFlow.value = enabled
    }

    fun isRequireConfirmationEnabled(): Boolean = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, true)
    fun setRequireConfirmationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, enabled).apply()
        _requireConfirmationFlow.value = enabled
    }

    fun resetAllSettings() {
        prefs.edit().clear().apply()
        _modelFlow.value = DEFAULT_MODEL
        _systemPromptFlow.value = DEFAULT_SYSTEM_PROMPT
        _webSearchEnabledFlow.value = true
        _searxUrlFlow.value = DEFAULT_SEARX_URL
        _weatherEnabledFlow.value = true
        _wikipediaEnabledFlow.value = true
        _currencyEnabledFlow.value = true
        _timeEnabledFlow.value = true
        _showAgentActivityFlow.value = true
        _requireConfirmationFlow.value = true
    }
}
