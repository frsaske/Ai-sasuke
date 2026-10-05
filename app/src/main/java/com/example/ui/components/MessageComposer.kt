package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.AttachedFileInfo
import com.example.util.FileTransferManager
import kotlinx.coroutines.launch

data class AgentActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val promptTemplate: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    isGenerating: Boolean,
    onSend: (AttachedFileInfo?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showActionSheet by remember { mutableStateOf(false) }
    var attachedFile by remember { mutableStateOf<AttachedFileInfo?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val canSend = (text.isNotBlank() || attachedFile != null) && !isGenerating

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val res = FileTransferManager.saveAttachedFile(context, uri)
            res.fold(
                onSuccess = { info ->
                    attachedFile = info
                    Toast.makeText(context, "Attached: ${info.name} (${info.formattedSize})", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    Toast.makeText(context, err.message ?: "Failed to attach file", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val agentActions = remember {
        listOf(
            // GitHub Actions
            AgentActionItem(
                title = "Download File from Repo",
                subtitle = "Download any file (e.g. main.py) to Android device",
                icon = Icons.Default.Download,
                promptTemplate = "Download file 'main.py' from repository 'frsaske/Ai-sasuke'",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "Upload Attached File to Git",
                subtitle = "Push attached file to any folder in repository",
                icon = Icons.Default.UploadFile,
                promptTemplate = "Upload attached file to repository 'frsaske/Ai-sasuke' in folder 'session1'",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "List GitHub Repositories",
                subtitle = "View all your accessible repositories",
                icon = Icons.Default.Code,
                promptTemplate = "List my accessible GitHub repositories.",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "Inspect Repository Tree & Files",
                subtitle = "Browse directory contents and file structure",
                icon = Icons.Default.Code,
                promptTemplate = "List files in repository 'frsaske/Ai-sasuke'",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "Read Code from Repo",
                subtitle = "Examine source code from repository",
                icon = Icons.Default.Code,
                promptTemplate = "Read file 'main.py' from repository 'frsaske/Ai-sasuke'",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "Recent Commits",
                subtitle = "Check commit history for repository",
                icon = Icons.Default.Code,
                promptTemplate = "Show recent commits for repository frsaske/Ai-sasuke",
                category = "GITHUB"
            ),
            AgentActionItem(
                title = "Create GitHub Issue",
                subtitle = "Open a new issue with title and description",
                icon = Icons.Default.Code,
                promptTemplate = "Create a GitHub issue in owner/repo with title 'Fix bug' and description 'Issue details'",
                category = "GITHUB"
            ),

            // Gmail Actions
            AgentActionItem(
                title = "Check Inbox / Unread Emails",
                subtitle = "Query latest unread messages in Gmail",
                icon = Icons.Default.Email,
                promptTemplate = "Check my Gmail inbox for any recent unread emails.",
                category = "GMAIL"
            ),
            AgentActionItem(
                title = "Send an Email",
                subtitle = "Compose and send email via Gmail connector",
                icon = Icons.Default.Email,
                promptTemplate = "Send an email using Gmail to recipient@example.com with subject 'Update' and body 'Hello, here is my update.'",
                category = "GMAIL"
            ),
            AgentActionItem(
                title = "Create Email Draft",
                subtitle = "Draft an email without sending immediately",
                icon = Icons.Default.Email,
                promptTemplate = "Create a draft email in Gmail to team@example.com with subject 'Sprint review' and body 'Notes from sprint.'",
                category = "GMAIL"
            ),
            AgentActionItem(
                title = "Search Gmail by Query",
                subtitle = "Filter messages by sender, subject, or keyword",
                icon = Icons.Default.Email,
                promptTemplate = "Search my Gmail messages for 'from:github'",
                category = "GMAIL"
            ),

            // Web & Real-Time Knowledge
            AgentActionItem(
                title = "Live Web Search (Tavily)",
                subtitle = "Query latest news and verified information",
                icon = Icons.Default.Language,
                promptTemplate = "Search the live web for ",
                category = "WEB SEARCH"
            ),
            AgentActionItem(
                title = "Fetch & Read Webpage",
                subtitle = "Extract text content from any URL",
                icon = Icons.Default.Language,
                promptTemplate = "Read and extract contents from URL: ",
                category = "WEB SEARCH"
            ),

            // Utilities & Knowledge
            AgentActionItem(
                title = "Weather Forecast",
                subtitle = "Real-time global weather conditions",
                icon = Icons.Default.WbSunny,
                promptTemplate = "What is the current weather and forecast for ",
                category = "UTILITIES"
            ),
            AgentActionItem(
                title = "Wikipedia Search",
                subtitle = "Factual encyclopedia lookup",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                promptTemplate = "Search Wikipedia for ",
                category = "UTILITIES"
            ),
            AgentActionItem(
                title = "Currency Exchange",
                subtitle = "Live ECB foreign exchange conversion",
                icon = Icons.Default.Paid,
                promptTemplate = "Convert 100 USD to EUR using live exchange rates",
                category = "UTILITIES"
            ),
            AgentActionItem(
                title = "World Clock",
                subtitle = "Current time in any timezone",
                icon = Icons.Default.Schedule,
                promptTemplate = "What is the current time and timezone for Tokyo, Japan?",
                category = "UTILITIES"
            )
        )
    }

    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceElevated,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextMuted.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Actions & Tools",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Attach files or pick an action shortcut",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                showActionSheet = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TOP FEATURE: ATTACH FILE (< 5MB)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B2234))
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable {
                            coroutineScope.launch {
                                sheetState.hide()
                                showActionSheet = false
                                filePickerLauncher.launch("*/*")
                            }
                        }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach File",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Attach File (< 5MB)",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2563EB).copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "DEVICE",
                                        color = Color(0xFF60A5FA),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Attach creds.json, code, or any document to upload or send",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = agentActions.groupBy { it.category }
                    grouped.forEach { (category, items) ->
                        item {
                            Text(
                                text = category,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        onTextChanged(item.promptTemplate)
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            showActionSheet = false
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = item.subtitle,
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ATTACHED FILE CHIP / PREVIEW
        AnimatedVisibility(visible = attachedFile != null) {
            if (attachedFile != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141926))
                            .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E283D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachedFile!!.name,
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "${attachedFile!!.formattedSize} • Ready to send or upload to git",
                                color = Color(0xFF93C5FD),
                                fontSize = 10.5.sp
                            )
                        }

                        IconButton(
                            onClick = { attachedFile = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
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
                // Attachment / Tools Plus Icon
                IconButton(
                    onClick = { showActionSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .testTag("attachment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agent Tools and Actions",
                        tint = if (attachedFile != null) Color(0xFF60A5FA) else TextSecondary,
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
                            text = if (attachedFile != null) "Instructions for ${attachedFile!!.name}..." else "Message...",
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
                                if (canSend) {
                                    val fileToSend = attachedFile
                                    attachedFile = null
                                    onSend(fileToSend)
                                }
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
}
