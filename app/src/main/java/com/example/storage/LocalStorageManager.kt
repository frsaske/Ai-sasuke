package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.SasukeXDatabase
import com.example.data.model.FileIndexEntity
import com.example.util.LocalWorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

data class AttachedFileInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val parentFolder: String? = null
)

object LocalStorageManager {

    private val _lastAttachedFile = MutableStateFlow<AttachedFileInfo?>(null)
    val lastAttachedFile: StateFlow<AttachedFileInfo?> = _lastAttachedFile.asStateFlow()

    fun setLastAttachedFile(info: AttachedFileInfo?) {
        _lastAttachedFile.value = info
    }

    suspend fun indexStorage(context: Context): Int = withContext(Dispatchers.IO) {
        val database = SasukeXDatabase.getInstance(context)
        val dao = database.fileIndexDao()
        val indexedList = mutableListOf<FileIndexEntity>()

        // 1. Index local internal workspace
        val workspace = LocalWorkspaceManager.getWorkspaceDir(context)
        indexLocalDirectory(workspace, workspace, indexedList)

        // 2. Index SAF Persisted Roots if granted
        val resolver = context.contentResolver
        val persistedUris = resolver.persistedUriPermissions

        for (perm in persistedUris) {
            if (perm.isReadPermission) {
                try {
                    val rootDoc = DocumentFile.fromTreeUri(context, perm.uri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        indexDocumentTree(context, rootDoc, rootDoc.name ?: "Root", indexedList)
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Save into Room database
        dao.clearAll()
        if (indexedList.isNotEmpty()) {
            dao.insertAll(indexedList)
        }
        indexedList.size
    }

    private fun indexLocalDirectory(dir: File, root: File, result: MutableList<FileIndexEntity>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                indexLocalDirectory(f, root, result)
            } else {
                val relPath = try { f.relativeTo(root).path } catch (_: Exception) { f.name }
                val ext = f.extension.lowercase(Locale.ROOT)
                result.add(
                    FileIndexEntity(
                        name = f.name,
                        normalizedName = f.name.lowercase(Locale.ROOT),
                        extension = ext,
                        parentFolder = f.parentFile?.name ?: "workspace",
                        uriString = Uri.fromFile(f).toString(),
                        size = f.length(),
                        lastModified = f.lastModified(),
                        mimeType = getMimeType(f.name),
                        displayPath = relPath
                    )
                )
            }
        }
    }

    private fun indexDocumentTree(
        context: Context,
        dir: DocumentFile,
        currentPath: String,
        result: MutableList<FileIndexEntity>
    ) {
        val files = dir.listFiles()
        for (doc in files) {
            val name = doc.name ?: continue
            val docPath = "$currentPath/$name"
            if (doc.isDirectory) {
                indexDocumentTree(context, doc, docPath, result)
            } else {
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                result.add(
                    FileIndexEntity(
                        name = name,
                        normalizedName = name.lowercase(Locale.ROOT),
                        extension = ext,
                        parentFolder = dir.name ?: currentPath,
                        uriString = doc.uri.toString(),
                        size = doc.length(),
                        lastModified = doc.lastModified(),
                        mimeType = doc.type ?: getMimeType(name),
                        displayPath = docPath
                    )
                )
            }
        }
    }

    suspend fun searchFiles(context: Context, query: String): List<FileSearchResult> = withContext(Dispatchers.IO) {
        val database = SasukeXDatabase.getInstance(context)
        val dao = database.fileIndexDao()
        var all = dao.getAll(2000)

        // If empty index, auto-index workspace
        if (all.isEmpty()) {
            indexStorage(context)
            all = dao.getAll(2000)
        }

        DeterministicFileSearcher.search(all, query)
    }

    suspend fun readFile(
        context: Context,
        target: String,
        lineRange: String? = null,
        lastLines: Int? = null,
        maxBytes: Long = 64_000
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val uri = resolveUri(context, target)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for target: $target"))

            val lines = mutableListOf<String>()
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }

            val totalLines = lines.size
            val selectedLines: List<String>

            if (lastLines != null && lastLines > 0) {
                selectedLines = lines.takeLast(lastLines)
            } else if (!lineRange.isNullOrBlank()) {
                val parts = lineRange.split("-")
                val start = (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                val end = (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: totalLines).coerceAtMost(totalLines)
                selectedLines = if (start in lines.indices && end >= start) {
                    lines.subList(start, end)
                } else lines
            } else {
                // If entire file, limit lines to safe window to prevent token explosion
                selectedLines = if (lines.size > 250) lines.take(250) else lines
            }

            val joinedContent = selectedLines.joinToString("\n")
            val truncated = if (joinedContent.length > maxBytes) {
                joinedContent.take(maxBytes.toInt()) + "\n... [Truncated at $maxBytes bytes]"
            } else joinedContent

            Result.success(
                mapOf(
                    "target" to target,
                    "total_lines" to totalLines,
                    "returned_lines" to selectedLines.size,
                    "content" to truncated
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(
        context: Context,
        folderOrPath: String,
        fileName: String? = null,
        content: String = "",
        mimeType: String = "text/plain",
        overwrite: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (folderOrPath.startsWith("content://")) {
                val treeUri = Uri.parse(folderOrPath)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@withContext Result.failure(Exception("Cannot access folder: $folderOrPath"))
                val name = fileName ?: "new_file.txt"
                val existing = treeDoc.findFile(name)
                if (existing != null && !overwrite) {
                    return@withContext Result.failure(Exception("File '$name' already exists."))
                }
                if (existing != null && overwrite) {
                    existing.delete()
                }
                val newFile = treeDoc.createFile(mimeType, name)
                    ?: return@withContext Result.failure(Exception("Failed to create document file"))
                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                indexStorage(context)
                Result.success(newFile.uri.toString())
            } else {
                // Local workspace
                val targetPath = if (fileName != null) "$folderOrPath/$fileName".trimStart('/') else folderOrPath
                val res = LocalWorkspaceManager.createFile(context, targetPath, content, overwrite)
                indexStorage(context)
                res.map { it.absolutePath }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(
        context: Context,
        target: String,
        content: String,
        append: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uri = resolveUri(context, target)
            val mode = if (append) "wa" else "wt"
            val outputStream = context.contentResolver.openOutputStream(uri, mode)
                ?: return@withContext Result.failure(Exception("Cannot open write stream for $target"))

            outputStream.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            indexStorage(context)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyFile(
        context: Context,
        source: String,
        destinationFolder: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val srcUri = resolveUri(context, source)
            val srcName = resolveFileName(context, srcUri)
            val inputStream = context.contentResolver.openInputStream(srcUri)
                ?: return@withContext Result.failure(Exception("Cannot read source: $source"))

            val content = inputStream.use { it.readBytes() }

            if (destinationFolder.startsWith("content://")) {
                val treeUri = Uri.parse(destinationFolder)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@withContext Result.failure(Exception("Cannot access destination tree"))
                val targetDoc = treeDoc.createFile(getMimeType(srcName), srcName)
                    ?: return@withContext Result.failure(Exception("Failed to create target file in tree"))
                context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                    out.write(content)
                }
                indexStorage(context)
                Result.success(targetDoc.uri.toString())
            } else {
                val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                val destDir = if (destinationFolder.isNotBlank()) File(ws, destinationFolder) else ws
                destDir.mkdirs()
                val targetFile = File(destDir, srcName)
                targetFile.writeBytes(content)
                indexStorage(context)
                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveFile(
        context: Context,
        source: String,
        destinationFolder: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val copyRes = copyFile(context, source, destinationFolder)
        if (copyRes.isSuccess) {
            deleteFile(context, source)
            copyRes
        } else {
            copyRes
        }
    }

    suspend fun deleteFile(
        context: Context,
        target: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (target.startsWith("content://")) {
                val uri = Uri.parse(target)
                val doc = DocumentFile.fromSingleUri(context, uri)
                val deleted = doc?.delete() == true || context.contentResolver.delete(uri, null, null) > 0
                indexStorage(context)
                Result.success(deleted)
            } else {
                val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                val clean = target.removePrefix("file://").trimStart('/', '\\')
                val f = if (File(target).isAbsolute) File(target) else File(ws, clean)
                val deleted = f.delete()
                indexStorage(context)
                Result.success(deleted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(
        context: Context,
        target: String,
        newName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (target.startsWith("content://")) {
                val uri = Uri.parse(target)
                val renamed = DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
                indexStorage(context)
                Result.success(renamed)
            } else {
                val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                val clean = target.removePrefix("file://").trimStart('/', '\\')
                val f = if (File(target).isAbsolute) File(target) else File(ws, clean)
                val dest = File(f.parentFile, newName)
                val renamed = f.renameTo(dest)
                indexStorage(context)
                Result.success(renamed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(
        context: Context,
        parentFolder: String,
        folderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (parentFolder.startsWith("content://")) {
                val treeUri = Uri.parse(parentFolder)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@withContext Result.failure(Exception("Cannot access parent folder tree"))
                val dir = treeDoc.createDirectory(folderName)
                    ?: return@withContext Result.failure(Exception("Failed to create directory in tree"))
                indexStorage(context)
                Result.success(dir.uri.toString())
            } else {
                val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                val p = if (parentFolder.isNotBlank()) File(ws, parentFolder) else ws
                val newDir = File(p, folderName)
                newDir.mkdirs()
                indexStorage(context)
                Result.success(newDir.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFolder(
        context: Context,
        folder: String? = null
    ): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<Map<String, Any?>>()
            if (folder != null && folder.startsWith("content://")) {
                val treeUri = Uri.parse(folder)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                treeDoc?.listFiles()?.forEach { doc ->
                    list.add(
                        mapOf(
                            "name" to (doc.name ?: ""),
                            "is_directory" to doc.isDirectory,
                            "size" to doc.length(),
                            "modified" to doc.lastModified(),
                            "uri" to doc.uri.toString()
                        )
                    )
                }
            } else {
                val ws = LocalWorkspaceManager.getWorkspaceDir(context)
                val targetDir = if (!folder.isNullOrBlank()) File(ws, folder) else ws
                targetDir.listFiles()?.forEach { f ->
                    list.add(
                        mapOf(
                            "name" to f.name,
                            "is_directory" to f.isDirectory,
                            "size" to f.length(),
                            "modified" to f.lastModified(),
                            "path" to f.relativeTo(ws).path
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveUri(context: Context, target: String): Uri {
        return if (target.startsWith("content://")) {
            Uri.parse(target)
        } else if (target.startsWith("file://")) {
            Uri.parse(target)
        } else {
            val ws = LocalWorkspaceManager.getWorkspaceDir(context)
            val clean = target.trimStart('/', '\\')
            val file = if (File(target).isAbsolute) File(target) else File(ws, clean)
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        val doc = DocumentFile.fromSingleUri(context, uri)
        return doc?.name ?: "file_${System.currentTimeMillis()}"
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "txt", "md", "csv", "log" -> "text/plain"
            "json" -> "application/json"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "html" -> "text/html"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
