package com.example.agent.tools.drive

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.drive.GoogleDriveService
import com.example.util.LocalWorkspaceManager
import java.io.File

class DriveSearchTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_search"
    override val description: String = "Search files and documents in Google Drive by filename or query."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf("type" to "STRING", "description" to "Search keyword or filename (e.g. 'SasukeX', 'PDF', 'notes')")
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val query = arguments["query"]?.toString()?.trim() ?: ""
        if (query.isEmpty()) return ToolResult.failure("INVALID_ARGUMENT", "Query required.")

        return try {
            val files = driveService.searchFiles(query)
            val compact = files.map {
                mapOf("id" to it.id, "name" to it.name, "mime" to it.mimeType, "size" to it.size, "link" to it.webViewLink)
            }
            ToolResult.success(
                data = mapOf("query" to query, "count" to compact.size, "files" to compact),
                summary = "Found ${compact.size} file(s) in Drive for '$query'"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Drive search failed")
        }
    }
}

class DriveListFolderTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_list_folder"
    override val description: String = "List files and subfolders inside a specific Google Drive folder."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "folder_id" to mapOf("type" to "STRING", "description" to "Folder ID (use 'root' for Drive root)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val folderId = arguments["folder_id"]?.toString()?.trim() ?: "root"
        return try {
            val files = driveService.listFolder(folderId)
            val compact = files.map {
                mapOf("id" to it.id, "name" to it.name, "is_folder" to it.isFolder, "size" to it.size)
            }
            ToolResult.success(
                data = mapOf("folder_id" to folderId, "count" to compact.size, "files" to compact),
                summary = "Found ${compact.size} items in Drive folder"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to list Drive folder")
        }
    }
}

class DriveGetMetadataTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_get_metadata"
    override val description: String = "Get metadata details of a Google Drive file by ID."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID")
        ),
        "required" to listOf("file_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        return try {
            val meta = driveService.getMetadata(fileId)
            ToolResult.success(
                data = mapOf("id" to meta.id, "name" to meta.name, "mime" to meta.mimeType, "size" to meta.size, "link" to meta.webViewLink),
                summary = "Metadata for '${meta.name}' (${meta.mimeType})"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to get metadata")
        }
    }
}

class DriveReadTextTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_read_text"
    override val description: String = "Read plain text from a Google Drive text file or Google Doc."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID to read")
        ),
        "required" to listOf("file_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        return try {
            val content = driveService.readText(fileId)
            ToolResult.success(
                data = mapOf("file_id" to fileId, "length" to content.length, "content" to content),
                summary = "Read ${content.length} characters from Drive file"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to read Drive document")
        }
    }
}

class DriveDownloadTool(private val driveService: GoogleDriveService, private val context: Context) : Tool {
    override val name: String = "drive_download"
    override val description: String = "Download a file from Google Drive to local workspace or Download folder."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID"),
            "local_path" to mapOf("type" to "STRING", "description" to "Local file destination name/path")
        ),
        "required" to listOf("file_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        val localPath = arguments["local_path"]?.toString()?.trim()

        return try {
            val meta = driveService.getMetadata(fileId)
            val ws = LocalWorkspaceManager.getWorkspaceDir(context)
            val destFile = if (!localPath.isNullOrBlank()) File(ws, localPath) else File(ws, meta.name)
            val downloaded = driveService.downloadToFile(fileId, destFile)
            ToolResult.success(
                data = mapOf("file_id" to fileId, "local_path" to downloaded.absolutePath, "size" to downloaded.length()),
                summary = "Downloaded '${meta.name}' to ${downloaded.name}"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to download Drive file")
        }
    }
}

class DriveUploadTool(private val driveService: GoogleDriveService, private val context: Context) : Tool {
    override val name: String = "drive_upload"
    override val description: String = "Upload a local file to Google Drive directly without routing bytes through AI."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "local_path" to mapOf("type" to "STRING", "description" to "Local file path or filename in workspace"),
            "drive_name" to mapOf("type" to "STRING", "description" to "Optional target name on Drive"),
            "parent_folder_id" to mapOf("type" to "STRING", "description" to "Optional Drive folder ID")
        ),
        "required" to listOf("local_path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val localPath = arguments["local_path"]?.toString()?.trim() ?: ""
        val driveName = arguments["drive_name"]?.toString()?.trim()
        val parentId = arguments["parent_folder_id"]?.toString()?.trim()

        val ws = LocalWorkspaceManager.getWorkspaceDir(context)
        val file = if (File(localPath).isAbsolute) File(localPath) else File(ws, localPath)
        if (!file.exists()) {
            return ToolResult.failure("FILE_NOT_FOUND", "Local file not found: $localPath")
        }

        return try {
            val item = driveService.uploadFile(file, driveName, parentId)
            ToolResult.success(
                data = mapOf("drive_id" to item.id, "name" to item.name, "link" to item.webViewLink),
                summary = "Uploaded '${item.name}' to Drive"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to upload to Drive")
        }
    }
}

class DriveCreateFolderTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_create_folder"
    override val description: String = "Create a new folder in Google Drive."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "name" to mapOf("type" to "STRING", "description" to "Name of new folder"),
            "parent_folder_id" to mapOf("type" to "STRING", "description" to "Optional parent Drive folder ID")
        ),
        "required" to listOf("name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val name = arguments["name"]?.toString()?.trim() ?: ""
        val parent = arguments["parent_folder_id"]?.toString()?.trim()
        if (name.isEmpty()) return ToolResult.failure("INVALID_ARGUMENT", "Folder name is required.")

        return try {
            val f = driveService.createFolder(name, parent)
            ToolResult.success(
                data = mapOf("folder_id" to f.id, "name" to f.name),
                summary = "Created Drive folder: $name"
            )
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to create folder")
        }
    }
}

class DriveRenameTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_rename"
    override val description: String = "Rename a Google Drive file or folder."
    override val permission: ToolPermission = ToolPermission.WRITE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID"),
            "new_name" to mapOf("type" to "STRING", "description" to "New file name")
        ),
        "required" to listOf("file_id", "new_name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        val newName = arguments["new_name"]?.toString()?.trim() ?: ""

        return try {
            val ok = driveService.renameFile(fileId, newName)
            ToolResult.success(data = mapOf("renamed" to ok, "file_id" to fileId, "new_name" to newName), summary = "Renamed to $newName")
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to rename file")
        }
    }
}

class DriveTrashTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_trash"
    override val description: String = "Move a file in Google Drive to trash. Requires confirmation."
    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE
    override val requiresConfirmation: Boolean = true
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID to trash")
        ),
        "required" to listOf("file_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        return try {
            val ok = driveService.trashFile(fileId)
            ToolResult.success(data = mapOf("trashed" to ok, "file_id" to fileId), summary = "Moved file $fileId to trash")
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to trash file")
        }
    }
}

class DriveShareLinkTool(private val driveService: GoogleDriveService) : Tool {
    override val name: String = "drive_share_link"
    override val description: String = "Get a sharable view link for a Google Drive file."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_id" to mapOf("type" to "STRING", "description" to "Drive file ID")
        ),
        "required" to listOf("file_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileId = arguments["file_id"]?.toString()?.trim() ?: ""
        return try {
            val link = driveService.getShareLink(fileId)
            ToolResult.success(data = mapOf("file_id" to fileId, "link" to link), summary = "Share link: $link")
        } catch (e: Exception) {
            ToolResult.failure("DRIVE_ERROR", e.localizedMessage ?: "Failed to get share link")
        }
    }
}
