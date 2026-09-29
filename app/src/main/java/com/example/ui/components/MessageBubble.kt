package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorBorder
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg
import com.example.ui.theme.UserBubbleBorder
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

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
            // User Message
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                        .background(UserBubbleBg)
                        .border(1.dp, UserBubbleBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showContextMenu = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("user_message_bubble")
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Context menu for User message
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    modifier = Modifier.background(SurfaceContainerHighDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy Text", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Message", message.content))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Message"))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ErrorRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        }
                    )
                }
            }
        } else {
            // Assistant Message
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // SasukeX Avatar Emblem
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighDark)
                        .border(1.dp, AccentCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SX",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
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
                    // Content or Error
                    if (message.isError) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ErrorContainer)
                                .border(1.dp, ErrorBorder, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generation Notice",
                                    color = ErrorRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.errorMessage ?: message.content.ifBlank { "An unexpected issue occurred while requesting Gemini." },
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AccentCyan
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AccentCyan)),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Request", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Regular / Streaming Assistant Content
                        if (message.content.isNotBlank()) {
                            MarkdownContent(content = message.content)
                        }

                        // If streaming and empty or active
                        if (message.isStreaming) {
                            TypingIndicator(modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    // Bottom Action Bar for Assistant
                    if (!message.isStreaming && !message.isError && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("SasukeX Response", message.content))
                                    copied = true
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_response_button")
                            ) {
                                if (copied) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Copied",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Response",
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message.content)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Response"))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = formattedTime,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Context Menu
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        modifier = Modifier.background(SurfaceContainerHighDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Response", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SasukeX Response", message.content))
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Regenerate", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                showContextMenu = false
                                onRegenerate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = ErrorRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
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
}
