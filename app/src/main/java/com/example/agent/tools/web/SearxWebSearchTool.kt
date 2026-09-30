package com.example.agent.tools.web

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

class SearxWebSearchTool(
    private val getSearxUrl: () -> String
) : Tool {
    override val name: String = "web_search"
    override val description: String =
        "Search the live web using SearXNG for current news, facts, technical articles, and documentation."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "STRING",
                "description" to "The search terms to query on the web."
            )
        ),
        "required" to listOf("query")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments["query"]?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "Query string must not be empty.")
        }

        val instanceBaseUrl = getSearxUrl().trim().trimEnd('/')
        if (instanceBaseUrl.isEmpty()) {
            return@withContext ToolResult.failure(
                "CONFIG_REQUIRED",
                "SearXNG instance URL is not configured. Please set your SearXNG instance in Settings > Agent Tools."
            )
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val targetUrl = if (instanceBaseUrl.contains("/search")) {
                "$instanceBaseUrl?q=$encodedQuery&format=json"
            } else {
                "$instanceBaseUrl/search?q=$encodedQuery&format=json"
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "SasukeX-Agent/1.0 (Android; Mobile)")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                return@withContext when (code) {
                    429 -> ToolResult.failure("RATE_LIMIT", "SearXNG rate limit reached. Please retry in a moment.")
                    403 -> ToolResult.failure("FORBIDDEN", "SearXNG access was forbidden by the instance.")
                    else -> ToolResult.failure("HTTP_ERROR", "SearXNG returned HTTP $code.")
                }
            }

            val bodyString = response.body?.string() ?: ""
            if (bodyString.isBlank()) {
                return@withContext ToolResult.failure("EMPTY_RESPONSE", "SearXNG returned an empty response.")
            }

            val json = JSONObject(bodyString)
            val resultsArray = json.optJSONArray("results")
            if (resultsArray == null || resultsArray.length() == 0) {
                return@withContext ToolResult.success(
                    data = mapOf("query" to query, "results" to emptyList<Map<String, String>>()),
                    summary = "0 results found for '$query'"
                )
            }

            val parsedResults = mutableListOf<Map<String, String>>()
            val sources = mutableListOf<SourceCitation>()
            val maxResults = 6

            for (i in 0 until minOf(resultsArray.length(), maxResults)) {
                val item = resultsArray.getJSONObject(i)
                val title = item.optString("title", "Untitled").take(150)
                val url = item.optString("url", "")
                val snippet = item.optString("content", "").take(300)
                val engine = item.optString("engine", "web")

                if (url.isNotEmpty()) {
                    parsedResults.add(
                        mapOf(
                            "title" to title,
                            "url" to url,
                            "snippet" to snippet,
                            "source" to engine
                        )
                    )
                    sources.add(SourceCitation(title = title, url = url))
                }
            }

            ToolResult.success(
                data = mapOf(
                    "query" to query,
                    "results" to parsedResults
                ),
                summary = "${parsedResults.size} web results found",
                sources = sources
            )
        } catch (e: Exception) {
            ToolResult.failure(
                "NETWORK_ERROR",
                e.localizedMessage ?: "Failed to connect to SearXNG instance."
            )
        }
    }
}
