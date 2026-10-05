package com.example.agent.tools.gmail

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GmailService(
    private val getToken: () -> String,
    private val getUserEmail: () -> String = { "me" }
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun newRequestBuilder(endpoint: String): Request.Builder {
        val url = if (endpoint.startsWith("https://")) endpoint else "https://gmail.googleapis.com/gmail/v1/users/me$endpoint"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SasukeX-Agent-Android")

        val token = getToken().trim()
        if (token.isNotEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    fun hasToken(): Boolean = getToken().trim().isNotEmpty()

    suspend fun getProfile(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please authorize or configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val request = newRequestBuilder("/profile").get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                Result.success(
                    mapOf(
                        "emailAddress" to json.optString("emailAddress", getUserEmail()),
                        "messagesTotal" to json.optInt("messagesTotal", 0),
                        "threadsTotal" to json.optInt("threadsTotal", 0),
                        "historyId" to json.optString("historyId", "")
                    )
                )
            } else {
                val code = response.code
                val err = response.body?.string() ?: ""
                Result.failure(Exception("Gmail authentication failed (HTTP $code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to connect to Gmail API."))
        }
    }

    suspend fun listMessages(
        query: String = "",
        maxResults: Int = 10,
        labelIds: List<String> = emptyList()
    ): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val endpoint = buildString {
                append("/messages?maxResults=${maxResults.coerceIn(1, 25)}")
                if (query.isNotBlank()) append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
                labelIds.forEach { lbl ->
                    append("&labelIds=").append(java.net.URLEncoder.encode(lbl, "UTF-8"))
                }
            }

            val request = newRequestBuilder(endpoint).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gmail API error HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            val messagesArray = json.optJSONArray("messages") ?: JSONArray()
            val list = mutableListOf<Map<String, Any?>>()

            for (i in 0 until messagesArray.length()) {
                val item = messagesArray.getJSONObject(i)
                val id = item.optString("id")
                if (id.isNotBlank()) {
                    // Fetch concise metadata for each message
                    val metaRes = fetchMessageMetadata(id)
                    list.add(metaRes ?: mapOf("id" to id, "threadId" to item.optString("threadId")))
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchMessageMetadata(messageId: String): Map<String, Any?>? {
        return try {
            val req = newRequestBuilder("/messages/$messageId?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=To&metadataHeaders=Date").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: "{}")
            val snippet = json.optString("snippet", "")
            val labels = mutableListOf<String>()
            val labelsArr = json.optJSONArray("labelIds")
            if (labelsArr != null) {
                for (j in 0 until labelsArr.length()) {
                    labels.add(labelsArr.getString(j))
                }
            }

            var subject = "(No Subject)"
            var from = "Unknown"
            var to = ""
            var date = ""

            val headers = json.optJSONObject("payload")?.optJSONArray("headers")
            if (headers != null) {
                for (k in 0 until headers.length()) {
                    val h = headers.getJSONObject(k)
                    val name = h.optString("name", "")
                    val value = h.optString("value", "")
                    when {
                        name.equals("Subject", ignoreCase = true) -> subject = value
                        name.equals("From", ignoreCase = true) -> from = value
                        name.equals("To", ignoreCase = true) -> to = value
                        name.equals("Date", ignoreCase = true) -> date = value
                    }
                }
            }

            mapOf(
                "id" to messageId,
                "subject" to subject,
                "from" to from,
                "to" to to,
                "date" to date,
                "snippet" to snippet,
                "is_unread" to labels.contains("UNREAD"),
                "labels" to labels
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getMessage(messageId: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val request = newRequestBuilder("/messages/$messageId?format=full").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Email $messageId not found (HTTP ${response.code})"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val snippet = json.optString("snippet", "")

            val labels = mutableListOf<String>()
            val labelsArr = json.optJSONArray("labelIds")
            if (labelsArr != null) {
                for (j in 0 until labelsArr.length()) {
                    labels.add(labelsArr.getString(j))
                }
            }

            var subject = "(No Subject)"
            var from = "Unknown"
            var to = ""
            var date = ""

            val payload = json.optJSONObject("payload")
            val headers = payload?.optJSONArray("headers")
            if (headers != null) {
                for (k in 0 until headers.length()) {
                    val h = headers.getJSONObject(k)
                    val name = h.optString("name", "")
                    val value = h.optString("value", "")
                    when {
                        name.equals("Subject", ignoreCase = true) -> subject = value
                        name.equals("From", ignoreCase = true) -> from = value
                        name.equals("To", ignoreCase = true) -> to = value
                        name.equals("Date", ignoreCase = true) -> date = value
                    }
                }
            }

            val bodyText = extractBodyText(payload)

            Result.success(
                mapOf(
                    "id" to messageId,
                    "threadId" to json.optString("threadId"),
                    "subject" to subject,
                    "from" to from,
                    "to" to to,
                    "date" to date,
                    "snippet" to snippet,
                    "body" to bodyText,
                    "labels" to labels,
                    "is_unread" to labels.contains("UNREAD")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBodyText(payload: JSONObject?): String {
        if (payload == null) return ""

        // 1. Direct body data
        val directData = payload.optJSONObject("body")?.optString("data", "")
        if (!directData.isNullOrBlank()) {
            return decodeBase64Url(directData)
        }

        // 2. Multipart parts
        val parts = payload.optJSONArray("parts")
        if (parts != null) {
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val mimeType = part.optString("mimeType", "")
                val partData = part.optJSONObject("body")?.optString("data", "")
                if (mimeType.startsWith("text/plain") && !partData.isNullOrBlank()) {
                    return decodeBase64Url(partData)
                }
                if (!partData.isNullOrBlank()) {
                    sb.append(decodeBase64Url(partData)).append("\n")
                }
            }
            if (sb.isNotBlank()) return sb.toString().trim()
        }

        return payload.optString("snippet", "")
    }

    private fun decodeBase64Url(data: String): String {
        return try {
            val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            data
        }
    }

    suspend fun sendMessage(
        to: String,
        subject: String,
        body: String,
        cc: String? = null
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val rawEmail = buildRfc2822Email(to = to, subject = subject, body = body, cc = cc)
            val base64UrlRaw = Base64.encodeToString(
                rawEmail.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val payload = JSONObject().apply {
                put("raw", base64UrlRaw)
            }

            val request = newRequestBuilder("/messages/send")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to send email (HTTP ${response.code}): ${response.message}"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "id" to json.optString("id"),
                    "threadId" to json.optString("threadId"),
                    "to" to to,
                    "subject" to subject,
                    "status" to "SENT"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDraft(
        to: String,
        subject: String,
        body: String
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val rawEmail = buildRfc2822Email(to = to, subject = subject, body = body)
            val base64UrlRaw = Base64.encodeToString(
                rawEmail.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val messageObj = JSONObject().apply {
                put("raw", base64UrlRaw)
            }
            val payload = JSONObject().apply {
                put("message", messageObj)
            }

            val request = newRequestBuilder("/drafts")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create draft (HTTP ${response.code})"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "id" to json.optString("id"),
                    "to" to to,
                    "subject" to subject,
                    "status" to "DRAFT_CREATED"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: String, permanent: Boolean = false): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val request = if (permanent) {
                newRequestBuilder("/messages/$messageId").delete().build()
            } else {
                newRequestBuilder("/messages/$messageId/trash")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
            }

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to delete email $messageId (HTTP ${response.code})"))
            }

            Result.success(
                mapOf(
                    "id" to messageId,
                    "action" to if (permanent) "PERMANENTLY_DELETED" else "TRASHED"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun modifyLabels(
        messageId: String,
        addLabels: List<String> = emptyList(),
        removeLabels: List<String> = emptyList()
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                Exception("Gmail token is not set. Please configure your token in Settings → Gmail Connector.")
            )
        }

        try {
            val payload = JSONObject().apply {
                put("addLabelIds", JSONArray(addLabels))
                put("removeLabelIds", JSONArray(removeLabels))
            }

            val request = newRequestBuilder("/messages/$messageId/modify")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to update labels (HTTP ${response.code})"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "id" to messageId,
                    "labels" to json.optJSONArray("labelIds")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRfc2822Email(to: String, subject: String, body: String, cc: String? = null): String {
        return buildString {
            append("To: ").append(to).append("\r\n")
            if (!cc.isNullOrBlank()) {
                append("Cc: ").append(cc).append("\r\n")
            }
            append("Subject: ").append(subject).append("\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("MIME-Version: 1.0\r\n")
            append("\r\n")
            append(body)
        }
    }
}
