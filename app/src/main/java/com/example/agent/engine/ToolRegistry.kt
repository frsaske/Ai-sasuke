package com.example.agent.engine

import com.example.agent.model.Tool
import com.example.agent.tools.currency.ExchangeRateTool
import com.example.agent.tools.github.GitHubCreateBranchTool
import com.example.agent.tools.github.GitHubCreateFileTool
import com.example.agent.tools.github.GitHubCreateIssueTool
import com.example.agent.tools.github.GitHubCreatePullRequestTool
import com.example.agent.tools.github.GitHubDeleteFileTool
import com.example.agent.tools.github.GitHubListBranchesTool
import com.example.agent.tools.github.GitHubListCommitsTool
import com.example.agent.tools.github.GitHubListFilesTool
import com.example.agent.tools.github.GitHubListIssuesTool
import com.example.agent.tools.github.GitHubListReleasesTool
import com.example.agent.tools.github.GitHubListReposTool
import com.example.agent.tools.github.GitHubReadFileTool
import com.example.agent.tools.github.GitHubRepoInfoTool
import com.example.agent.tools.github.GitHubService
import com.example.agent.tools.github.GitHubUpdateFileTool
import com.example.agent.tools.time.CurrentTimeTool
import com.example.agent.tools.weather.WeatherTool
import com.example.agent.tools.wikipedia.WikipediaTool
import com.example.agent.tools.web.FetchUrlTool
import com.example.agent.tools.web.SearxWebSearchTool
import com.example.ai.model.FunctionDeclaration

/**
 * Central registry of all agent capabilities.
 * Built to be easily extendable with future integrations:
 * Gmail, Drive, Calendar, Terminal, Files, Device Actions, etc.
 */
class ToolRegistry(
    private val getSearxUrl: () -> String,
    private val getGitHubToken: () -> String
) {
    private val toolsMap = mutableMapOf<String, Tool>()

    val gitHubService = GitHubService(getGitHubToken)

    init {
        // 1. Web Tools
        register(SearxWebSearchTool(getSearxUrl))
        register(FetchUrlTool())

        // 2. GitHub Read Tools
        register(GitHubListReposTool(gitHubService))
        register(GitHubRepoInfoTool(gitHubService))
        register(GitHubListFilesTool(gitHubService))
        register(GitHubReadFileTool(gitHubService))
        register(GitHubListCommitsTool(gitHubService))
        register(GitHubListIssuesTool(gitHubService))
        register(GitHubListBranchesTool(gitHubService))
        register(GitHubListReleasesTool(gitHubService))

        // 3. GitHub Write / Destructive Tools
        register(GitHubCreateIssueTool(gitHubService))
        register(GitHubCreateFileTool(gitHubService))
        register(GitHubUpdateFileTool(gitHubService))
        register(GitHubDeleteFileTool(gitHubService))
        register(GitHubCreateBranchTool(gitHubService))
        register(GitHubCreatePullRequestTool(gitHubService))

        // 4. Utility Knowledge Tools
        register(WeatherTool())
        register(WikipediaTool())
        register(ExchangeRateTool())
        register(CurrentTimeTool())
    }

    fun register(tool: Tool) {
        toolsMap[tool.name] = tool
    }

    fun unregister(name: String) {
        toolsMap.remove(name)
    }

    fun getTool(name: String): Tool? = toolsMap[name]

    fun getAllTools(): List<Tool> = toolsMap.values.toList()

    /**
     * Filters available tools based on user settings toggles.
     */
    fun getActiveDeclarations(
        isWebEnabled: Boolean = true,
        isGitHubEnabled: Boolean = true,
        isWeatherEnabled: Boolean = true,
        isWikipediaEnabled: Boolean = true,
        isCurrencyEnabled: Boolean = true,
        isTimeEnabled: Boolean = true
    ): List<FunctionDeclaration> {
        return toolsMap.values.filter { tool ->
            when {
                tool.name == "web_search" || tool.name == "fetch_url" -> isWebEnabled
                tool.name.startsWith("github_") -> isGitHubEnabled
                tool.name == "get_weather" -> isWeatherEnabled
                tool.name == "wikipedia_search" -> isWikipediaEnabled
                tool.name == "exchange_rate" -> isCurrencyEnabled
                tool.name == "current_time" -> isTimeEnabled
                else -> true // future tools active by default
            }
        }.map { it.toDeclaration() }
    }
}
