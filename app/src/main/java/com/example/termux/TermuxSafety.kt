package com.example.termux

enum class CommandSafetyLevel {
    READ_ONLY,
    NORMAL,
    DESTRUCTIVE
}

data class TermuxExecutionResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val error: String? = null
)

object TermuxSafety {

    private val READ_ONLY_REGEX = Regex(
        "^\\s*(pwd|ls|dir|find|cat|head|tail|less|more|grep|egrep|fgrep|wc|diff|file|which|whereis|whoami|id|uname|uptime|date|cal|df|du|free|ps|top|echo|printf|git\\s+(status|log|diff|show|branch|remote|tag|config\\s+--get)|python[0-9.]*\\s+--version|python[0-9.]*\\s+-V|node\\s+--version|npm\\s+--version|java\\s+-version|gcc\\s+--version|cargo\\s+--version)\\b.*",
        RegexOption.IGNORE_CASE
    )

    private val DESTRUCTIVE_PATTERNS = listOf(
        Regex("\\brm\\s+(-[a-zA-Z]*r[a-zA-Z]*f?|-rf|--recursive|--force)\\b", RegexOption.IGNORE_CASE),
        Regex("\\brm\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+reset\\s+--hard\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+clean\\s+-[a-zA-Z]*f\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+push\\s+.*--force\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmkfs(\\.[a-zA-Z0-9]+)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdd\\s+if=.*of=/dev/\\b", RegexOption.IGNORE_CASE),
        Regex("\\btruncate\\b", RegexOption.IGNORE_CASE),
        Regex("\\bshred\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfdisk\\b", RegexOption.IGNORE_CASE),
        Regex("\\bformat\\b", RegexOption.IGNORE_CASE),
        Regex(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};\\s*:", RegexOption.IGNORE_CASE), // Fork bomb
        Regex(">\\s*/dev/sd[a-z]", RegexOption.IGNORE_CASE)
    )

    fun classifyCommand(command: String): CommandSafetyLevel {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return CommandSafetyLevel.READ_ONLY

        for (pattern in DESTRUCTIVE_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return CommandSafetyLevel.DESTRUCTIVE
            }
        }

        if (READ_ONLY_REGEX.matches(trimmed)) {
            return CommandSafetyLevel.READ_ONLY
        }

        return CommandSafetyLevel.NORMAL
    }

    fun isDestructive(command: String): Boolean {
        return classifyCommand(command) == CommandSafetyLevel.DESTRUCTIVE
    }
}
