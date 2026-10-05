package com.example.agent.tools.web

import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TavilyWebSearchTool(
    private val getApiKey: () -> String
) : Tool {
    override val name: String = "web_search"
    override val description: String =
        "Search the live web using Tavily AI Search for accurate real-time facts, current news, technical docs, and documentation."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "STRING",
                "description" to "The search terms to query on the live web."
            )
        ),
        "required" to listOf("query")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments["query"]?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "Query string must not be empty.")
        }

        val apiKey = getApiKey().trim()
        if (apiKey.isEmpty()) {
            return@withContext ToolResult.failure(
                "CONFIG_REQUIRED",
                "Tavily API key is not configured. Please set your key in Settings > Agent Tools."
            )
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("api_key", apiKey)
                put("query", query)
                put("search_depth", "basic")
                put("include_answer", true)
                put("max_results", 5)
            }

            val requestBody = jsonPayload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .post(requestBody)
                .header("Accept", "application/json")
                .header("User-Agent", "SasukeX-Agent/1.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext when (code) {
                    401, 403 -> ToolResult.failure("UNAUTHORIZED", "Invalid Tavily API key. Check your key in Settings.")
                    429 -> ToolResult.failure("RATE_LIMIT", "Tavily rate limit exceeded. Please retry in a moment.")
                    else -> ToolResult.failure("HTTP_ERROR", "Tavily returned HTTP $code: ${bodyString.take(120)}")
                }
            }

            if (bodyString.isBlank()) {
                return@withContext ToolResult.failure("EMPTY_RESPONSE", "Received empty response from Tavily search.")
            }

            val json = JSONObject(bodyString)
            val directAnswer = json.optString("answer", "").takeIf { it.isNotBlank() }
            val resultsArray = json.optJSONArray("results") ?: JSONArray()

            val parsedResults = mutableListOf<Map<String, String>>()
            val sources = mutableListOf<SourceCitation>()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val title = item.optString("title", "Untitled").take(150)
                val url = item.optString("url", "")
                val content = item.optString("content", "").take(500)

                if (url.isNotEmpty()) {
                    parsedResults.add(
                        mapOf(
                            "title" to title,
                            "url" to url,
                            "content" to content
                        )
                    )
                    sources.add(SourceCitation(title = title, url = url))
                }
            }

            val summary = when {
                parsedResults.isNotEmpty() -> "${parsedResults.size} web results found"
                !directAnswer.isNullOrBlank() -> "Direct answer retrieved"
                else -> "0 results found"
            }

            ToolResult.success(
                data = mapOf(
                    "query" to query,
                    "answer" to (directAnswer ?: ""),
                    "results" to parsedResults
                ),
                summary = summary,
                sources = sources
            )
        } catch (e: Exception) {
            ToolResult.failure(
                "NETWORK_ERROR",
                e.localizedMessage ?: "Failed to connect to Tavily search service."
            )
        }
    }
}
