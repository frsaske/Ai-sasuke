package com.example.agent.tools.file

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.util.LocalWorkspaceManager

// --- 1. LOCAL CREATE FILE ---
class LocalCreateFileTool(private val context: Context) : Tool {
    override val name: String = "local_create_file"
    override val description: String =
        "Create a new file in the local workspace directory on this device. Requires file path (e.g. 'notes/todo.txt') and content."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "path" to mapOf("type" to "STRING", "description" to "File path or name (e.g. 'script.py' or 'notes/math_ch2.md')"),
            "content" to mapOf("type" to "STRING", "description" to "Text content to write into the file"),
            "overwrite" to mapOf("type" to "BOOLEAN", "description" to "Whether to overwrite if file already exists (default false)")
        ),
        "required" to listOf("path", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val overwrite = (arguments["overwrite"] as? Boolean) ?: false

        if (path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "File path is required.")
        }

        return LocalWorkspaceManager.createFile(context, path, content, overwrite).fold(
            onSuccess = { item ->
                ToolResult.success(
                    data = mapOf(
                        "name" to item.name,
                        "path" to item.relativePath,
                        "size" to item.formattedSize,
                        "modified" to item.formattedDate
                    ),
                    summary = "Created local file '${item.relativePath}' (${item.formattedSize})"
                )
            },
            onFailure = { ToolResult.failure("FILE_ERROR", it.localizedMessage ?: "Failed to create local file") }
        )
    }
}

// --- 2. LOCAL READ FILE ---
class LocalReadFileTool(private val context: Context) : Tool {
    override val name: String = "local_read_file"
    override val description: String =
        "Read the text content of a file stored in the local workspace on this device."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "path" to mapOf("type" to "STRING", "description" to "File path or name to read (e.g. 'notes/math_ch2.md')")
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val path = arguments["path"]?.toString()?.trim() ?: ""
        if (path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "File path is required.")
        }

        return LocalWorkspaceManager.readFile(context, path).fold(
            onSuccess = { content ->
                val truncated = if (content.length > 15000) {
                    content.take(15000) + "\n... [Truncated: ${content.length} chars total]"
                } else content

                ToolResult.success(
                    data = mapOf(
                        "path" to path,
                        "content" to truncated,
                        "length" to content.length
                    ),
                    summary = "Read '$path' (${content.length} characters)"
                )
            },
            onFailure = { ToolResult.failure("FILE_ERROR", it.localizedMessage ?: "Failed to read local file") }
        )
    }
}

// --- 3. LOCAL EDIT / UPDATE FILE ---
class LocalEditFileTool(private val context: Context) : Tool {
    override val name: String = "local_edit_file"
    override val description: String =
        "Update or append text content to an existing local workspace file on this device."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "path" to mapOf("type" to "STRING", "description" to "File path to edit"),
            "content" to mapOf("type" to "STRING", "description" to "New or additional text content"),
            "append" to mapOf("type" to "BOOLEAN", "description" to "If true, appends content to the end of the file; if false, replaces content (default false)")
        ),
        "required" to listOf("path", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val append = (arguments["append"] as? Boolean) ?: false

        if (path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "File path is required.")
        }

        return LocalWorkspaceManager.editFile(context, path, content, append).fold(
            onSuccess = { item ->
                val actionVerb = if (append) "Appended to" else "Updated"
                ToolResult.success(
                    data = mapOf(
                        "name" to item.name,
                        "path" to item.relativePath,
                        "size" to item.formattedSize,
                        "modified" to item.formattedDate
                    ),
                    summary = "$actionVerb local file '${item.relativePath}' (${item.formattedSize})"
                )
            },
            onFailure = { ToolResult.failure("FILE_ERROR", it.localizedMessage ?: "Failed to edit local file") }
        )
    }
}

// --- 4. LOCAL DELETE FILE ---
class LocalDeleteFileTool(private val context: Context) : Tool {
    override val name: String = "local_delete_file"
    override val description: String =
        "Permanently delete a file or directory from the local workspace on this device. Destructive action."

    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "path" to mapOf("type" to "STRING", "description" to "File or directory path to delete")
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val path = arguments["path"]?.toString()?.trim() ?: ""
        if (path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "File path is required.")
        }

        return LocalWorkspaceManager.deleteFile(context, path).fold(
            onSuccess = {
                ToolResult.success(
                    data = mapOf("path" to path, "deleted" to true),
                    summary = "Deleted local file/directory '$path'"
                )
            },
            onFailure = { ToolResult.failure("FILE_ERROR", it.localizedMessage ?: "Failed to delete file") }
        )
    }
}

// --- 5. LOCAL LIST FILES ---
class LocalListFilesTool(private val context: Context) : Tool {
    override val name: String = "local_list_files"
    override val description: String =
        "List all files and subdirectories stored in the local workspace on this device."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "directory" to mapOf("type" to "STRING", "description" to "Optional subdirectory to list (leave blank for workspace root)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val subDir = arguments["directory"]?.toString()?.trim() ?: ""

        return LocalWorkspaceManager.listFiles(context, subDir).fold(
            onSuccess = { list ->
                val summaryList = list.map {
                    mapOf(
                        "name" to it.name,
                        "path" to it.relativePath,
                        "size" to it.formattedSize,
                        "is_dir" to it.isDirectory,
                        "modified" to it.formattedDate
                    )
                }
                ToolResult.success(
                    data = mapOf("files" to summaryList, "count" to list.size),
                    summary = "Found ${list.size} files in local workspace"
                )
            },
            onFailure = { ToolResult.failure("FILE_ERROR", it.localizedMessage ?: "Failed to list local files") }
        )
    }
}
