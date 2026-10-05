package com.example.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalFileItem(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean
) {
    val formattedSize: String
        get() = FileTransferManager.formatFileSize(sizeBytes)

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))
}

object LocalWorkspaceManager {

    private const val WORKSPACE_DIR = "workspace"

    fun getWorkspaceDir(context: Context): File {
        val dir = File(context.filesDir, WORKSPACE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createFile(
        context: Context,
        relativePath: String,
        content: String,
        overwrite: Boolean = false
    ): Result<LocalFileItem> {
        return try {
            val root = getWorkspaceDir(context)
            val cleanPath = relativePath.trimStart('/', '\\')
            val file = File(root, cleanPath)

            // Prevent path traversal
            if (!file.canonicalPath.startsWith(root.canonicalPath)) {
                return Result.failure(IllegalArgumentException("Path outside workspace is not allowed."))
            }

            if (file.exists() && !overwrite) {
                return Result.failure(FileAlreadyExistsException(file, reason = "File already exists. Set overwrite=true to replace."))
            }

            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)

            Result.success(
                LocalFileItem(
                    name = file.name,
                    relativePath = file.relativeTo(root).path,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readFile(context: Context, relativePath: String): Result<String> {
        return try {
            val root = getWorkspaceDir(context)
            val cleanPath = relativePath.trimStart('/', '\\')
            val file = File(root, cleanPath)

            if (!file.canonicalPath.startsWith(root.canonicalPath)) {
                return Result.failure(IllegalArgumentException("Path outside workspace is not allowed."))
            }

            if (!file.exists()) {
                return Result.failure(NoSuchFileException(file, reason = "File not found in workspace."))
            }

            if (file.isDirectory) {
                return Result.failure(IllegalArgumentException("Target path is a directory, not a file."))
            }

            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun editFile(
        context: Context,
        relativePath: String,
        content: String,
        append: Boolean = false
    ): Result<LocalFileItem> {
        return try {
            val root = getWorkspaceDir(context)
            val cleanPath = relativePath.trimStart('/', '\\')
            val file = File(root, cleanPath)

            if (!file.canonicalPath.startsWith(root.canonicalPath)) {
                return Result.failure(IllegalArgumentException("Path outside workspace is not allowed."))
            }

            if (!file.exists()) {
                // If doesn't exist, create it
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } else {
                if (append) {
                    file.appendText(content, Charsets.UTF_8)
                } else {
                    file.writeText(content, Charsets.UTF_8)
                }
            }

            Result.success(
                LocalFileItem(
                    name = file.name,
                    relativePath = file.relativeTo(root).path,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteFile(context: Context, relativePath: String): Result<Boolean> {
        return try {
            val root = getWorkspaceDir(context)
            val cleanPath = relativePath.trimStart('/', '\\')
            val file = File(root, cleanPath)

            if (!file.canonicalPath.startsWith(root.canonicalPath)) {
                return Result.failure(IllegalArgumentException("Path outside workspace is not allowed."))
            }

            if (!file.exists()) {
                return Result.failure(NoSuchFileException(file, reason = "File not found."))
            }

            val deleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }

            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listFiles(context: Context, subDir: String = ""): Result<List<LocalFileItem>> {
        return try {
            val root = getWorkspaceDir(context)
            val targetDir = if (subDir.isBlank()) root else File(root, subDir.trimStart('/', '\\'))

            if (!targetDir.canonicalPath.startsWith(root.canonicalPath)) {
                return Result.failure(IllegalArgumentException("Path outside workspace is not allowed."))
            }

            if (!targetDir.exists()) {
                return Result.success(emptyList())
            }

            val items = targetDir.walkTopDown().maxDepth(3).filter { it != targetDir }.map { f ->
                LocalFileItem(
                    name = f.name,
                    relativePath = f.relativeTo(root).path,
                    absolutePath = f.absolutePath,
                    sizeBytes = if (f.isDirectory) 0L else f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = f.isDirectory
                )
            }.toList()

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearWorkspace(context: Context): Boolean {
        val root = getWorkspaceDir(context)
        return root.deleteRecursively() && root.mkdirs()
    }
}
