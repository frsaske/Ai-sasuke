package com.example.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.util.LocalWorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TermuxExecutor {

    const val TERMUX_PACKAGE_NAME = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    // Termux RUN_COMMAND standard intent extras constants
    const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_RUN_COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    // Result callback broadcast action
    private const val ACTION_TERMUX_RESULT = "com.example.sasukex.TERMUX_COMMAND_RESULT"
    private const val EXTRA_REQUEST_ID = "request_id"

    private val pendingResults = ConcurrentHashMap<String, Channel<TermuxExecutionResult>>()

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(TERMUX_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun execute(
        context: Context,
        command: String,
        workingDirectory: String? = null,
        timeoutSeconds: Long = 60
    ): TermuxExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TermuxExecutionResult(
                success = false,
                stdout = "",
                stderr = "Empty command.",
                exitCode = 1,
                error = "EMPTY_COMMAND"
            )
        }

        val workDir = resolveWorkingDirectory(context, workingDirectory)

        // If Termux is installed and permission is granted, dispatch via Termux RUN_COMMAND
        if (isTermuxInstalled(context) && hasRunCommandPermission(context)) {
            return@withContext executeViaTermux(context, trimmed, workDir, timeoutSeconds)
        }

        // If Termux is not installed or permission missing, provide transparent local shell execution
        // using the Android process runtime in the workspace directory
        return@withContext executeViaLocalProcess(context, trimmed, workDir, timeoutSeconds)
    }

    private suspend fun executeViaTermux(
        context: Context,
        command: String,
        workingDir: File,
        timeoutSeconds: Long
    ): TermuxExecutionResult {
        val requestId = UUID.randomUUID().toString()
        val channel = Channel<TermuxExecutionResult>(1)
        pendingResults[requestId] = channel

        val callbackIntent = Intent(ACTION_TERMUX_RESULT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REQUEST_ID, requestId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            callbackIntent,
            pendingIntentFlags
        )

        val runIntent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE_NAME, "com.termux.app.RunCommandService")
            putExtra(EXTRA_RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", command))
            putExtra(EXTRA_RUN_COMMAND_WORKDIR, workingDir.absolutePath)
            putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
            putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
            putExtra(EXTRA_RUN_COMMAND_PENDING_INTENT, pendingIntent)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val recId = intent.getStringExtra(EXTRA_REQUEST_ID)
                if (recId == requestId) {
                    val stdout = intent.getStringExtra("stdout") ?: ""
                    val stderr = intent.getStringExtra("stderr") ?: ""
                    val exitCode = intent.getIntExtra("exitCode", 0)
                    val errmsg = intent.getStringExtra("errmsg")

                    val result = TermuxExecutionResult(
                        success = (exitCode == 0) && errmsg.isNullOrBlank(),
                        stdout = stdout,
                        stderr = if (!errmsg.isNullOrBlank()) "$stderr\n$errmsg".trim() else stderr,
                        exitCode = exitCode,
                        error = errmsg
                    )
                    channel.trySend(result)
                }
            }
        }

        val filter = IntentFilter(ACTION_TERMUX_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(runIntent)
            } else {
                context.startService(runIntent)
            }
        } catch (_: Exception) {
            // If service startup failed (e.g. background restriction), try sending as broadcast
            try {
                context.sendBroadcast(runIntent)
            } catch (e: Exception) {
                context.unregisterReceiver(receiver)
                pendingResults.remove(requestId)
                return executeViaLocalProcess(context, command, workingDir, timeoutSeconds)
            }
        }

        return try {
            withTimeout(timeoutSeconds * 1000) {
                val res = channel.receive()
                res
            }
        } catch (_: TimeoutCancellationException) {
            TermuxExecutionResult(
                success = false,
                stdout = "",
                stderr = "Command timed out after $timeoutSeconds seconds.",
                exitCode = 124,
                error = "TIMEOUT"
            )
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            pendingResults.remove(requestId)
        }
    }

    private suspend fun executeViaLocalProcess(
        context: Context,
        command: String,
        workingDir: File,
        timeoutSeconds: Long
    ): TermuxExecutionResult = withContext(Dispatchers.IO) {
        val process: Process
        try {
            process = ProcessBuilder("sh", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            return@withContext TermuxExecutionResult(
                success = false,
                stdout = "",
                stderr = e.localizedMessage ?: "Failed to spawn process",
                exitCode = 1,
                error = "SPAWN_ERROR"
            )
        }

        try {
            val stdoutChannel = Channel<String>(1)
            val stderrChannel = Channel<String>(1)

            Thread {
                val out = process.inputStream.bufferedReader().use { it.readText() }
                stdoutChannel.trySend(out)
            }.start()

            Thread {
                val err = process.errorStream.bufferedReader().use { it.readText() }
                stderrChannel.trySend(err)
            }.start()

            withTimeout(timeoutSeconds * 1000) {
                val exitCode = process.waitFor()
                val stdout = stdoutChannel.receive()
                val stderr = stderrChannel.receive()

                TermuxExecutionResult(
                    success = (exitCode == 0),
                    stdout = stdout.take(15000),
                    stderr = stderr.take(15000),
                    exitCode = exitCode,
                    error = if (exitCode != 0) "EXIT_$exitCode" else null
                )
            }
        } catch (_: TimeoutCancellationException) {
            process.destroy()
            TermuxExecutionResult(
                success = false,
                stdout = "",
                stderr = "Command timed out after $timeoutSeconds seconds.",
                exitCode = 124,
                error = "TIMEOUT"
            )
        } catch (e: Exception) {
            process.destroy()
            TermuxExecutionResult(
                success = false,
                stdout = "",
                stderr = e.localizedMessage ?: "Execution error",
                exitCode = 1,
                error = e.javaClass.simpleName
            )
        }
    }

    private fun resolveWorkingDirectory(context: Context, path: String?): File {
        if (!path.isNullOrBlank()) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                return dir
            }
        }
        return LocalWorkspaceManager.getWorkspaceDir(context)
    }
}
