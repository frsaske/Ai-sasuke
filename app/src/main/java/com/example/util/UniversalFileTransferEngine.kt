package com.example.util

import android.content.Context
import android.net.Uri
import com.example.agent.tools.github.GitHubService
import com.example.drive.GoogleDriveService
import com.example.storage.LocalStorageManager
import com.example.termux.TermuxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class TransferLocation {
    LOCAL_STORAGE,
    TERMUX,
    GITHUB,
    GOOGLE_DRIVE,
    GMAIL
}

data class TransferJob(
    val sourceLocation: TransferLocation,
    val sourcePathOrId: String,
    val targetLocation: TransferLocation,
    val targetPathOrId: String,
    val options: Map<String, Any?> = emptyMap()
)

data class TransferResult(
    val success: Boolean,
    val summary: String,
    val bytesTransferred: Long,
    val error: String? = null
)

object UniversalFileTransferEngine {

    suspend fun transfer(
        context: Context,
        gitHubService: GitHubService,
        driveService: GoogleDriveService,
        job: TransferJob
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
            when {
                // 1. Local Storage -> Google Drive
                job.sourceLocation == TransferLocation.LOCAL_STORAGE && job.targetLocation == TransferLocation.GOOGLE_DRIVE -> {
                    val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                    val localFile = if (File(job.sourcePathOrId).isAbsolute) File(job.sourcePathOrId) else File(ws, job.sourcePathOrId)
                    if (!localFile.exists()) {
                        return@withContext TransferResult(false, "Source file not found", 0, "FILE_NOT_FOUND")
                    }
                    val item = driveService.uploadFile(localFile, parentFolderId = job.targetPathOrId.takeIf { it != "root" })
                    TransferResult(true, "Uploaded '${item.name}' to Drive", localFile.length())
                }

                // 2. Google Drive -> Local Storage
                job.sourceLocation == TransferLocation.GOOGLE_DRIVE && job.targetLocation == TransferLocation.LOCAL_STORAGE -> {
                    val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                    val destFile = File(ws, job.targetPathOrId)
                    val downloaded = driveService.downloadToFile(job.sourcePathOrId, destFile)
                    LocalStorageManager.indexStorage(context)
                    TransferResult(true, "Downloaded Drive file to '${downloaded.name}'", downloaded.length())
                }

                // 3. Local Storage -> GitHub
                job.sourceLocation == TransferLocation.LOCAL_STORAGE && job.targetLocation == TransferLocation.GITHUB -> {
                    val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                    val localFile = if (File(job.sourcePathOrId).isAbsolute) File(job.sourcePathOrId) else File(ws, job.sourcePathOrId)
                    if (!localFile.exists()) {
                        return@withContext TransferResult(false, "Local file not found", 0, "FILE_NOT_FOUND")
                    }
                    val parts = job.targetPathOrId.split(":", limit = 2)
                    val repo = parts[0]
                    val remotePath = parts.getOrNull(1) ?: localFile.name
                    val repoParts = repo.split("/")
                    val owner = repoParts[0]
                    val repoName = repoParts[1]
                    val contentBase64 = android.util.Base64.encodeToString(localFile.readBytes(), android.util.Base64.NO_WRAP)

                    val res = gitHubService.createOrUpdateBinaryFile(
                        owner = owner,
                        repo = repoName,
                        path = remotePath,
                        base64Content = contentBase64,
                        message = "Upload ${localFile.name} via SasukeX"
                    )
                    if (res.isSuccess) {
                        TransferResult(true, "Uploaded '${localFile.name}' to GitHub ($repo/$remotePath)", localFile.length())
                    } else {
                        TransferResult(false, "GitHub upload failed: ${res.exceptionOrNull()?.message}", 0, "GITHUB_FAIL")
                    }
                }

                // 4. GitHub -> Local Storage
                job.sourceLocation == TransferLocation.GITHUB && job.targetLocation == TransferLocation.LOCAL_STORAGE -> {
                    val parts = job.sourcePathOrId.split(":", limit = 2)
                    val repo = parts[0]
                    val remotePath = parts.getOrNull(1) ?: ""
                    val repoParts = repo.split("/")
                    val owner = repoParts[0]
                    val repoName = repoParts[1]

                    val res = gitHubService.downloadRawFile(owner, repoName, remotePath)
                    if (res.isSuccess) {
                        val bytes = res.getOrThrow()
                        val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                        val fileName = remotePath.substringAfterLast('/')
                        val destFile = File(ws, job.targetPathOrId.ifBlank { fileName })
                        destFile.parentFile?.mkdirs()
                        destFile.writeBytes(bytes)
                        LocalStorageManager.indexStorage(context)
                        TransferResult(true, "Downloaded '$remotePath' from GitHub to '${destFile.name}'", bytes.size.toLong())
                    } else {
                        TransferResult(false, "GitHub file fetch failed: ${res.exceptionOrNull()?.message}", 0, "GITHUB_FAIL")
                    }
                }

                // 5. Termux -> Local Workspace
                job.sourceLocation == TransferLocation.TERMUX && job.targetLocation == TransferLocation.LOCAL_STORAGE -> {
                    val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                    val destFile = File(ws, job.targetPathOrId)
                    val cpRes = TermuxExecutor.execute(context, "cp '${job.sourcePathOrId}' '${destFile.absolutePath}'")
                    if (cpRes.success) {
                        LocalStorageManager.indexStorage(context)
                        TransferResult(true, "Copied from Termux to local workspace", destFile.length())
                    } else {
                        TransferResult(false, cpRes.stderr, 0, "TERMUX_FAIL")
                    }
                }

                // Default / Fallback
                else -> {
                    TransferResult(false, "Direct transfer from ${job.sourceLocation} to ${job.targetLocation} not supported.", 0, "UNSUPPORTED")
                }
            }
        } catch (e: Exception) {
            TransferResult(false, e.localizedMessage ?: "Transfer failed", 0, "EXCEPTION")
        }
    }
}
