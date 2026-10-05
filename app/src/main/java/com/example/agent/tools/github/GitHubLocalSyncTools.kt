package com.example.agent.tools.github

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.drive.GoogleDriveService
import com.example.termux.TermuxExecutor
import com.example.util.LocalWorkspaceManager
import com.example.util.TransferJob
import com.example.util.TransferLocation
import com.example.util.UniversalFileTransferEngine
import java.io.File

class GitHubDownloadToLocalTool(
    private val gitHubService: GitHubService,
    private val context: Context
) : Tool {
    override val name: String = "github_download_to_local"
    override val description: String =
        "Download a file directly from a GitHub repository into the local storage workspace without routing bytes through AI."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "repo" to mapOf("type" to "STRING", "description" to "Repository 'owner/repo'"),
            "path" to mapOf("type" to "STRING", "description" to "File path in repo (e.g. 'README.md')"),
            "local_destination" to mapOf("type" to "STRING", "description" to "Local filename or relative path")
        ),
        "required" to listOf("repo", "path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val localDest = arguments["local_destination"]?.toString()?.trim() ?: File(path).name

        val parts = repo.split("/")
        if (parts.size < 2) return ToolResult.failure("INVALID_ARGUMENT", "Repo must be 'owner/repo'")

        val job = TransferJob(
            sourceLocation = TransferLocation.GITHUB,
            sourcePathOrId = "$repo:$path",
            targetLocation = TransferLocation.LOCAL_STORAGE,
            targetPathOrId = localDest
        )

        val res = UniversalFileTransferEngine.transfer(context, gitHubService, GoogleDriveService(context), job)
        return if (res.success) {
            ToolResult.success(data = mapOf("repo" to repo, "path" to path, "local" to localDest), summary = res.summary)
        } else {
            ToolResult.failure(res.error ?: "DOWNLOAD_FAIL", res.summary)
        }
    }
}

class GitHubUploadFromLocalTool(
    private val gitHubService: GitHubService,
    private val context: Context
) : Tool {
    override val name: String = "github_upload_from_local"
    override val description: String =
        "Upload a local file to a GitHub repository directly."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "local_path" to mapOf("type" to "STRING", "description" to "Local file path in workspace"),
            "repo" to mapOf("type" to "STRING", "description" to "GitHub repository 'owner/repo'"),
            "remote_path" to mapOf("type" to "STRING", "description" to "Target remote path in repository"),
            "commit_message" to mapOf("type" to "STRING", "description" to "Commit message")
        ),
        "required" to listOf("local_path", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val localPath = arguments["local_path"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val remotePath = arguments["remote_path"]?.toString()?.trim() ?: File(localPath).name

        val job = TransferJob(
            sourceLocation = TransferLocation.LOCAL_STORAGE,
            sourcePathOrId = localPath,
            targetLocation = TransferLocation.GITHUB,
            targetPathOrId = "$repo:$remotePath"
        )

        val res = UniversalFileTransferEngine.transfer(context, gitHubService, GoogleDriveService(context), job)
        return if (res.success) {
            ToolResult.success(data = mapOf("local" to localPath, "repo" to repo, "remote_path" to remotePath), summary = res.summary)
        } else {
            ToolResult.failure(res.error ?: "UPLOAD_FAIL", res.summary)
        }
    }
}

class GitHubCloneToLocalTool(private val context: Context) : Tool {
    override val name: String = "github_clone_to_local"
    override val description: String = "Clone a GitHub repository to local directory via Termux / git clone."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "repo_url" to mapOf("type" to "STRING", "description" to "Git clone URL or 'owner/repo'"),
            "directory_name" to mapOf("type" to "STRING", "description" to "Optional target folder name")
        ),
        "required" to listOf("repo_url")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val raw = arguments["repo_url"]?.toString()?.trim() ?: ""
        val dir = arguments["directory_name"]?.toString()?.trim() ?: ""

        val url = if (raw.startsWith("http") || raw.startsWith("git@")) raw else "https://github.com/$raw.git"
        val cmd = if (dir.isNotBlank()) "git clone $url '$dir'" else "git clone $url"

        val res = TermuxExecutor.execute(context, cmd)
        return if (res.success) {
            ToolResult.success(data = mapOf("command" to cmd, "stdout" to res.stdout), summary = "Cloned $url successfully")
        } else {
            ToolResult.failure("CLONE_FAIL", "git clone exited with code ${res.exitCode}: ${res.stderr}")
        }
    }
}

class GitHubPullToLocalTool(private val context: Context) : Tool {
    override val name: String = "github_pull_to_local"
    override val description: String = "Pull latest changes from GitHub remote inside a local Git repository."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "folder" to mapOf("type" to "STRING", "description" to "Local repository folder (leave empty for current workspace)"),
            "remote" to mapOf("type" to "STRING", "description" to "Remote name (default 'origin')"),
            "branch" to mapOf("type" to "STRING", "description" to "Branch name (default 'main')")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val folder = arguments["folder"]?.toString()?.trim()
        val remote = arguments["remote"]?.toString()?.trim() ?: "origin"
        val branch = arguments["branch"]?.toString()?.trim() ?: "main"

        val ws = LocalWorkspaceManager.getWorkspaceDir(context)
        val targetDir = if (!folder.isNullOrBlank()) File(ws, folder).absolutePath else ws.absolutePath

        val cmd = "git pull $remote $branch"
        val res = TermuxExecutor.execute(context, cmd, workingDirectory = targetDir)

        return if (res.success) {
            ToolResult.success(data = mapOf("command" to cmd, "output" to res.stdout), summary = "Pulled latest changes from $remote/$branch")
        } else {
            ToolResult.failure("PULL_FAIL", "git pull failed: ${res.stderr}")
        }
    }
}

class GitHubCommitLocalChangesTool(private val context: Context) : Tool {
    override val name: String = "github_commit_local_changes"
    override val description: String = "Stage files (git add) and create a local git commit."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "message" to mapOf("type" to "STRING", "description" to "Commit message"),
            "folder" to mapOf("type" to "STRING", "description" to "Repository folder")
        ),
        "required" to listOf("message")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val msg = arguments["message"]?.toString()?.trim() ?: "Update from SasukeX"
        val folder = arguments["folder"]?.toString()?.trim()

        val ws = LocalWorkspaceManager.getWorkspaceDir(context)
        val targetDir = if (!folder.isNullOrBlank()) File(ws, folder).absolutePath else ws.absolutePath

        val escapedMsg = msg.replace("\"", "\\\"")
        val cmd = "git add -A && git commit -m \"$escapedMsg\""
        val res = TermuxExecutor.execute(context, cmd, workingDirectory = targetDir)

        return if (res.success) {
            ToolResult.success(data = mapOf("message" to msg, "output" to res.stdout), summary = "Committed changes: $msg")
        } else {
            ToolResult.failure("COMMIT_FAIL", "Commit failed: ${res.stderr.ifBlank { res.stdout }}")
        }
    }
}

class GitHubPushLocalChangesTool(private val context: Context) : Tool {
    override val name: String = "github_push_local_changes"
    override val description: String = "Push local commits to GitHub remote repository. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE
    override val requiresConfirmation: Boolean = true
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "folder" to mapOf("type" to "STRING", "description" to "Repository folder"),
            "remote" to mapOf("type" to "STRING", "description" to "Remote name (default 'origin')"),
            "branch" to mapOf("type" to "STRING", "description" to "Branch name (default 'main')")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val folder = arguments["folder"]?.toString()?.trim()
        val remote = arguments["remote"]?.toString()?.trim() ?: "origin"
        val branch = arguments["branch"]?.toString()?.trim() ?: "main"

        val ws = LocalWorkspaceManager.getWorkspaceDir(context)
        val targetDir = if (!folder.isNullOrBlank()) File(ws, folder).absolutePath else ws.absolutePath

        val cmd = "git push $remote $branch"
        val res = TermuxExecutor.execute(context, cmd, workingDirectory = targetDir)

        return if (res.success) {
            ToolResult.success(data = mapOf("pushed" to true, "remote" to remote, "branch" to branch), summary = "Pushed to $remote/$branch successfully")
        } else {
            ToolResult.failure("PUSH_FAIL", "git push failed: ${res.stderr}")
        }
    }
}

class UniversalFileTransferTool(
    private val gitHubService: GitHubService,
    private val context: Context
) : Tool {
    override val name: String = "universal_file_transfer"
    override val description: String =
        "Directly transfer files between systems (LOCAL_STORAGE, TERMUX, GITHUB, GOOGLE_DRIVE) without loading binary bytes into AI prompt."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "source_type" to mapOf("type" to "STRING", "description" to "LOCAL_STORAGE, TERMUX, GITHUB, or GOOGLE_DRIVE"),
            "source_target" to mapOf("type" to "STRING", "description" to "Path, file ID, or 'repo:path'"),
            "dest_type" to mapOf("type" to "STRING", "description" to "LOCAL_STORAGE, TERMUX, GITHUB, or GOOGLE_DRIVE"),
            "dest_target" to mapOf("type" to "STRING", "description" to "Destination path, folder ID, or 'repo:path'")
        ),
        "required" to listOf("source_type", "source_target", "dest_type", "dest_target")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val srcTypeStr = arguments["source_type"]?.toString()?.uppercase() ?: ""
        val srcTarget = arguments["source_target"]?.toString() ?: ""
        val dstTypeStr = arguments["dest_type"]?.toString()?.uppercase() ?: ""
        val dstTarget = arguments["dest_target"]?.toString() ?: ""

        val srcLocation = try { TransferLocation.valueOf(srcTypeStr) } catch (_: Exception) { TransferLocation.LOCAL_STORAGE }
        val dstLocation = try { TransferLocation.valueOf(dstTypeStr) } catch (_: Exception) { TransferLocation.LOCAL_STORAGE }

        val job = TransferJob(srcLocation, srcTarget, dstLocation, dstTarget)
        val res = UniversalFileTransferEngine.transfer(context, gitHubService, GoogleDriveService(context), job)

        return if (res.success) {
            ToolResult.success(data = mapOf("bytes" to res.bytesTransferred), summary = res.summary)
        } else {
            ToolResult.failure(res.error ?: "TRANSFER_ERROR", res.summary)
        }
    }
}
