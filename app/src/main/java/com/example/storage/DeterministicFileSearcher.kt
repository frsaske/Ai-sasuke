package com.example.storage

import com.example.data.model.FileIndexEntity
import java.util.Locale
import kotlin.math.max

data class FileSearchResult(
    val name: String,
    val uri: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val displayPath: String,
    val score: Double,
    val matchType: String
)

object DeterministicFileSearcher {

    private val EXTENSION_MAP = mapOf(
        "python" to "py",
        "py" to "py",
        "javascript" to "js",
        "js" to "js",
        "typescript" to "ts",
        "ts" to "ts",
        "markdown" to "md",
        "md" to "md",
        "text" to "txt",
        "txt" to "txt",
        "json" to "json",
        "pdf" to "pdf",
        "zip" to "zip",
        "tar" to "tar",
        "image" to "png",
        "photo" to "jpg",
        "video" to "mp4",
        "kotlin" to "kt",
        "kt" to "kt",
        "java" to "java",
        "shell" to "sh",
        "sh" to "sh"
    )

    private val STOPWORDS = setOf(
        "dhund", "dhundo", "dhoondo", "search", "find", "check", "karo", "karna", "dikhao", "batao",
        "abey", "bhai", "file", "files", "wali", "wala", "mein", "me", "folder", "ko", "ki", "ka",
        "hai", "kya", "koi", "the", "a", "an", "in", "show", "get", "list"
    )

    fun search(files: List<FileIndexEntity>, rawQuery: String): List<FileSearchResult> {
        val query = rawQuery.trim()
        if (query.isEmpty() || files.isEmpty()) return emptyList()

        val queryLower = query.lowercase(Locale.ROOT)
        val extractedExt = detectTargetExtension(queryLower)
        val cleanTerms = extractSearchTerms(queryLower)
        val coreQuery = cleanTerms.joinToString(" ").ifBlank { queryLower }

        val scoredResults = mutableListOf<FileSearchResult>()

        for (file in files) {
            val fileName = file.name
            val fileNameLower = file.normalizedName
            val folderLower = file.parentFolder.lowercase(Locale.ROOT)

            var score = 0.0
            var matchType = "none"

            // 1. Exact filename match
            if (fileName == query) {
                score = 1.00
                matchType = "exact"
            }
            // 2. Case-insensitive exact match
            else if (fileNameLower == queryLower || fileNameLower == coreQuery) {
                score = 0.98
                matchType = "case_insensitive_exact"
            }
            // 3. Prefix match
            else if (fileNameLower.startsWith(coreQuery) || coreQuery.startsWith(fileNameLower)) {
                score = 0.88
                matchType = "prefix"
            }
            // 4. Token match (all or most tokens present)
            else {
                val fileTokens = fileNameLower.split("_", "-", " ", ".", "/").filter { it.isNotBlank() }
                val matchedTokens = cleanTerms.count { term ->
                    fileTokens.any { it.contains(term) } || fileNameLower.contains(term)
                }

                if (cleanTerms.isNotEmpty() && matchedTokens == cleanTerms.size) {
                    score = 0.80 + (0.05 * (cleanTerms.size.coerceAtMost(3)))
                    matchType = "token_match"
                }
                // 5. Substring match
                else if (fileNameLower.contains(coreQuery)) {
                    score = 0.75
                    matchType = "substring"
                }
                // Check folder match
                else if (folderLower.contains(coreQuery) || cleanTerms.any { folderLower.contains(it) }) {
                    score = 0.70
                    matchType = "folder_match"
                }
                // 6. Fuzzy match
                else {
                    val sim = stringSimilarity(coreQuery, fileNameLower)
                    if (sim >= 0.55) {
                        score = 0.50 + (sim * 0.20)
                        matchType = "fuzzy"
                    }
                }
            }

            // 7. Extension-aware match boost
            if (extractedExt != null) {
                if (file.extension.equals(extractedExt, ignoreCase = true)) {
                    score = if (score > 0) score + 0.15 else 0.65
                    if (matchType == "none") matchType = "extension_match"
                }
            }

            if (score > 0.40) {
                scoredResults.add(
                    FileSearchResult(
                        name = file.name,
                        uri = file.uriString,
                        folder = file.parentFolder,
                        size = file.size,
                        modified = file.lastModified,
                        displayPath = file.displayPath,
                        score = (score.coerceAtMost(1.00) * 100).toInt() / 100.0,
                        matchType = matchType
                    )
                )
            }
        }

        return scoredResults.sortedByDescending { it.score }.take(15)
    }

    private fun detectTargetExtension(query: String): String? {
        val words = query.split(Regex("[\\s,]+"))
        for (word in words) {
            val stripped = word.removePrefix(".")
            if (EXTENSION_MAP.containsKey(stripped)) {
                return EXTENSION_MAP[stripped]
            }
        }
        return null
    }

    private fun extractSearchTerms(query: String): List<String> {
        val tokens = query.split(Regex("[^a-zA-Z0-9._-]+"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length > 1 && !STOPWORDS.contains(it) }
        return tokens
    }

    private fun stringSimilarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(s1, s2)
        return (maxLen - dist).toDouble() / maxLen
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
