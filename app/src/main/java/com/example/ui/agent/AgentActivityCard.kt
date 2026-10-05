package com.example.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.model.ToolActivity
import com.example.agent.model.ToolStatus
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.FileTransferManager
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AgentActivityCard(
    activity: ToolActivity,
    modifier: Modifier = Modifier,
    onConfirmAction: ((ToolActivity) -> Unit)? = null,
    onCancelAction: ((ToolActivity) -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }
    var showPreviewCode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(2000)
            copiedCode = false
        }
    }

    val isRunning = activity.status == ToolStatus.RUNNING || activity.status == ToolStatus.PREPARING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateColor(
        initialValue = SasukeCrimson.copy(alpha = 0.3f),
        targetValue = SasukeCrimson.copy(alpha = 1.0f),
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val containerBackground = when (activity.status) {
        ToolStatus.WAITING_CONFIRMATION -> Color(0xFF1E1710)
        else -> SurfaceElevated
    }

    val borderColor = when (activity.status) {
        ToolStatus.WAITING_CONFIRMATION -> Color(0xFFF59E0B).copy(alpha = 0.4f)
        ToolStatus.RUNNING, ToolStatus.PREPARING -> SasukeCrimson.copy(alpha = 0.5f)
        else -> BorderSubtle
    }

    // Inspect if this activity produced or handled a file download
    val isDownloadTool = activity.toolName == "github_download_file" ||
            activity.resultSummary?.contains("download", ignoreCase = true) == true
    val downloadedFilePath = activity.arguments["path"]?.toString()?.substringAfterLast('/')
        ?: activity.details?.substringAfter("file_path=")?.substringBefore(",")?.substringBefore("}")?.trim()

    // Inspect if this activity is reading code
    val isCodeReading = activity.toolName == "github_read_file" ||
            activity.toolName == "read_attached_file" ||
            (activity.arguments["path"]?.toString()?.matches(Regex(".*\\.(py|kt|java|js|ts|json|xml|html|css|sh|md|txt)$")) == true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerBackground)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .animateContentSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val toolIcon = when {
                        activity.toolName == "web_search" -> Icons.Default.Search
                        activity.toolName == "fetch_url" -> Icons.Default.Language
                        activity.toolName == "github_download_file" -> Icons.Default.Download
                        activity.toolName == "github_upload_local_file" -> Icons.Default.UploadFile
                        activity.toolName == "read_attached_file" -> Icons.Default.Description
                        activity.toolName == "terminal_execute" -> Icons.Default.Code
                        activity.toolName.startsWith("gmail") -> Icons.Default.Email
                        activity.toolName.startsWith("github") -> Icons.Default.Code
                        activity.toolName.startsWith("local_folder") -> Icons.Default.Description
                        activity.toolName.startsWith("local_") -> Icons.Default.Description
                        activity.toolName.startsWith("calendar") -> Icons.Default.Schedule
                        activity.toolName.startsWith("drive") -> Icons.Default.Download
                        activity.toolName == "universal_file_transfer" -> Icons.Default.Share
                        activity.toolName == "save_memory" -> Icons.Default.Check
                        activity.toolName == "get_weather" -> Icons.Default.WbSunny
                        activity.toolName == "wikipedia_search" -> Icons.AutoMirrored.Filled.MenuBook
                        activity.toolName == "exchange_rate" -> Icons.Default.Paid
                        activity.toolName == "current_time" -> Icons.Default.Schedule
                        else -> Icons.Default.Build
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = null,
                            tint = if (isRunning) SasukeCrimson else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    when (activity.status) {
                        ToolStatus.PREPARING, ToolStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = SasukeCrimson,
                                strokeWidth = 2.dp
                            )
                        }
                        ToolStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        ToolStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        ToolStatus.WAITING_CONFIRMATION -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Confirmation Required",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activity.title,
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (isRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(pulseAlpha.copy(alpha = 0.2f))
                                        .border(0.5.dp, pulseAlpha, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "WORKING",
                                        color = pulseAlpha,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (isRunning) "Processing: ${activity.subtitle}" else (activity.resultSummary ?: activity.subtitle),
                            color = if (activity.status == ToolStatus.WAITING_CONFIRMATION) Color(0xFFFBBF24) else TextMuted,
                            fontSize = 11.sp,
                            maxLines = if (expanded) 3 else 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        Text(
                            text = "View process",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand process details",
                        tint = if (isRunning) SasukeCrimson else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // DOWNLOAD FILE CARD (Promptly visible if downloaded file is present)
            if (isDownloadTool && activity.status == ToolStatus.SUCCESS) {
                Spacer(modifier = Modifier.height(8.dp))
                DownloadFileCard(
                    activity = activity,
                    onOpenPreview = { showPreviewCode = !showPreviewCode }
                )
            }

            // GMAIL SUMMARY CARD (Promptly visible for Gmail operations)
            if (activity.toolName.startsWith("gmail_") && activity.status == ToolStatus.SUCCESS) {
                Spacer(modifier = Modifier.height(8.dp))
                GmailSummaryCard(activity = activity)
            }

            // TERMINAL CARD (Promptly visible for Terminal execution)
            if (activity.toolName == "terminal_execute") {
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.terminal.TerminalActivityCard(activity = activity)
            }

            // Expanded details, code reader & arguments view
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    // Process Step Status Indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark)
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) SasukeCrimson else SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRunning) "STATUS: RUNNING PROCESS" else "STATUS: EXECUTION COMPLETED",
                                    color = if (isRunning) SasukeCrimson else SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tool: ${activity.toolName} • Status: ${activity.status.name}",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // ARGUMENTS SECTION
                    if (activity.arguments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ARGUMENTS",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = activity.arguments.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // CODE VIEWER SECTION (When reading a file or code is output)
                    val rawDetails = activity.details ?: ""
                    val codeContent = extractCodeFromDetails(rawDetails)

                    if (codeContent.isNotBlank() && (isCodeReading || showPreviewCode)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CODE CONTENT (${codeContent.lines().size} lines)",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", codeContent))
                                        copiedCode = true
                                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copiedCode) Icons.Default.Done else Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = if (copiedCode) SuccessGreen else TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (copiedCode) "Copied!" else "Copy Code",
                                    color = if (copiedCode) SuccessGreen else TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        val scrollStateV = rememberScrollState()
                        val scrollStateH = rememberScrollState()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0C0D12))
                                .border(1.dp, Color(0xFF1E202B), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .verticalScroll(scrollStateV)
                                    .horizontalScroll(scrollStateH)
                            ) {
                                val lines = codeContent.lines()
                                val lineNumWidth = "${lines.size}".length * 9

                                // Line numbers column
                                Column(modifier = Modifier.width(lineNumWidth.dp.coerceAtLeast(24.dp))) {
                                    lines.indices.forEach { index ->
                                        Text(
                                            text = "${index + 1}",
                                            color = TextMuted.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Code lines column
                                Text(
                                    text = codeContent,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else if (rawDetails.isNotBlank()) {
                        // Standard output view (full, no 300-char truncation)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROCESS OUTPUT",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Output", rawDetails))
                                        Toast.makeText(context, "Output copied", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy output",
                                    tint = TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", color = TextMuted, fontSize = 10.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .verticalScroll(scrollState)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = rawDetails,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Confirmation Action Card (when waiting for confirmation)
            if (activity.status == ToolStatus.WAITING_CONFIRMATION && activity.confirmationPayload != null) {
                val payload = activity.confirmationPayload
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (payload.isDestructive) "DESTRUCTIVE ACTION" else "APPROVAL REQUIRED",
                            color = if (payload.isDestructive) ErrorRed else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = payload.actionTitle,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Target: ${payload.target}",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )

                    if (!payload.previewTitle.isNullOrBlank()) {
                        Text(
                            text = payload.previewTitle,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!payload.previewContent.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF111116))
                                .padding(6.dp)
                        ) {
                            Text(
                                text = payload.previewContent,
                                color = TextPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 4
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onCancelAction?.invoke(activity) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onConfirmAction?.invoke(activity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (payload.isDestructive) ErrorRed else SasukeCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (payload.isDestructive) "Confirm Delete" else "Confirm & Execute", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Download File Card displaying downloaded file metadata and direct actions:
 * Open File, Share File, View Code.
 */
@Composable
fun DownloadFileCard(
    activity: ToolActivity,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawPath = activity.arguments["path"]?.toString() ?: "file"
    val fileName = rawPath.substringAfterLast('/')
    val ext = fileName.substringAfterLast('.', "").uppercase()

    // Determine target local file location
    val localFile = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
        fileName
    ).takeIf { it.exists() } ?: File(
        File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "downloads"),
        fileName
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12151E))
            .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File extension badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2433)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (ext.isNotBlank()) ext.take(4) else "FILE",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Saved to Downloads",
                            color = SuccessGreen,
                            fontSize = 11.sp
                        )
                        if (localFile.exists()) {
                            Text(
                                text = " • ${FileTransferManager.formatFileSize(localFile.length())}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (localFile.exists()) {
                            FileTransferManager.openFile(context, localFile)
                        } else {
                            Toast.makeText(context, "Saved in Downloads/$fileName", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        if (localFile.exists()) {
                            FileTransferManager.shareFile(context, localFile)
                        } else {
                            Toast.makeText(context, "File saved in Downloads", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = TextPrimary, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenPreview,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Code", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Extracts plain code text from tool details output.
 */
private fun extractCodeFromDetails(details: String): String {
    if (details.isBlank()) return ""
    // Handle map strings from ToolResult like {content=...}
    if (details.contains("content=")) {
        val extracted = details.substringAfter("content=").substringBeforeLast(", size=").substringBeforeLast("}")
        if (extracted.isNotBlank()) return extracted
    }
    return details
}

/**
 * Gmail Summary Card displaying email operation metadata and clear confirmation.
 */
@Composable
fun GmailSummaryCard(
    activity: ToolActivity,
    modifier: Modifier = Modifier
) {
    val toolName = activity.toolName
    val summary = activity.resultSummary ?: ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141724))
            .border(1.dp, Color(0xFF2E3550), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2338)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (toolName) {
                            "gmail_list_messages" -> "Gmail Search Results"
                            "gmail_read_message" -> "Email Content"
                            "gmail_send_message" -> "Email Dispatched"
                            "gmail_create_draft" -> "Draft Saved"
                            "gmail_delete_message" -> "Email Removed"
                            "gmail_modify_labels" -> "Labels Updated"
                            else -> "Gmail Activity"
                        },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary.ifBlank { "Completed successfully" },
                        color = Color(0xFFA5B4FC),
                        fontSize = 11.5.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

