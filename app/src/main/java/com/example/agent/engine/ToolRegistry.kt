package com.example.agent.engine

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.tools.currency.ExchangeRateTool
import com.example.agent.tools.github.GitHubCreateBranchTool
import com.example.agent.tools.github.GitHubCreateFileTool
import com.example.agent.tools.github.GitHubCreateIssueTool
import com.example.agent.tools.github.GitHubCreatePullRequestTool
import com.example.agent.tools.github.GitHubCreateRepoTool
import com.example.agent.tools.github.GitHubDeleteFileTool
import com.example.agent.tools.github.GitHubDeleteRepoTool
import com.example.agent.tools.github.GitHubDownloadFileTool
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
import com.example.agent.tools.github.GitHubUploadLocalFileTool
import com.example.agent.tools.github.ReadAttachedFileTool
import com.example.agent.tools.gmail.GmailCreateDraftTool
import com.example.agent.tools.gmail.GmailDeleteMessageTool
import com.example.agent.tools.gmail.GmailListMessagesTool
import com.example.agent.tools.gmail.GmailModifyLabelsTool
import com.example.agent.tools.gmail.GmailReadMessageTool
import com.example.agent.tools.gmail.GmailSendMessageTool
import com.example.agent.tools.gmail.GmailService
import com.example.agent.tools.time.CurrentTimeTool
import com.example.agent.tools.weather.WeatherTool
import com.example.agent.tools.wikipedia.WikipediaTool
import com.example.agent.tools.web.FetchUrlTool
import com.example.agent.tools.web.TavilyWebSearchTool
import com.example.agent.tools.web.SearxWebSearchTool
import com.example.ai.model.FunctionDeclaration

/**
 * Central registry of all agent capabilities.
 * Built to be easily extendable with future integrations:
 * Gmail, GitHub, File Downloads, Attachments, Web Search, Knowledge Tools.
 */
class ToolRegistry(
    private val getTavilyKey: () -> String,
    private val getSearxUrl: () -> String = { "" },
    private val getGitHubToken: () -> String,
    private val getGmailToken: () -> String = { "" },
    private val getGmailUserEmail: () -> String = { "me" },
    private val context: Context? = null,
    private val memoryRepository: com.example.data.repository.MemoryRepository? = null
) {
    private val toolsMap = mutableMapOf<String, Tool>()

    val gitHubService = GitHubService(getGitHubToken)
    val gmailService = GmailService(getGmailToken, getGmailUserEmail)

    init {
        // 0. User Memory Tool
        if (memoryRepository != null) {
            register(com.example.agent.tools.memory.SaveMemoryTool(memoryRepository))
        }

        // 1. Web Tools
        register(TavilyWebSearchTool(getTavilyKey))
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
        register(GitHubCreateRepoTool(gitHubService))
        register(GitHubDeleteRepoTool(gitHubService))
        register(GitHubCreateIssueTool(gitHubService))
        register(GitHubCreateFileTool(gitHubService))
        register(GitHubUpdateFileTool(gitHubService))
        register(GitHubDeleteFileTool(gitHubService))
        register(GitHubCreateBranchTool(gitHubService))
        register(GitHubCreatePullRequestTool(gitHubService))

        // 4. File Downloads, Local Uploads, Local Workspace Files & Attached Files
        if (context != null) {
            register(GitHubDownloadFileTool(gitHubService, context))
            register(GitHubUploadLocalFileTool(gitHubService, context))
            register(ReadAttachedFileTool(context))

            // Intelligent Local Storage Agent tools
            register(com.example.agent.tools.storage.LocalFileSearchTool(context))
            register(com.example.agent.tools.storage.StorageFileReadTool(context))
            register(com.example.agent.tools.storage.StorageFileCreateTool(context))
            register(com.example.agent.tools.storage.StorageFileWriteTool(context))
            register(com.example.agent.tools.storage.StorageFileCopyTool(context))
            register(com.example.agent.tools.storage.StorageFileMoveTool(context))
            register(com.example.agent.tools.storage.StorageFileRenameTool(context))
            register(com.example.agent.tools.storage.StorageFileDeleteTool(context))
            register(com.example.agent.tools.storage.StorageFolderCreateTool(context))
            register(com.example.agent.tools.storage.StorageFolderListTool(context))
            register(com.example.agent.tools.storage.StorageFileOpenTool(context))

            // Termux Terminal tool
            register(com.example.agent.tools.termux.TerminalExecuteTool(context))

            // Google Calendar Agent tools
            register(com.example.agent.tools.calendar.CalendarListCalendarsTool(context))
            register(com.example.agent.tools.calendar.CalendarListEventsTool(context))
            register(com.example.agent.tools.calendar.CalendarSearchEventsTool(context))
            register(com.example.agent.tools.calendar.CalendarCreateEventTool(context))
            register(com.example.agent.tools.calendar.CalendarDeleteEventTool(context))
            register(com.example.agent.tools.calendar.CalendarFindFreeTimeTool(context))

            // Google Drive Agent tools
            val driveService = com.example.drive.GoogleDriveService(context)
            register(com.example.agent.tools.drive.DriveSearchTool(driveService))
            register(com.example.agent.tools.drive.DriveListFolderTool(driveService))
            register(com.example.agent.tools.drive.DriveGetMetadataTool(driveService))
            register(com.example.agent.tools.drive.DriveReadTextTool(driveService))
            register(com.example.agent.tools.drive.DriveDownloadTool(driveService, context))
            register(com.example.agent.tools.drive.DriveUploadTool(driveService, context))
            register(com.example.agent.tools.drive.DriveCreateFolderTool(driveService))
            register(com.example.agent.tools.drive.DriveRenameTool(driveService))
            register(com.example.agent.tools.drive.DriveTrashTool(driveService))
            register(com.example.agent.tools.drive.DriveShareLinkTool(driveService))

            // Direct File ↔ GitHub Workflows & Universal File Transfer
            register(com.example.agent.tools.github.GitHubDownloadToLocalTool(gitHubService, context))
            register(com.example.agent.tools.github.GitHubUploadFromLocalTool(gitHubService, context))
            register(com.example.agent.tools.github.GitHubCloneToLocalTool(context))
            register(com.example.agent.tools.github.GitHubPullToLocalTool(context))
            register(com.example.agent.tools.github.GitHubCommitLocalChangesTool(context))
            register(com.example.agent.tools.github.GitHubPushLocalChangesTool(context))
            register(com.example.agent.tools.github.UniversalFileTransferTool(gitHubService, context))
        }

        // 5. Gmail Tools
        register(GmailListMessagesTool(gmailService))
        register(GmailReadMessageTool(gmailService))
        register(GmailSendMessageTool(gmailService))
        register(GmailCreateDraftTool(gmailService))
        register(GmailDeleteMessageTool(gmailService))
        register(GmailModifyLabelsTool(gmailService))

        // 6. Utility Knowledge Tools
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
        isGmailEnabled: Boolean = true,
        isLocalFilesEnabled: Boolean = true,
        isMemoryEnabled: Boolean = true,
        isTermuxEnabled: Boolean = true,
        isCalendarEnabled: Boolean = true,
        isDriveEnabled: Boolean = true,
        isWeatherEnabled: Boolean = true,
        isWikipediaEnabled: Boolean = true,
        isCurrencyEnabled: Boolean = true,
        isTimeEnabled: Boolean = true
    ): List<FunctionDeclaration> {
        return toolsMap.values.filter { tool ->
            when {
                tool.name == "web_search" || tool.name == "fetch_url" -> isWebEnabled
                tool.name.startsWith("github_") || tool.name == "read_attached_file" -> isGitHubEnabled
                tool.name.startsWith("local_") -> isLocalFilesEnabled
                tool.name == "terminal_execute" -> isTermuxEnabled
                tool.name.startsWith("calendar_") -> isCalendarEnabled
                tool.name.startsWith("drive_") -> isDriveEnabled
                tool.name == "universal_file_transfer" -> (isGitHubEnabled || isDriveEnabled || isLocalFilesEnabled)
                tool.name == "save_memory" -> isMemoryEnabled
                tool.name.startsWith("gmail_") -> isGmailEnabled
                tool.name == "get_weather" -> isWeatherEnabled
                tool.name == "wikipedia_search" -> isWikipediaEnabled
                tool.name == "exchange_rate" -> isCurrencyEnabled
                tool.name == "current_time" -> isTimeEnabled
                else -> true
            }
        }.map { it.toDeclaration() }
    }
}
