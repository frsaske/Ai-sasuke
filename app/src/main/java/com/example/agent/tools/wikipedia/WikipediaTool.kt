package com.example.agent.tools.wikipedia

import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WikipediaTool : Tool {
    override val name: String = "wikipedia_search"
    override val description: String =
        "Search Wikipedia encyclopedia for summaries, biographies, historical events, scientific definitions, and cultural knowledge."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "STRING",
                "description" to "The topic, person, place, or concept to look up on Wikipedia."
            )
        ),
        "required" to listOf("query")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments["query"]?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "Query string must not be empty.")
        }

        try {
            // 1. Search Wikipedia
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&utf8=&format=json"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "SasukeX-Agent-Android/1.0 (Mobile)")
                .build()

            val searchResponse = httpClient.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                return@withContext ToolResult.failure("HTTP_ERROR", "Wikipedia search returned HTTP ${searchResponse.code}")
            }

            val searchJson = JSONObject(searchResponse.body?.string() ?: "{}")
            val searchList = searchJson.optJSONObject("query")?.optJSONArray("search")
            if (searchList == null || searchList.length() == 0) {
                return@withContext ToolResult.success(
                    data = mapOf("query" to query, "results" to "No Wikipedia articles found matching '$query'."),
                    summary = "No article found on Wikipedia"
                )
            }

            // Top match
            val topTitle = searchList.getJSONObject(0).optString("title", "")
            if (topTitle.isEmpty()) {
                return@withContext ToolResult.failure("NOT_FOUND", "No valid article title found.")
            }

            // 2. Fetch page summary
            val encodedTitle = URLEncoder.encode(topTitle.replace(' ', '_'), "UTF-8")
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"

            val summaryRequest = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", "SasukeX-Agent-Android/1.0 (Mobile)")
                .build()

            val summaryResponse = httpClient.newCall(summaryRequest).execute()
            if (!summaryResponse.isSuccessful) {
                val snippet = searchList.getJSONObject(0).optString("snippet", "").replace(Regex("<.*?>"), "")
                return@withContext ToolResult.success(
                    data = mapOf("title" to topTitle, "extract" to snippet),
                    summary = "Found $topTitle",
                    sources = listOf(SourceCitation(topTitle, "https://en.wikipedia.org/wiki/$encodedTitle"))
                )
            }

            val summaryJson = JSONObject(summaryResponse.body?.string() ?: "{}")
            val title = summaryJson.optString("title", topTitle)
            val extract = summaryJson.optString("extract", "No summary available.")
            val description = summaryJson.optString("description", "")
            val pageUrl = summaryJson.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page", "https://en.wikipedia.org/wiki/$encodedTitle")
                ?: "https://en.wikipedia.org/wiki/$encodedTitle"

            val sources = listOf(SourceCitation(title, pageUrl))

            ToolResult.success(
                data = mapOf(
                    "title" to title,
                    "description" to description,
                    "extract" to extract,
                    "url" to pageUrl
                ),
                summary = "Wikipedia: $title",
                sources = sources
            )
        } catch (e: Exception) {
            ToolResult.failure("NETWORK_ERROR", e.localizedMessage ?: "Failed to connect to Wikipedia.")
        }
    }
}
