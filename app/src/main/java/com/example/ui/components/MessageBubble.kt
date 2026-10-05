package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorBorder
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg
import com.example.ui.theme.UserBubbleBorder
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.agent.model.ToolActivity
import com.example.ui.agent.AgentActivityCard
import com.example.ui.agent.SourceCitationsView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmAction: ((ToolActivity) -> Unit)? = null,
    onCancelAction: ((ToolActivity) -> Unit)? = null
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val isUser = message.role == MessageRole.USER
    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User Message Bubble
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(UserBubbleBg)
                        .border(1.dp, UserBubbleBorder, RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showContextMenu = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                        .testTag("user_message_bubble")
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy", color = TextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Message", message.content))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ErrorRed, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        }
                    )
                }
            }
        } else {
            // Assistant Message: Seamless full-bleed reading
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Minimal clean avatar
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighDark)
                        .border(1.dp, BorderMedium, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showContextMenu = true }
                        )
                        .testTag("assistant_message_bubble")
                ) {
                    if (message.isError) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErrorContainer)
                                .border(1.dp, ErrorBorder, RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unable to complete request",
                                    color = ErrorRed,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.errorMessage ?: message.content.ifBlank { "An unexpected error occurred." },
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = ButtonDefaults.outlinedButtonBorder(true),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // AGENT ACTIVITIES
                        if (message.activities.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                message.activities.forEach { act ->
                                    AgentActivityCard(
                                        activity = act,
                                        onConfirmAction = onConfirmAction,
                                        onCancelAction = onCancelAction
                                    )
                                }
                            }
                        }

                        if (message.content.isNotBlank()) {
                            MarkdownContent(content = message.content)
                        }

                        // SOURCE CITATIONS
                        if (message.sources.isNotEmpty()) {
                            SourceCitationsView(sources = message.sources)
                        }

                        if (message.isStreaming && message.activities.none { it.status == com.example.agent.model.ToolStatus.WAITING_CONFIRMATION }) {
                            TypingIndicator()
                        }
                    }

                    // Token usage badge below reply
                    val totalTokens = message.totalTokens ?: (if ((message.promptTokens ?: 0) + (message.candidatesTokens ?: 0) > 0) (message.promptTokens ?: 0) + (message.candidatesTokens ?: 0) else null)
                    if (!message.isStreaming && !message.isError && totalTokens != null && totalTokens > 0) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceDark,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier.testTag("token_usage_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DataUsage,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$totalTokens tokens (prompt: ${message.promptTokens ?: 0} • response: ${message.candidatesTokens ?: 0})",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Action buttons
                    if (!message.isStreaming && !message.isError && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Response", message.content))
                                    copied = true
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("copy_response_button")
                            ) {
                                if (copied) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Copied",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message.content)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share"))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Raw AI Reply JSON Button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerDark,
                                border = BorderStroke(0.8.dp, BorderMedium),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showJsonDialog = true }
                                    .testTag("json_dialog_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "JSON Response",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "JSON",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = formattedTime,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy", color = TextPrimary, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Response", message.content))
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Regenerate", color = TextPrimary, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showContextMenu = false
                                onRegenerate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = ErrorRed, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showContextMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showJsonDialog) {
        var jsonCopied by remember { mutableStateOf(false) }
        val rawJsonText = remember(message) {
            message.rawResponseJson?.takeIf { it.isNotBlank() } ?: buildFallbackJson(message)
        }

        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = SasukeCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Complete AI Response JSON",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Raw payload returned by Gemini API for this reply:",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = rawJsonText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Response JSON", rawJsonText))
                        jsonCopied = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (jsonCopied) SuccessGreen else SasukeCrimson
                    ),
                    modifier = Modifier.testTag("copy_json_button")
                ) {
                    Icon(
                        imageVector = if (jsonCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (jsonCopied) "Copied!" else "Copy JSON",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

private fun buildFallbackJson(message: ChatMessage): String {
    val obj = org.json.JSONObject()
    obj.put("id", message.id)
    obj.put("conversationId", message.conversationId)
    obj.put("role", message.role.name.lowercase())
    obj.put("timestamp", message.timestamp)
    obj.put("content", message.content)
    if (message.promptTokens != null || message.candidatesTokens != null || message.totalTokens != null) {
        val usage = org.json.JSONObject()
        usage.put("promptTokenCount", message.promptTokens ?: 0)
        usage.put("candidatesTokenCount", message.candidatesTokens ?: 0)
        usage.put("totalTokenCount", message.totalTokens ?: 0)
        obj.put("usageMetadata", usage)
    }
    if (message.sources.isNotEmpty()) {
        val sourcesArr = org.json.JSONArray()
        for (s in message.sources) {
            sourcesArr.put(org.json.JSONObject().apply {
                put("title", s.title)
                put("url", s.url)
            })
        }
        obj.put("sources", sourcesArr)
    }
    return obj.toString(2)
}
