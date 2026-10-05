package com.example.agent.tools.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.storage.LocalStorageManager

// --- 1. LOCAL FILE SEARCH (Deterministic, Zero Gemini Prompt Waste) ---
class LocalFileSearchTool(private val context: Context) : Tool {
    override val name: String = "local_file_search"
    override val description: String =
        "Search files on the device using deterministic local indexing and fuzzy ranking. Bypasses AI tokens. Returns compact metadata matches (name, folder, size, modified)."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "STRING",
                "description" to "Filename, prefix, or search terms (e.g. 'sasuke.py', 'python', 'README')"
            )
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val query = arguments["query"]?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Search query is required.")
        }

        val results = LocalStorageManager.searchFiles(context, query)
        val compactMatches = results.map { match ->
            mapOf(
                "name" to match.name,
                "folder" to match.folder,
                "size" to match.size,
                "modified" to match.modified,
                "uri" to match.uri,
                "score" to match.score,
                "match_type" to match.matchType
            )
        }

        return ToolResult.success(
            data = mapOf(
                "query" to query,
                "count" to compactMatches.size,
                "matches" to compactMatches
            ),
            summary = if (compactMatches.isNotEmpty()) {
                "Found ${compactMatches.size} match(es) for '$query'. Top: ${compactMatches.first()["name"]}"
            } else {
                "No local files matched '$query'."
            }
        )
    }
}

// --- 2. LOCAL FILE READ ---
class StorageFileReadTool(private val context: Context) : Tool {
    override val name: String = "local_file_read"
    override val description: String =
        "Read contents of a local file by path or URI. Supports line ranges (e.g. '1-50') or last_lines (e.g. 100) to minimize tokens."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "target" to mapOf("type" to "STRING", "description" to "File path or content URI"),
            "line_range" to mapOf("type" to "STRING", "description" to "Optional range of lines (e.g. '1-100')"),
            "last_lines" to mapOf("type" to "INTEGER", "description" to "Optional number of ending lines (e.g. 50)")
        ),
        "required" to listOf("target")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val target = arguments["target"]?.toString()?.trim() ?: ""
        if (target.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "File target is required.")
        }
        val lineRange = arguments["line_range"]?.toString()
        val lastLines = (arguments["last_lines"] as? Number)?.toInt()

        return LocalStorageManager.readFile(context, target, lineRange, lastLines).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res,
                    summary = "Read ${res["returned_lines"]} of ${res["total_lines"]} lines from $target"
                )
            },
            onFailure = { ToolResult.failure("FILE_READ_ERROR", it.localizedMessage ?: "Failed to read file") }
        )
    }
}

// --- 3. LOCAL FILE CREATE ---
class StorageFileCreateTool(private val context: Context) : Tool {
    override val name: String = "local_file_create"
    override val description: String =
        "Create a new file in a local folder or SAF directory. Takes folder path/URI, file_name, and text content."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "folder" to mapOf("type" to "STRING", "description" to "Folder path or tree URI (e.g. 'notes' or workspace)"),
            "file_name" to mapOf("type" to "STRING", "description" to "Name of the new file (e.g. 'todo.txt')"),
            "content" to mapOf("type" to "STRING", "description" to "File text content"),
            "overwrite" to mapOf("type" to "BOOLEAN", "description" to "Whether to overwrite existing file (default false)")
        ),
        "required" to listOf("folder", "file_name", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val folder = arguments["folder"]?.toString()?.trim() ?: ""
        val fileName = arguments["file_name"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val overwrite = (arguments["overwrite"] as? Boolean) ?: false

        if (folder.isEmpty() || fileName.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Folder and file_name are required.")
        }

        return LocalStorageManager.createFile(context, folder, fileName, content, overwrite = overwrite).fold(
            onSuccess = { uriOrPath ->
                ToolResult.success(
                    data = mapOf("target" to uriOrPath, "file_name" to fileName),
                    summary = "Created file '$fileName' in $folder"
                )
            },
            onFailure = { ToolResult.failure("CREATE_ERROR", it.localizedMessage ?: "Failed to create file") }
        )
    }
}

// --- 4. LOCAL FILE WRITE / EDIT ---
class StorageFileWriteTool(private val context: Context) : Tool {
    override val name: String = "local_file_write"
    override val description: String =
        "Write or append text content to an existing local file."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "target" to mapOf("type" to "STRING", "description" to "File path or URI to write to"),
            "content" to mapOf("type" to "STRING", "description" to "Text content to write"),
            "append" to mapOf("type" to "BOOLEAN", "description" to "If true, appends content instead of overwriting")
        ),
        "required" to listOf("target", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val target = arguments["target"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val append = (arguments["append"] as? Boolean) ?: false

        return LocalStorageManager.writeFile(context, target, content, append).fold(
            onSuccess = {
                ToolResult.success(
                    data = mapOf("target" to target, "appended" to append),
                    summary = if (append) "Appended to $target" else "Overwrote $target"
                )
            },
            onFailure = { ToolResult.failure("WRITE_ERROR", it.localizedMessage ?: "Failed to write file") }
        )
    }
}

// --- 5. LOCAL FILE COPY ---
class StorageFileCopyTool(private val context: Context) : Tool {
    override val name: String = "local_file_copy"
    override val description: String =
        "Copy a file from one local path or URI to a destination folder."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "source" to mapOf("type" to "STRING", "description" to "Source file path or URI"),
            "destination_folder" to mapOf("type" to "STRING", "description" to "Target folder path or URI")
        ),
        "required" to listOf("source", "destination_folder")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val source = arguments["source"]?.toString()?.trim() ?: ""
        val dest = arguments["destination_folder"]?.toString()?.trim() ?: ""

        return LocalStorageManager.copyFile(context, source, dest).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = mapOf("source" to source, "destination" to res),
                    summary = "Copied file to $res"
                )
            },
            onFailure = { ToolResult.failure("COPY_ERROR", it.localizedMessage ?: "Failed to copy file") }
        )
    }
}

// --- 6. LOCAL FILE MOVE ---
class StorageFileMoveTool(private val context: Context) : Tool {
    override val name: String = "local_file_move"
    override val description: String =
        "Move a file from one local path or URI to a destination folder."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "source" to mapOf("type" to "STRING", "description" to "Source file path or URI"),
            "destination_folder" to mapOf("type" to "STRING", "description" to "Destination folder path or URI")
        ),
        "required" to listOf("source", "destination_folder")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val source = arguments["source"]?.toString()?.trim() ?: ""
        val dest = arguments["destination_folder"]?.toString()?.trim() ?: ""

        return LocalStorageManager.moveFile(context, source, dest).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = mapOf("source" to source, "destination" to res),
                    summary = "Moved file to $res"
                )
            },
            onFailure = { ToolResult.failure("MOVE_ERROR", it.localizedMessage ?: "Failed to move file") }
        )
    }
}

// --- 7. LOCAL FILE RENAME ---
class StorageFileRenameTool(private val context: Context) : Tool {
    override val name: String = "local_file_rename"
    override val description: String = "Rename a local file or document."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "target" to mapOf("type" to "STRING", "description" to "File path or URI to rename"),
            "new_name" to mapOf("type" to "STRING", "description" to "New file name with extension")
        ),
        "required" to listOf("target", "new_name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val target = arguments["target"]?.toString()?.trim() ?: ""
        val newName = arguments["new_name"]?.toString()?.trim() ?: ""

        return LocalStorageManager.renameFile(context, target, newName).fold(
            onSuccess = {
                ToolResult.success(
                    data = mapOf("target" to target, "new_name" to newName),
                    summary = "Renamed file to $newName"
                )
            },
            onFailure = { ToolResult.failure("RENAME_ERROR", it.localizedMessage ?: "Failed to rename file") }
        )
    }
}

// --- 8. LOCAL FILE DELETE ---
class StorageFileDeleteTool(private val context: Context) : Tool {
    override val name: String = "local_file_delete"
    override val description: String =
        "Delete a local file permanently. Requires user confirmation."

    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE
    override val requiresConfirmation: Boolean = true

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "target" to mapOf("type" to "STRING", "description" to "File path or URI to delete")
        ),
        "required" to listOf("target")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val target = arguments["target"]?.toString()?.trim() ?: ""

        return LocalStorageManager.deleteFile(context, target).fold(
            onSuccess = {
                ToolResult.success(
                    data = mapOf("deleted" to true, "target" to target),
                    summary = "Deleted local file: $target"
                )
            },
            onFailure = { ToolResult.failure("DELETE_ERROR", it.localizedMessage ?: "Failed to delete file") }
        )
    }
}

// --- 9. LOCAL FOLDER CREATE ---
class StorageFolderCreateTool(private val context: Context) : Tool {
    override val name: String = "local_folder_create"
    override val description: String = "Create a new folder in local workspace or document tree."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "parent_folder" to mapOf("type" to "STRING", "description" to "Parent folder path or tree URI (default root workspace)"),
            "folder_name" to mapOf("type" to "STRING", "description" to "Name of new folder")
        ),
        "required" to listOf("folder_name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val parent = arguments["parent_folder"]?.toString()?.trim() ?: ""
        val name = arguments["folder_name"]?.toString()?.trim() ?: ""

        if (name.isEmpty()) return ToolResult.failure("INVALID_ARGUMENT", "Folder name is required.")

        return LocalStorageManager.createFolder(context, parent, name).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = mapOf("folder_name" to name, "path" to res),
                    summary = "Created folder: $name"
                )
            },
            onFailure = { ToolResult.failure("FOLDER_CREATE_ERROR", it.localizedMessage ?: "Failed to create folder") }
        )
    }
}

// --- 10. LOCAL FOLDER LIST ---
class StorageFolderListTool(private val context: Context) : Tool {
    override val name: String = "local_folder_list"
    override val description: String = "List files and subfolders in a local folder or tree URI."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "folder" to mapOf("type" to "STRING", "description" to "Folder path or tree URI (leave empty for workspace root)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val folder = arguments["folder"]?.toString()?.trim()

        return LocalStorageManager.listFolder(context, folder).fold(
            onSuccess = { items ->
                ToolResult.success(
                    data = mapOf("folder" to (folder ?: "root"), "count" to items.size, "items" to items.take(100)),
                    summary = "Found ${items.size} item(s) in ${folder ?: "workspace root"}"
                )
            },
            onFailure = { ToolResult.failure("LIST_ERROR", it.localizedMessage ?: "Failed to list folder") }
        )
    }
}

// --- 11. LOCAL FILE OPEN (System Intent) ---
class StorageFileOpenTool(private val context: Context) : Tool {
    override val name: String = "local_file_open"
    override val description: String = "Open a local file in an external viewer or default Android app."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "target" to mapOf("type" to "STRING", "description" to "File path or content URI to open")
        ),
        "required" to listOf("target")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val target = arguments["target"]?.toString()?.trim() ?: ""
        return try {
            val uri = if (target.startsWith("content://") || target.startsWith("file://")) {
                Uri.parse(target)
            } else {
                val f = java.io.File(target)
                if (f.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                } else {
                    return ToolResult.failure("FILE_NOT_FOUND", "File does not exist: $target")
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            ToolResult.success(
                data = mapOf("opened" to true, "target" to target),
                summary = "Opened file $target in external app"
            )
        } catch (e: Exception) {
            ToolResult.failure("OPEN_ERROR", e.localizedMessage ?: "Failed to open file in external app")
        }
    }
}
