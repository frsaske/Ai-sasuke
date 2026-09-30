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
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorBorder
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
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
                        if (message.content.isNotBlank()) {
                            MarkdownContent(content = message.content)
                        }

                        if (message.isStreaming) {
                            TypingIndicator()
                        }
                    }

                    // Action buttons
                    if (!message.isStreaming && !message.isError && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
}
