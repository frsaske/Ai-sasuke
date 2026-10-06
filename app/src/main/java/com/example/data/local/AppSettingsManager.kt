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
        private const val KEY_GMAIL_ENABLED = "tool_gmail_enabled"
        private const val KEY_GITHUB_ENABLED = "tool_github_enabled"
        private const val KEY_LOCAL_FILES_ENABLED = "tool_local_files_enabled"
        private const val KEY_MEMORY_ENABLED = "tool_memory_enabled"
        private const val KEY_SHOW_AGENT_ACTIVITY = "tool_show_agent_activity"
        private const val KEY_REQUIRE_CONFIRMATION = "tool_require_confirmation"
        private const val KEY_GIT_REQUIRE_COMMIT_CONFIRMATION = "git_require_commit_confirmation"
        private const val KEY_GIT_DEFAULT_BRANCH = "git_default_branch"
        private const val KEY_GIT_DEFAULT_OWNER = "git_default_owner"
        private const val KEY_GIT_DEFAULT_REPO = "git_default_repo"

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite-preview"
        const val DEFAULT_SEARX_URL = "https://search.ononoki.org"
        const val DEFAULT_SYSTEM_PROMPT =
            "You are SasukeX, a smart, concise, and ultra-capable personal AI assistant equipped with real tools: Termux Terminal (terminal_execute), Intelligent Local Storage, Google Calendar, Google Drive, Git/GitHub operations, Gmail, Search, Weather, Wikipedia, Currency, Time, and Memory.\n\nCRITICAL RULES:\n1. CREDIT/TOKEN SAVINGS: Keep answers concise and direct. Never dump huge raw logs or full files unless explicitly asked. Use compact metadata search tools.\n2. FILE TRANSFERS: When handling files or Git, invoke direct tool actions. Never output binary content in prompt text.\n3. MEMORY: Save important personal facts (name, age, preferences) using save_memory.\n4. SAFETY: Sensitive commands (rm -rf, delete, force-push) require confirmation."

        val AVAILABLE_MODELS = listOf(
            ModelOption(
                id = "gemini-3.1-flash-lite-preview",
                displayName = "SasukeX Sharingan (Gemini 3.1 Flash Lite)",
                description = "Ultra Fast & Free Tier Friendly • Instant responses, low latency & minimal quota consumption",
                badge = "Recommended"
            ),
            ModelOption(
                id = "gemini-3.8-flash",
                displayName = "SasukeX Chidori (Gemini 3.8 Flash)",
                description = "Modern Flagship • Superior intelligence, multimodal reasoning & full tool execution",
                badge = "Flagship"
            ),
            ModelOption(
                id = "gemini-3.5-flash",
                displayName = "SasukeX Raikiri (Gemini 3.5 Flash)",
                description = "High Throughput • Fast, capable generation for multi-turn chats and tools",
                badge = "Fast"
            ),
            ModelOption(
                id = "gemini-3.1-pro-preview",
                displayName = "SasukeX Susanoo (Gemini 3.1 Pro)",
                description = "Maximum Reasoning • Complex coding, deep problem-solving & advanced analysis",
                badge = "Deep Reasoning"
            ),
            ModelOption(
                id = "gemini-flash-latest",
                displayName = "SasukeX Rinnegan (Flash Latest)",
                description = "Auto-Updating • Automatically tracks Google's latest Gemini Flash generation",
                badge = "Auto Latest"
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

    private val _gmailEnabledFlow = MutableStateFlow(isGmailEnabled())
    val gmailEnabledFlow: StateFlow<Boolean> = _gmailEnabledFlow.asStateFlow()

    private val _gitHubEnabledFlow = MutableStateFlow(isGitHubEnabled())
    val gitHubEnabledFlow: StateFlow<Boolean> = _gitHubEnabledFlow.asStateFlow()

    private val _localFilesEnabledFlow = MutableStateFlow(isLocalFilesEnabled())
    val localFilesEnabledFlow: StateFlow<Boolean> = _localFilesEnabledFlow.asStateFlow()

    private val _memoryEnabledFlow = MutableStateFlow(isMemoryEnabled())
    val memoryEnabledFlow: StateFlow<Boolean> = _memoryEnabledFlow.asStateFlow()

    private val _termuxEnabledFlow = MutableStateFlow(isTermuxEnabled())
    val termuxEnabledFlow: StateFlow<Boolean> = _termuxEnabledFlow.asStateFlow()

    private val _calendarEnabledFlow = MutableStateFlow(isCalendarEnabled())
    val calendarEnabledFlow: StateFlow<Boolean> = _calendarEnabledFlow.asStateFlow()

    private val _driveEnabledFlow = MutableStateFlow(isDriveEnabled())
    val driveEnabledFlow: StateFlow<Boolean> = _driveEnabledFlow.asStateFlow()

    private val _showAgentActivityFlow = MutableStateFlow(isShowAgentActivityEnabled())
    val showAgentActivityFlow: StateFlow<Boolean> = _showAgentActivityFlow.asStateFlow()

    private val _requireConfirmationFlow = MutableStateFlow(isRequireConfirmationEnabled())
    val requireConfirmationFlow: StateFlow<Boolean> = _requireConfirmationFlow.asStateFlow()

    private val _gitRequireCommitConfirmationFlow = MutableStateFlow(isGitRequireCommitConfirmation())
    val gitRequireCommitConfirmationFlow: StateFlow<Boolean> = _gitRequireCommitConfirmationFlow.asStateFlow()

    private val _gitDefaultBranchFlow = MutableStateFlow(getGitDefaultBranch())
    val gitDefaultBranchFlow: StateFlow<String> = _gitDefaultBranchFlow.asStateFlow()

    private val _gitDefaultOwnerFlow = MutableStateFlow(getGitDefaultOwner())
    val gitDefaultOwnerFlow: StateFlow<String> = _gitDefaultOwnerFlow.asStateFlow()

    private val _gitDefaultRepoFlow = MutableStateFlow(getGitDefaultRepo())
    val gitDefaultRepoFlow: StateFlow<String> = _gitDefaultRepoFlow.asStateFlow()

    fun getSelectedModel(): String {
        val stored = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        return if (stored.startsWith("gemini-1.") || stored == "gemini-2.0-flash" || AVAILABLE_MODELS.none { it.id == stored }) {
            setSelectedModel(DEFAULT_MODEL)
            DEFAULT_MODEL
        } else {
            stored
        }
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

    fun isGmailEnabled(): Boolean = prefs.getBoolean(KEY_GMAIL_ENABLED, true)
    fun setGmailEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GMAIL_ENABLED, enabled).apply()
        _gmailEnabledFlow.value = enabled
    }

    fun isGitHubEnabled(): Boolean = prefs.getBoolean(KEY_GITHUB_ENABLED, true)
    fun setGitHubEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GITHUB_ENABLED, enabled).apply()
        _gitHubEnabledFlow.value = enabled
    }

    fun isLocalFilesEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_FILES_ENABLED, true)
    fun setLocalFilesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_FILES_ENABLED, enabled).apply()
        _localFilesEnabledFlow.value = enabled
    }

    fun isMemoryEnabled(): Boolean = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
        _memoryEnabledFlow.value = enabled
    }

    fun isGitRequireCommitConfirmation(): Boolean = prefs.getBoolean(KEY_GIT_REQUIRE_COMMIT_CONFIRMATION, true)
    fun setGitRequireCommitConfirmation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GIT_REQUIRE_COMMIT_CONFIRMATION, enabled).apply()
        _gitRequireCommitConfirmationFlow.value = enabled
    }

    fun getGitDefaultBranch(): String = prefs.getString(KEY_GIT_DEFAULT_BRANCH, "main") ?: "main"
    fun setGitDefaultBranch(branch: String) {
        val sanitized = branch.trim().ifBlank { "main" }
        prefs.edit().putString(KEY_GIT_DEFAULT_BRANCH, sanitized).apply()
        _gitDefaultBranchFlow.value = sanitized
    }

    fun getGitDefaultOwner(): String = prefs.getString(KEY_GIT_DEFAULT_OWNER, "") ?: ""
    fun setGitDefaultOwner(owner: String) {
        val sanitized = owner.trim()
        prefs.edit().putString(KEY_GIT_DEFAULT_OWNER, sanitized).apply()
        _gitDefaultOwnerFlow.value = sanitized
    }

    fun getGitDefaultRepo(): String = prefs.getString(KEY_GIT_DEFAULT_REPO, "") ?: ""
    fun setGitDefaultRepo(repo: String) {
        val sanitized = repo.trim()
        prefs.edit().putString(KEY_GIT_DEFAULT_REPO, sanitized).apply()
        _gitDefaultRepoFlow.value = sanitized
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

    fun isTermuxEnabled(): Boolean = prefs.getBoolean("tool_termux_enabled", true)
    fun setTermuxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tool_termux_enabled", enabled).apply()
        _termuxEnabledFlow.value = enabled
    }

    fun isCalendarEnabled(): Boolean = prefs.getBoolean("tool_calendar_enabled", true)
    fun setCalendarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tool_calendar_enabled", enabled).apply()
        _calendarEnabledFlow.value = enabled
    }

    fun isDriveEnabled(): Boolean = prefs.getBoolean("tool_drive_enabled", true)
    fun setDriveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tool_drive_enabled", enabled).apply()
        _driveEnabledFlow.value = enabled
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
        _gmailEnabledFlow.value = true
        _gitHubEnabledFlow.value = true
        _localFilesEnabledFlow.value = true
        _memoryEnabledFlow.value = true
        _termuxEnabledFlow.value = true
        _calendarEnabledFlow.value = true
        _driveEnabledFlow.value = true
        _showAgentActivityFlow.value = true
        _requireConfirmationFlow.value = true
        _gitRequireCommitConfirmationFlow.value = true
        _gitDefaultBranchFlow.value = "main"
        _gitDefaultOwnerFlow.value = ""
        _gitDefaultRepoFlow.value = ""
    }
}
