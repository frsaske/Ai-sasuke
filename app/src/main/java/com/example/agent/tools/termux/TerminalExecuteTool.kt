package com.example.agent.tools.termux

import android.content.Context
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.termux.CommandSafetyLevel
import com.example.termux.TermuxExecutor
import com.example.termux.TermuxSafety

class TerminalExecuteTool(private val context: Context) : Tool {

    override val name: String = "terminal_execute"

    override val description: String =
        "Execute a shell command inside the local terminal/Termux execution environment. Returns stdout, stderr, and exit_code. Destructive commands (like rm, git reset --hard, formatting) will require user confirmation."

    override val permission: ToolPermission = ToolPermission.WRITE

    override fun requiresConfirmation(arguments: Map<String, Any?>): Boolean {
        val command = arguments["command"]?.toString() ?: ""
        return TermuxSafety.isDestructive(command)
    }

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "command" to mapOf(
                "type" to "STRING",
                "description" to "The shell command to execute (e.g. 'ls -la', 'python script.py', 'git status')"
            ),
            "working_directory" to mapOf(
                "type" to "STRING",
                "description" to "Optional working directory path (defaults to local workspace or Termux home)"
            ),
            "timeout_seconds" to mapOf(
                "type" to "INTEGER",
                "description" to "Maximum execution time in seconds (default 60)"
            )
        ),
        "required" to listOf("command")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val command = arguments["command"]?.toString()?.trim() ?: ""
        if (command.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Command cannot be empty.")
        }

        val workingDir = arguments["working_directory"]?.toString()?.takeIf { it.isNotBlank() }
        val timeoutSec = (arguments["timeout_seconds"] as? Number)?.toLong() ?: 60L

        val result = TermuxExecutor.execute(
            context = context,
            command = command,
            workingDirectory = workingDir,
            timeoutSeconds = timeoutSec.coerceIn(5L, 300L)
        )

        val dataMap = mapOf(
            "success" to result.success,
            "command" to command,
            "stdout" to result.stdout,
            "stderr" to result.stderr,
            "exit_code" to result.exitCode,
            "working_directory" to (workingDir ?: "workspace")
        )

        return if (result.success) {
            ToolResult.success(
                data = dataMap,
                summary = "Exit code ${result.exitCode}: ${result.stdout.lines().firstOrNull()?.take(60) ?: "Completed"}"
            )
        } else {
            ToolResult.success(
                data = dataMap,
                summary = "Exit code ${result.exitCode}: ${result.stderr.lines().firstOrNull()?.take(60) ?: (result.error ?: "Failed")}"
            )
        }
    }
}
