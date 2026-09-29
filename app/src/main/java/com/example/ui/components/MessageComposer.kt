package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var showAttachmentDialog by remember { mutableStateOf(false) }

    val canSend = text.isNotBlank() && !isGenerating

    val borderColor by animateColorAsState(
        targetValue = when {
            isGenerating -> AccentCyan.copy(alpha = 0.5f)
            isFocused -> AccentIndigo.copy(alpha = 0.7f)
            else -> BorderSubtle
        },
        animationSpec = tween(250),
        label = "composer_border"
    )

    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = {
                Text(
                    text = "Agent Tools & Attachments",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "File attachments and external integrations (GitHub, Google Drive, Gmail, Terminal) are architected and will be activated in upcoming agent updates.\n\nSasukeX currently processes all conversational queries and code generation via Google Gemini.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Got it", color = AccentCyan)
                }
            },
            containerColor = SurfaceContainerHighDark
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceDark)
                .border(1.2.dp, borderColor, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attachment Button
            IconButton(
                onClick = { showAttachmentDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("attachment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach File",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Multiline Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 130.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Message SasukeX...",
                        color = TextMuted,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    textStyle = LocalTextStyle.current.copy(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(AccentCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag("message_input_field")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Dynamic Action Button (Send or Stop)
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    // Stop Button
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ErrorRed)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generating",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Send Button
                    IconButton(
                        onClick = {
                            if (canSend) {
                                onSend()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) AccentIndigo else SurfaceContainerDark
                            )
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (canSend) Color.White else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
