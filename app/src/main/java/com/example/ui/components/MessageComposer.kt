package com.example.ui.components

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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
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
    var showAttachmentDialog by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank() && !isGenerating

    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = {
                Text(
                    text = "Tools & Attachments",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Agent tools (GitHub, Google Drive, Gmail, Terminal) are architected and will be activated in upcoming updates.\n\nSasukeX is ready to generate and answer your prompts.",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("OK", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderMedium, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attachment Plus Icon
            IconButton(
                onClick = { showAttachmentDialog = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .testTag("attachment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add attachment",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Text Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp, max = 130.dp)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Message...",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    textStyle = LocalTextStyle.current.copy(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Circular Action Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.Black,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (canSend) onSend()
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (canSend) Color.White else SurfaceContainerHighDark)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Color.Black else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
