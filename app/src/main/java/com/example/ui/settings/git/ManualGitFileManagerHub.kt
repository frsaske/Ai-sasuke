package com.example.ui.settings.git

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.data.local.SecureStorageManager
import com.example.ui.settings.TestConnectionState
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualGitFileManagerHub(
    appSettingsManager: AppSettingsManager,
    secureStorageManager: SecureStorageManager,
    onTestGitHub: suspend () -> Result<String>,
    onCreateFile: suspend (owner: String, repo: String, path: String, content: String, message: String, branch: String?) -> Result<Map<String, Any?>>,
    onUpdateFile: suspend (owner: String, repo: String, path: String, content: String, message: String, sha: String, branch: String?) -> Result<Map<String, Any?>>,
    onDeleteFile: suspend (owner: String, repo: String, path: String, message: String, sha: String, branch: String?) -> Result<Map<String, Any?>>,
    onListFiles: suspend (owner: String, repo: String, path: String) -> Result<List<Map<String, Any?>>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isGitHubEnabled by remember { mutableStateOf(appSettingsManager.isGitHubEnabled()) }
    var isGitHubConnected by remember { mutableStateOf(secureStorageManager.hasGitHubToken()) }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    // Commit permission & safety toggles
    var requireCommitConfirmation by remember { mutableStateOf(appSettingsManager.isGitRequireCommitConfirmation()) }
    var showAgentActivity by remember { mutableStateOf(appSettingsManager.isShowAgentActivityEnabled()) }

    // Defaults
    var defaultOwner by remember { mutableStateOf(appSettingsManager.getGitDefaultOwner()) }
    var defaultRepo by remember { mutableStateOf(appSettingsManager.getGitDefaultRepo()) }
    var defaultBranch by remember { mutableStateOf(appSettingsManager.getGitDefaultBranch()) }

    // Manual Action Dialogs
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showEditFileDialog by remember { mutableStateOf(false) }
    var showDeleteFileDialog by remember { mutableStateOf(false) }
    var showBrowseFilesDialog by remember { mutableStateOf(false) }

    // Operation status banner
    var actionStatusMessage by remember { mutableStateOf<String?>(null) }
    var isActionInProgress by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Section Header with Enable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF238636).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Git & File Workspace Hub",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isGitHubConnected) "GitHub Connected • Manual & Auto Tools" else "Token Required",
                        color = if (isGitHubConnected) SuccessGreen else TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            Switch(
                checked = isGitHubEnabled,
                onCheckedChange = {
                    isGitHubEnabled = it
                    appSettingsManager.setGitHubEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF238636),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceContainerDark
                ),
                modifier = Modifier.size(width = 38.dp, height = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // GITHUB TOKEN CARD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(0.8.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Personal Access Token", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (isGitHubConnected) "Active" else "Not Configured",
                    color = if (isGitHubConnected) SuccessGreen else ErrorRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isGitHubConnected) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = secureStorageManager.getMaskedGitHubToken(),
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = gitHubTokenInput,
                onValueChange = {
                    gitHubTokenInput = it
                    testState = TestConnectionState.Idle
                },
                placeholder = {
                    Text(
                        text = if (isGitHubConnected) "Enter new ghp_ token to update..." else "ghp_...",
                        color = TextMuted,
                        fontSize = 12.5.sp
                    )
                },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderMedium,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (gitHubTokenInput.isNotBlank()) {
                            secureStorageManager.saveGitHubToken(gitHubTokenInput.trim())
                            gitHubTokenInput = ""
                            isGitHubConnected = true
                            testState = TestConnectionState.Idle
                            Toast.makeText(context, "GitHub token saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = gitHubTokenInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Text("Save Token", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            testState = TestConnectionState.Testing
                            val res = onTestGitHub()
                            testState = res.fold(
                                onSuccess = { TestConnectionState.Success(it) },
                                onFailure = { TestConnectionState.Failure(it.localizedMessage ?: "Failed") }
                            )
                        }
                    },
                    enabled = isGitHubConnected && testState !is TestConnectionState.Testing,
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    if (testState is TestConnectionState.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = TextPrimary)
                    } else {
                        Text("Test Live", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }

            AnimatedVisibility(visible = testState !is TestConnectionState.Idle) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    when (val s = testState) {
                        is TestConnectionState.Success -> Text(s.responseSnippet, color = SuccessGreen, fontSize = 11.5.sp)
                        is TestConnectionState.Failure -> Text(s.errorMessage, color = ErrorRed, fontSize = 11.5.sp)
                        else -> {}
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SAFETY & COMMIT PERMISSIONS ("commit se phele permisson etc")
        Text(
            text = "SAFETY & COMMIT PERMISSIONS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(0.8.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Permission Before Commit / Changes", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Ask for your confirmation before writing, editing, or deleting files", color = TextMuted, fontSize = 11.sp)
                }

                Switch(
                    checked = requireCommitConfirmation,
                    onCheckedChange = {
                        requireCommitConfirmation = it
                        appSettingsManager.setGitRequireCommitConfirmation(it)
                        appSettingsManager.setRequireConfirmationEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SasukeCrimson,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceContainerDark
                    ),
                    modifier = Modifier.size(width = 38.dp, height = 24.dp)
                )
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Tool Activity Cards", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Show live cards when files are read, downloaded, or updated", color = TextMuted, fontSize = 11.sp)
                }

                Switch(
                    checked = showAgentActivity,
                    onCheckedChange = {
                        showAgentActivity = it
                        appSettingsManager.setShowAgentActivityEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6366F1),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceContainerDark
                    ),
                    modifier = Modifier.size(width = 38.dp, height = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DEFAULT REPOSITORY CONFIGURATION
        Text(
            text = "DEFAULT REPOSITORY SETUP",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = defaultOwner,
                onValueChange = {
                    defaultOwner = it
                    appSettingsManager.setGitDefaultOwner(it)
                },
                placeholder = { Text("Owner (User)", color = TextMuted, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderMedium,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = defaultRepo,
                onValueChange = {
                    defaultRepo = it
                    appSettingsManager.setGitDefaultRepo(it)
                },
                placeholder = { Text("Repo Name", color = TextMuted, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderMedium,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = defaultBranch,
                onValueChange = {
                    defaultBranch = it
                    appSettingsManager.setGitDefaultBranch(it)
                },
                placeholder = { Text("Branch", color = TextMuted, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderMedium,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                modifier = Modifier.width(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MANUAL COMMANDS HUB ("add delete create edit files etc.. that we can do manually")
        Text(
            text = "MANUAL COMMANDS & FILE OPERATIONS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Create File
            ManualActionButton(
                icon = Icons.Default.Add,
                label = "Create File",
                onClick = { showCreateFileDialog = true }
            )

            // 2. Edit File
            ManualActionButton(
                icon = Icons.Default.Edit,
                label = "Edit File",
                onClick = { showEditFileDialog = true }
            )

            // 3. Delete File
            ManualActionButton(
                icon = Icons.Default.Delete,
                label = "Delete File",
                color = ErrorRed,
                onClick = { showDeleteFileDialog = true }
            )

            // 4. Browse / List Files
            ManualActionButton(
                icon = Icons.Default.Folder,
                label = "Browse Files",
                onClick = { showBrowseFilesDialog = true }
            )
        }

        // Live Action Status Banner
        actionStatusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = msg, color = TextPrimary, fontSize = 11.5.sp)
                }
            }
        }
    }

    // --- 1. MANUAL CREATE FILE DIALOG ---
    if (showCreateFileDialog) {
        var ownerInput by remember { mutableStateOf(defaultOwner) }
        var repoInput by remember { mutableStateOf(defaultRepo) }
        var pathInput by remember { mutableStateOf("") }
        var contentInput by remember { mutableStateOf("") }
        var commitMsgInput by remember { mutableStateOf("") }
        var branchInput by remember { mutableStateOf(defaultBranch) }

        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Manual Create / Add File", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            placeholder = { Text("Owner", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            placeholder = { Text("Repo", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        placeholder = { Text("File path (e.g. notes/todo.txt or app.py)", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        placeholder = { Text("File content...", color = TextMuted, fontSize = 12.sp) },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = commitMsgInput,
                        onValueChange = { commitMsgInput = it },
                        placeholder = { Text("Commit message (optional)", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val owner = ownerInput.trim().ifBlank { defaultOwner }
                        val repo = repoInput.trim().ifBlank { defaultRepo }
                        val path = pathInput.trim()
                        val msg = commitMsgInput.ifBlank { "Create $path via manual setup" }

                        if (owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()) {
                            isActionInProgress = true
                            actionStatusMessage = "Creating $path in $owner/$repo..."
                            showCreateFileDialog = false

                            coroutineScope.launch {
                                val res = onCreateFile(owner, repo, path, contentInput, msg, branchInput.ifBlank { null })
                                isActionInProgress = false
                                actionStatusMessage = res.fold(
                                    onSuccess = { "Successfully created $path in $owner/$repo" },
                                    onFailure = { "Error: ${it.localizedMessage}" }
                                )
                            }
                        } else {
                            Toast.makeText(context, "Owner, Repo, and Path are required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Create File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = SurfaceElevated
        )
    }

    // --- 2. MANUAL EDIT FILE DIALOG ---
    if (showEditFileDialog) {
        var ownerInput by remember { mutableStateOf(defaultOwner) }
        var repoInput by remember { mutableStateOf(defaultRepo) }
        var pathInput by remember { mutableStateOf("") }
        var contentInput by remember { mutableStateOf("") }
        var shaInput by remember { mutableStateOf("") }
        var commitMsgInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditFileDialog = false },
            title = { Text("Manual Edit / Update File", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            placeholder = { Text("Owner", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            placeholder = { Text("Repo", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        placeholder = { Text("File path (e.g. README.md)", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = shaInput,
                        onValueChange = { shaInput = it },
                        placeholder = { Text("File SHA (optional, auto-resolved if blank)", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        placeholder = { Text("New file content...", color = TextMuted, fontSize = 12.sp) },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val owner = ownerInput.trim().ifBlank { defaultOwner }
                        val repo = repoInput.trim().ifBlank { defaultRepo }
                        val path = pathInput.trim()
                        val msg = commitMsgInput.ifBlank { "Update $path via manual setup" }

                        if (owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()) {
                            isActionInProgress = true
                            actionStatusMessage = "Updating $path in $owner/$repo..."
                            showEditFileDialog = false

                            coroutineScope.launch {
                                val res = onUpdateFile(owner, repo, path, contentInput, msg, shaInput.trim(), defaultBranch.ifBlank { null })
                                isActionInProgress = false
                                actionStatusMessage = res.fold(
                                    onSuccess = { "Successfully updated $path" },
                                    onFailure = { "Error: ${it.localizedMessage}" }
                                )
                            }
                        } else {
                            Toast.makeText(context, "Owner, Repo, and Path are required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditFileDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = SurfaceElevated
        )
    }

    // --- 3. MANUAL DELETE FILE DIALOG ---
    if (showDeleteFileDialog) {
        var ownerInput by remember { mutableStateOf(defaultOwner) }
        var repoInput by remember { mutableStateOf(defaultRepo) }
        var pathInput by remember { mutableStateOf("") }
        var shaInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDeleteFileDialog = false },
            title = { Text("Manual Delete File", color = ErrorRed, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permanently delete a file from the repository.", color = TextMuted, fontSize = 12.5.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            placeholder = { Text("Owner", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            placeholder = { Text("Repo", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        placeholder = { Text("File path to delete", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = shaInput,
                        onValueChange = { shaInput = it },
                        placeholder = { Text("File blob SHA", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val owner = ownerInput.trim().ifBlank { defaultOwner }
                        val repo = repoInput.trim().ifBlank { defaultRepo }
                        val path = pathInput.trim()

                        if (owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()) {
                            isActionInProgress = true
                            actionStatusMessage = "Deleting $path from $owner/$repo..."
                            showDeleteFileDialog = false

                            coroutineScope.launch {
                                val res = onDeleteFile(owner, repo, path, "Delete $path via manual setup", shaInput.trim(), defaultBranch.ifBlank { null })
                                isActionInProgress = false
                                actionStatusMessage = res.fold(
                                    onSuccess = { "Deleted $path from $owner/$repo" },
                                    onFailure = { "Error: ${it.localizedMessage}" }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Delete File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFileDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = SurfaceElevated
        )
    }

    // --- 4. BROWSE / LIST FILES DIALOG ---
    if (showBrowseFilesDialog) {
        var ownerInput by remember { mutableStateOf(defaultOwner) }
        var repoInput by remember { mutableStateOf(defaultRepo) }
        var pathInput by remember { mutableStateOf("") }
        var filesList by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
        var isFetching by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBrowseFilesDialog = false },
            title = { Text("Browse Repository Files", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = ownerInput,
                            onValueChange = { ownerInput = it },
                            placeholder = { Text("Owner", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            placeholder = { Text("Repo", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            placeholder = { Text("Path (optional)", color = TextMuted, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val owner = ownerInput.trim().ifBlank { defaultOwner }
                                val repo = repoInput.trim().ifBlank { defaultRepo }
                                if (owner.isNotBlank() && repo.isNotBlank()) {
                                    isFetching = true
                                    coroutineScope.launch {
                                        val res = onListFiles(owner, repo, pathInput.trim())
                                        isFetching = false
                                        filesList = res.getOrDefault(emptyList())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(7.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.Black)
                            } else {
                                Text("Fetch")
                            }
                        }
                    }

                    if (filesList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filesList.take(8).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item["name"]?.toString() ?: "",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = item["type"]?.toString() ?: "",
                                        color = TextMuted,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowseFilesDialog = false }) { Text("Close", color = TextPrimary) }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
private fun ManualActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.height(34.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = color)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, color = color)
    }
}
