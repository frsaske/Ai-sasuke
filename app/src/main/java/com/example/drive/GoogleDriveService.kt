package com.example.drive

import android.content.Context
import com.example.data.local.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: String,
    val webViewLink: String?,
    val isFolder: Boolean
)

class GoogleDriveService(private val context: Context) {

    private val secureStorage = SecureStorageManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getToken(): String {
        return secureStorage.getDriveToken()
    }

    private fun checkToken(): String {
        val t = getToken()
        if (t.isBlank()) {
            throw IllegalStateException("Google Drive token not configured. Please connect Google Account or enter Google OAuth token in Settings.")
        }
        return t
    }

    suspend fun searchFiles(query: String, maxResults: Int = 15): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val token = checkToken()
        val escaped = query.replace("'", "\\'")
        val qParam = "name contains '$escaped' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(qParam, "UTF-8")}&fields=files(id,name,mimeType,size,modifiedTime,webViewLink)&pageSize=$maxResults"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Drive API Error: $err")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            parseFileList(json.optJSONArray("files"))
        }
    }

    suspend fun listFolder(folderId: String = "root", maxResults: Int = 30): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val token = checkToken()
        val qParam = "'$folderId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(qParam, "UTF-8")}&fields=files(id,name,mimeType,size,modifiedTime,webViewLink)&pageSize=$maxResults"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Drive API Error: $err")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            parseFileList(json.optJSONArray("files"))
        }
    }

    suspend fun getMetadata(fileId: String): DriveFileItem = withContext(Dispatchers.IO) {
        val token = checkToken()
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size,modifiedTime,webViewLink,parents"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Drive API Error: $err")
            }
            val obj = JSONObject(response.body?.string() ?: "{}")
            DriveFileItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                mimeType = obj.optString("mimeType", "application/octet-stream"),
                size = obj.optLong("size", 0L),
                modifiedTime = obj.optString("modifiedTime", ""),
                webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                isFolder = obj.optString("mimeType") == "application/vnd.google-apps.folder"
            )
        }
    }

    suspend fun readText(fileId: String, maxChars: Int = 12000): String = withContext(Dispatchers.IO) {
        val token = checkToken()
        // If it is a Google Doc, export as plain text
        val meta = getMetadata(fileId)
        val url = if (meta.mimeType == "application/vnd.google-apps.document") {
            "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=text/plain"
        } else {
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Drive Download Error: $err")
            }
            val text = response.body?.string() ?: ""
            if (text.length > maxChars) {
                text.take(maxChars) + "\n... [Truncated: ${text.length} chars total]"
            } else text
        }
    }

    suspend fun downloadToFile(fileId: String, destFile: File): File = withContext(Dispatchers.IO) {
        val token = checkToken()
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Download failed: $err")
            }
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
            }
            destFile
        }
    }

    suspend fun uploadFile(
        file: File,
        name: String? = null,
        parentFolderId: String? = null,
        mimeType: String = "application/octet-stream"
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val token = checkToken()
        val fileName = name ?: file.name

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            if (!parentFolderId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentFolderId))
            }
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                fileName,
                file.readBytes().toRequestBody(mimeType.toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,webViewLink")
            .addHeader("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Drive Upload Error: $err")
            }
            val obj = JSONObject(response.body?.string() ?: "{}")
            DriveFileItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                mimeType = obj.optString("mimeType", mimeType),
                size = obj.optLong("size", file.length()),
                modifiedTime = "",
                webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                isFolder = false
            )
        }
    }

    suspend fun createFolder(name: String, parentFolderId: String? = null): DriveFileItem = withContext(Dispatchers.IO) {
        val token = checkToken()
        val metadataJson = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (!parentFolderId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentFolderId))
            }
        }.toString()

        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,webViewLink")
            .addHeader("Authorization", "Bearer $token")
            .post(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Create folder error: $err")
            }
            val obj = JSONObject(response.body?.string() ?: "{}")
            DriveFileItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                mimeType = "application/vnd.google-apps.folder",
                size = 0L,
                modifiedTime = "",
                webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                isFolder = true
            )
        }
    }

    suspend fun renameFile(fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val token = checkToken()
        val body = JSONObject().put("name", newName).toString()
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    suspend fun trashFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val token = checkToken()
        val body = JSONObject().put("trashed", true).toString()
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    suspend fun getShareLink(fileId: String): String = withContext(Dispatchers.IO) {
        val token = checkToken()
        // Grant anyone with link read permission
        try {
            val permBody = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }.toString()
            val permReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .addHeader("Authorization", "Bearer $token")
                .post(permBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            client.newCall(permReq).execute().close()
        } catch (_: Exception) {}

        val meta = getMetadata(fileId)
        meta.webViewLink ?: "https://drive.google.com/file/d/$fileId/view"
    }

    private fun parseFileList(arr: JSONArray?): List<DriveFileItem> {
        if (arr == null) return emptyList()
        val list = mutableListOf<DriveFileItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val mime = obj.optString("mimeType", "")
            list.add(
                DriveFileItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    mimeType = mime,
                    size = obj.optLong("size", 0L),
                    modifiedTime = obj.optString("modifiedTime", ""),
                    webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                    isFolder = mime == "application/vnd.google-apps.folder"
                )
            )
        }
        return list
    }
}
