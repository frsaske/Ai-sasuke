package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class AttachedFileInfo(
    val name: String,
    val localPath: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val formattedSize: String
        get() = FileTransferManager.formatFileSize(sizeBytes)
}

object FileTransferManager {

    const val MAX_ATTACHMENT_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB limit
    private const val DOWNLOADS_CHANNEL_ID = "sasukex_downloads"
    private const val DOWNLOADS_CHANNEL_NAME = "File Downloads & Transfers"

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py" -> "text/x-python"
            "json" -> "application/json"
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "js", "mjs" -> "application/javascript"
            "ts" -> "application/typescript"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "xml" -> "application/xml"
            "md" -> "text/markdown"
            "txt", "log", "env", "properties" -> "text/plain"
            "sh", "bash" -> "application/x-sh"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    /**
     * Copies a user-picked file from Uri to internal cache/attached_files.
     * Enforces the 5MB size limit.
     */
    fun saveAttachedFile(context: Context, uri: Uri): Result<AttachedFileInfo> {
        return try {
            val contentResolver = context.contentResolver

            // 1. Get original file name
            var fileName = "attachment_${System.currentTimeMillis()}"
            var fileSize = -1L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // 2. Validate file size
            if (fileSize > MAX_ATTACHMENT_SIZE_BYTES) {
                return Result.failure(
                    IllegalArgumentException(
                        "File exceeds 5MB limit (${formatFileSize(fileSize)}). Please select a file smaller than 5MB."
                    )
                )
            }

            val attachedDir = File(context.cacheDir, "attached_files").apply { mkdirs() }
            val destFile = File(attachedDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalCopied = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalCopied += bytesRead
                        if (totalCopied > MAX_ATTACHMENT_SIZE_BYTES) {
                            destFile.delete()
                            return Result.failure(
                                IllegalArgumentException("File exceeds 5MB limit. Please select a smaller file.")
                            )
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            } ?: return Result.failure(IllegalStateException("Could not open attachment stream."))

            val finalSize = destFile.length()
            val mimeType = getMimeType(fileName)

            Result.success(
                AttachedFileInfo(
                    name = fileName,
                    localPath = destFile.absolutePath,
                    sizeBytes = finalSize,
                    mimeType = mimeType
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads bytes from a cached attached file.
     */
    fun getAttachedFileBytes(context: Context, fileName: String): ByteArray? {
        val file = File(File(context.cacheDir, "attached_files"), fileName)
        return if (file.exists() && file.isFile) file.readBytes() else null
    }

    /**
     * Reads text from a cached attached file (if UTF-8 readable).
     */
    fun getAttachedFileText(context: Context, fileName: String): String? {
        val file = File(File(context.cacheDir, "attached_files"), fileName)
        return if (file.exists() && file.isFile) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    /**
     * Saves downloaded bytes to Android Downloads folder and external app storage.
     */
    fun saveDownloadedFile(context: Context, fileName: String, bytes: ByteArray): File {
        var targetFile: File? = null

        // 1. Write to public Downloads directory if possible
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
                val candidate = File(publicDownloads, fileName)
                candidate.writeBytes(bytes)
                targetFile = candidate
            }
        } catch (_: Exception) {}

        // 2. Also ensure a copy exists in app external files directory for 100% reliable FileProvider sharing
        val appDownloads = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "downloads").apply { mkdirs() }
        val fallbackFile = File(appDownloads, fileName)
        fallbackFile.writeBytes(bytes)

        val finalFile = targetFile ?: fallbackFile

        // MediaStore registration on Android 10+ so file appears immediately in system Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SasukeX")
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }
                }
            } catch (_: Exception) {}
        }

        return finalFile
    }

    /**
     * Posts a completion notification when a file is downloaded.
     */
    fun postDownloadNotification(context: Context, file: File) {
        try {
            ensureNotificationChannel(context)

            val openIntent = createOpenFileIntent(context, file)
            val pendingOpen = PendingIntent.getActivity(
                context,
                file.name.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shareIntent = createShareFileIntent(context, file)
            val pendingShare = PendingIntent.getActivity(
                context,
                file.name.hashCode() + 1,
                Intent.createChooser(shareIntent, "Share ${file.name}"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, DOWNLOADS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete: ${file.name}")
                .setContentText("${formatFileSize(file.length())} • Saved to Downloads")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingOpen)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_view, "Open", pendingOpen)
                .addAction(android.R.drawable.ic_menu_share, "Share", pendingShare)

            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(file.name.hashCode(), builder.build())
            }
        } catch (_: Exception) {}
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOADS_CHANNEL_ID,
                DOWNLOADS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for downloaded GitHub and web files"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Intent to open a downloaded file in an external app viewer.
     */
    fun createOpenFileIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = getMimeType(file.name)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent to share a file.
     */
    fun createShareFileIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = getMimeType(file.name)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun openFile(context: Context, file: File) {
        try {
            val intent = createOpenFileIntent(context, file)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val intent = createShareFileIntent(context, file)
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
