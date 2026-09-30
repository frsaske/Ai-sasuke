package com.example.agent.tools.github

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

class GitHubService(
    private val getToken: () -> String
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun newRequestBuilder(endpoint: String): Request.Builder {
        val url = if (endpoint.startsWith("https://")) endpoint else "https://api.github.com$endpoint"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SasukeX-Agent-Android")

        val token = getToken().trim()
        if (token.isNotEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val token = getToken().trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(Exception("No GitHub token configured. Please enter your Personal Access Token."))
        }

        try {
            val request = newRequestBuilder("/user").get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val login = json.optString("login", "User")
                val name = json.optString("name", login)
                Result.success("Connected to GitHub as $name (@$login)")
            } else {
                val code = response.code
                Result.failure(Exception("GitHub authentication failed (HTTP $code). Verify your token permissions."))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to connect to GitHub."))
        }
    }

    suspend fun listRepositories(visibility: String = "all", sort: String = "updated", perPage: Int = 10): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken().trim()
            val endpoint = if (token.isNotEmpty()) {
                "/user/repos?sort=$sort&per_page=$perPage&affiliation=owner,collaborator"
            } else {
                "/repositories?per_page=$perPage"
            }
            val request = newRequestBuilder(endpoint).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API error HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    mapOf(
                        "full_name" to item.optString("full_name"),
                        "name" to item.optString("name"),
                        "description" to item.optString("description", "No description"),
                        "stars" to item.optInt("stargazers_count", 0),
                        "forks" to item.optInt("forks_count", 0),
                        "private" to item.optBoolean("private", false),
                        "html_url" to item.optString("html_url"),
                        "default_branch" to item.optString("default_branch", "main"),
                        "updated_at" to item.optString("updated_at")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRepositoryInfo(owner: String, repo: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/repos/$owner/$repo").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Repository $owner/$repo not found (HTTP ${response.code})"))
            }
            val item = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "full_name" to item.optString("full_name"),
                    "description" to item.optString("description", "No description"),
                    "stars" to item.optInt("stargazers_count", 0),
                    "forks" to item.optInt("forks_count", 0),
                    "open_issues" to item.optInt("open_issues_count", 0),
                    "default_branch" to item.optString("default_branch", "main"),
                    "language" to item.optString("language", "Not specified"),
                    "html_url" to item.optString("html_url"),
                    "created_at" to item.optString("created_at"),
                    "updated_at" to item.optString("updated_at")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(owner: String, repo: String, path: String = "", ref: String? = null): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trim().trimStart('/')
            val endpoint = buildString {
                append("/repos/$owner/$repo/contents/$cleanPath")
                if (!ref.isNullOrBlank()) append("?ref=$ref")
            }
            val request = newRequestBuilder(endpoint).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Cannot list directory in $owner/$repo (HTTP ${response.code})"))
            }

            val body = response.body?.string() ?: "[]"
            val list = mutableListOf<Map<String, Any?>>()
            if (body.trimStart().startsWith("[")) {
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    list.add(
                        mapOf(
                            "name" to item.optString("name"),
                            "path" to item.optString("path"),
                            "type" to item.optString("type"), // "file", "dir"
                            "size" to item.optInt("size", 0),
                            "sha" to item.optString("sha")
                        )
                    )
                }
            } else {
                val item = JSONObject(body)
                list.add(
                    mapOf(
                        "name" to item.optString("name"),
                        "path" to item.optString("path"),
                        "type" to item.optString("type"),
                        "size" to item.optInt("size", 0),
                        "sha" to item.optString("sha")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFile(owner: String, repo: String, path: String, ref: String? = null): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trim().trimStart('/')
            val endpoint = buildString {
                append("/repos/$owner/$repo/contents/$cleanPath")
                if (!ref.isNullOrBlank()) append("?ref=$ref")
            }
            val request = newRequestBuilder(endpoint).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("File $cleanPath not found in $owner/$repo (HTTP ${response.code})"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val encoding = json.optString("encoding", "")
            val rawContent = json.optString("content", "")
            val sha = json.optString("sha", "")
            val size = json.optInt("size", 0)

            val decodedText = if (encoding.equals("base64", ignoreCase = true)) {
                val cleanBase64 = rawContent.replace("\n", "").replace("\r", "")
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            } else {
                rawContent
            }

            val maxChars = 12000
            val truncated = if (decodedText.length > maxChars) {
                decodedText.take(maxChars) + "\n\n... [File content truncated, total length: ${decodedText.length} chars]"
            } else {
                decodedText
            }

            Result.success(
                mapOf(
                    "path" to cleanPath,
                    "sha" to sha,
                    "size" to size,
                    "content" to truncated
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listCommits(owner: String, repo: String, perPage: Int = 5): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/repos/$owner/$repo/commits?per_page=$perPage").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch commits for $owner/$repo (HTTP ${response.code})"))
            }

            val array = JSONArray(response.body?.string() ?: "[]")
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val sha = item.optString("sha", "").take(7)
                val commitObj = item.optJSONObject("commit")
                val message = commitObj?.optString("message", "No commit message")?.lines()?.firstOrNull() ?: ""
                val authorObj = commitObj?.optJSONObject("author")
                val authorName = authorObj?.optString("name", "Unknown") ?: "Unknown"
                val date = authorObj?.optString("date", "") ?: ""

                list.add(
                    mapOf(
                        "sha" to sha,
                        "author" to authorName,
                        "message" to message,
                        "date" to date
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listIssues(owner: String, repo: String, state: String = "open", perPage: Int = 10): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/repos/$owner/$repo/issues?state=$state&per_page=$perPage").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch issues for $owner/$repo (HTTP ${response.code})"))
            }

            val array = JSONArray(response.body?.string() ?: "[]")
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                // Filter out pull requests which GitHub also returns in /issues
                if (item.has("pull_request")) continue

                list.add(
                    mapOf(
                        "number" to item.optInt("number"),
                        "title" to item.optString("title"),
                        "state" to item.optString("state"),
                        "created_by" to item.optJSONObject("user")?.optString("login", "unknown"),
                        "html_url" to item.optString("html_url")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBranches(owner: String, repo: String): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/repos/$owner/$repo/branches").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch branches for $owner/$repo (HTTP ${response.code})"))
            }

            val array = JSONArray(response.body?.string() ?: "[]")
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    mapOf(
                        "name" to item.optString("name"),
                        "commit_sha" to item.optJSONObject("commit")?.optString("sha", "")?.take(7)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listReleases(owner: String, repo: String): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/repos/$owner/$repo/releases?per_page=5").get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch releases for $owner/$repo (HTTP ${response.code})"))
            }

            val array = JSONArray(response.body?.string() ?: "[]")
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    mapOf(
                        "tag" to item.optString("tag_name"),
                        "name" to item.optString("name", item.optString("tag_name")),
                        "published_at" to item.optString("published_at"),
                        "html_url" to item.optString("html_url")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- WRITE OPERATIONS ---

    suspend fun createIssue(owner: String, repo: String, title: String, body: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("body", body)
            }
            val request = newRequestBuilder("/repos/$owner/$repo/issues")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create issue (HTTP ${response.code}): ${response.message}"))
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "number" to json.optInt("number"),
                    "title" to json.optString("title"),
                    "html_url" to json.optString("html_url")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String? = null,
        branch: String? = null
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trim().trimStart('/')
            val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("message", message.ifBlank { "Update $cleanPath via SasukeX" })
                put("content", encodedContent)
                if (!sha.isNullOrBlank()) put("sha", sha)
                if (!branch.isNullOrBlank()) put("branch", branch)
            }

            val request = newRequestBuilder("/repos/$owner/$repo/contents/$cleanPath")
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to save file in $owner/$repo (HTTP ${response.code})"))
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val contentObj = json.optJSONObject("content")
            Result.success(
                mapOf(
                    "path" to cleanPath,
                    "sha" to contentObj?.optString("sha", ""),
                    "html_url" to contentObj?.optString("html_url", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        sha: String,
        branch: String? = null
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trim().trimStart('/')
            val payload = JSONObject().apply {
                put("message", message.ifBlank { "Delete $cleanPath via SasukeX" })
                put("sha", sha)
                if (!branch.isNullOrBlank()) put("branch", branch)
            }

            val request = newRequestBuilder("/repos/$owner/$repo/contents/$cleanPath")
                .delete(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to delete file $cleanPath (HTTP ${response.code})"))
            }
            Result.success(mapOf("deleted" to true, "path" to cleanPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBranch(
        owner: String,
        repo: String,
        branchName: String,
        fromSha: String
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("ref", "refs/heads/$branchName")
                put("sha", fromSha)
            }
            val request = newRequestBuilder("/repos/$owner/$repo/git/refs")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create branch $branchName (HTTP ${response.code})"))
            }
            Result.success(mapOf("branch" to branchName, "ref" to "refs/heads/$branchName"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("body", body)
                put("head", head)
                put("base", base)
            }
            val request = newRequestBuilder("/repos/$owner/$repo/pulls")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create PR (HTTP ${response.code}): ${response.message}"))
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(
                mapOf(
                    "number" to json.optInt("number"),
                    "title" to json.optString("title"),
                    "html_url" to json.optString("html_url")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
