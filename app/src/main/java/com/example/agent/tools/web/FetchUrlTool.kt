package com.example.agent.tools.web

import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class FetchUrlTool : Tool {
    override val name: String = "fetch_url"
    override val description: String =
        "Fetch and extract readable plain text content from a web page URL. Use after web_search to inspect an article in depth."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "url" to mapOf(
                "type" to "STRING",
                "description" to "The full HTTPS URL of the webpage to fetch."
            )
        ),
        "required" to listOf("url")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val MAX_TEXT_LENGTH = 14000
    }

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawUrl = arguments["url"]?.toString()?.trim()
        if (rawUrl.isNullOrEmpty()) {
            return@withContext ToolResult.failure("INVALID_ARGUMENT", "URL parameter cannot be empty.")
        }

        // Validate URI & check against SSRF/private networks
        val uri = try {
            URI(rawUrl)
        } catch (e: Exception) {
            return@withContext ToolResult.failure("INVALID_URL", "Malformed URL: $rawUrl")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return@withContext ToolResult.failure("INVALID_SCHEME", "Only HTTP/HTTPS URLs are allowed.")
        }

        val host = uri.host?.lowercase(Locale.ROOT) ?: ""
        if (isPrivateHost(host)) {
            return@withContext ToolResult.failure("BLOCKED_HOST", "Access to internal, localhost or private IP addresses is blocked.")
        }

        try {
            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ToolResult.failure("HTTP_ERROR", "Webpage returned status HTTP ${response.code}")
            }

            val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
            if (!contentType.contains("text/") && !contentType.contains("html") && !contentType.contains("xml") && !contentType.contains("json")) {
                return@withContext ToolResult.failure("UNSUPPORTED_MEDIA", "Unsupported content type: $contentType. Only readable text/html pages can be fetched.")
            }

            val responseBody = response.body?.string() ?: ""
            if (responseBody.isBlank()) {
                return@withContext ToolResult.failure("EMPTY_PAGE", "Fetched page was empty.")
            }

            // Parse HTML safely with Jsoup
            val document = Jsoup.parse(responseBody, rawUrl)
            val title = document.title().ifBlank { uri.path.substringAfterLast('/').ifBlank { host } }

            // Strip noisy tags
            document.select("script, style, noscript, svg, nav, footer, header, form, iframe, aside, .advertisement, .ads").remove()

            // Extract main readable content
            val bodyText = document.body()?.text() ?: ""
            val truncatedText = if (bodyText.length > MAX_TEXT_LENGTH) {
                bodyText.take(MAX_TEXT_LENGTH) + "\n\n[Content truncated for length...]"
            } else {
                bodyText
            }

            val finalUrl = response.request.url.toString()
            val sources = listOf(SourceCitation(title = title, url = finalUrl))

            ToolResult.success(
                data = mapOf(
                    "title" to title,
                    "url" to finalUrl,
                    "content" to truncatedText
                ),
                summary = "Read ${truncatedText.length} characters from $host",
                sources = sources
            )
        } catch (e: Exception) {
            ToolResult.failure(
                "FETCH_ERROR",
                e.localizedMessage ?: "Failed to fetch webpage content."
            )
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1") return true
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return true
        if (host.matches(Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))) return true
        return false
    }
}
