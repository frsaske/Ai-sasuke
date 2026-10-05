package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.data.local.SecureStorageManager
import com.example.data.model.MemoryEntity
import com.example.ui.settings.git.ManualGitFileManagerHub
import com.example.ui.settings.memory.MemoryManagementHub
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
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
import com.example.util.LocalFileItem
import kotlinx.coroutines.launch

sealed class TestConnectionState {
    data object Idle : TestConnectionState()
    data object Testing : TestConnectionState()
    data class Success(val responseSnippet: String) : TestConnectionState()
    data class Failure(val errorMessage: String) : TestConnectionState()
}

enum class SettingsFolderTab {
    ALL,
    AI_SYSTEM,
    TERMUX,
    LOCAL_STORAGE,
    GIT_MANAGEMENT,
    CALENDAR,
    DRIVE,
    INTEGRATIONS,
    DATA_STORAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentApiKeyMasked: String,
    hasConfiguredKey: Boolean,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: suspend (String, String) -> Result<String>,
    currentSystemPrompt: String,
    onSaveSystemPrompt: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
    currentModel: String,
    onSelectModel: (String) -> Unit,
    onClearAllChats: () -> Unit,
    onResetAllSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    appSettingsManager: AppSettingsManager? = null,
    secureStorageManager: SecureStorageManager? = null,
    memories: List<MemoryEntity> = emptyList(),
    onSaveMemory: (String, String) -> Unit = { _, _ -> },
    onUpdateMemory: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteMemory: (String) -> Unit = {},
    onToggleMemory: (String, Boolean) -> Unit = { _, _ -> },
    onClearAllMemories: () -> Unit = {},
    exportTextProvider: suspend () -> String = { "" },
    exportJsonProvider: suspend () -> String = { "" },
    onTestTavily: (suspend (String) -> Result<String>)? = null,
    onTestGitHub: (suspend () -> Result<String>)? = null,
    onTestGmail: (suspend () -> Result<String>)? = null,
    onCreateFile: (suspend (String, String, String, String, String, String?) -> Result<Map<String, Any?>>)? = null,
    onUpdateFile: (suspend (String, String, String, String, String, String, String?) -> Result<Map<String, Any?>>)? = null,
    onDeleteFile: (suspend (String, String, String, String, String, String?) -> Result<Map<String, Any?>>)? = null,
    onListFiles: (suspend (String, String, String) -> Result<List<Map<String, Any?>>>)? = null,
    onClearLocalWorkspace: (suspend () -> Boolean)? = null,
    onListLocalFiles: (suspend () -> Result<List<LocalFileItem>>)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Quick Folder Filter Tab
    var activeTab by remember { mutableStateOf(SettingsFolderTab.ALL) }

    // Accordion Expansion States for Folders
    var isAiSystemExpanded by remember { mutableStateOf(true) }
    var isTermuxExpanded by remember { mutableStateOf(true) }
    var isLocalStorageExpanded by remember { mutableStateOf(true) }
    var isGitManagementExpanded by remember { mutableStateOf(true) }
    var isCalendarExpanded by remember { mutableStateOf(true) }
    var isDriveExpanded by remember { mutableStateOf(true) }
    var isIntegrationsExpanded by remember { mutableStateOf(true) }
    var isDataStorageExpanded by remember { mutableStateOf(true) }

    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var systemPromptInput by remember { mutableStateOf(currentSystemPrompt) }
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showClearChatsDialog by remember { mutableStateOf(false) }
    var showClearWorkspaceDialog by remember { mutableStateOf(false) }

    // Local workspace files preview
    var localWorkspaceFilesCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(currentSystemPrompt) {
        systemPromptInput = currentSystemPrompt
    }

    LaunchedEffect(Unit) {
        if (onListLocalFiles != null) {
            val res = onListLocalFiles()
            localWorkspaceFilesCount = res.getOrNull()?.size ?: 0
        }
    }

    // Reset settings confirmation
    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Restore default model, system prompt, and feature toggles? API keys and memories are safely preserved.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllSettings()
                        showResetSettingsDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Settings restored to defaults")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Clear chats confirmation
    if (showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatsDialog = false },
            title = { Text("Clear All Chats", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Delete all saved chat history from this device? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllChats()
                        showClearChatsDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("All chat history deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Clear local workspace confirmation
    if (showClearWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showClearWorkspaceDialog = false },
            title = { Text("Clear Workspace Files", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Permanently delete all files created in the app's local workspace?",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            onClearLocalWorkspace?.invoke()
                            localWorkspaceFilesCount = 0
                            showClearWorkspaceDialog = false
                            snackbarHostState.showSnackbar("Local workspace cleared")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWorkspaceDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings & Preferences",
                            color = TextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Executive System Console",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // FOLDER SELECTOR TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsFolderChip(
                    label = "All Modules",
                    isSelected = activeTab == SettingsFolderTab.ALL,
                    onClick = { activeTab = SettingsFolderTab.ALL },
                    icon = Icons.Default.Tune
                )
                SettingsFolderChip(
                    label = "AI Engine",
                    isSelected = activeTab == SettingsFolderTab.AI_SYSTEM,
                    onClick = { activeTab = SettingsFolderTab.AI_SYSTEM },
                    icon = Icons.Default.AutoAwesome
                )
                SettingsFolderChip(
                    label = "Termux Terminal",
                    isSelected = activeTab == SettingsFolderTab.TERMUX,
                    onClick = { activeTab = SettingsFolderTab.TERMUX },
                    icon = Icons.Default.Terminal
                )
                SettingsFolderChip(
                    label = "Local Storage",
                    isSelected = activeTab == SettingsFolderTab.LOCAL_STORAGE,
                    onClick = { activeTab = SettingsFolderTab.LOCAL_STORAGE },
                    icon = Icons.Default.Folder
                )
                SettingsFolderChip(
                    label = "Git & GitHub",
                    isSelected = activeTab == SettingsFolderTab.GIT_MANAGEMENT,
                    onClick = { activeTab = SettingsFolderTab.GIT_MANAGEMENT },
                    icon = Icons.Default.Code
                )
                SettingsFolderChip(
                    label = "Calendar",
                    isSelected = activeTab == SettingsFolderTab.CALENDAR,
                    onClick = { activeTab = SettingsFolderTab.CALENDAR },
                    icon = Icons.Default.DateRange
                )
                SettingsFolderChip(
                    label = "Google Drive",
                    isSelected = activeTab == SettingsFolderTab.DRIVE,
                    onClick = { activeTab = SettingsFolderTab.DRIVE },
                    icon = Icons.Default.Cloud
                )
                SettingsFolderChip(
                    label = "External APIs",
                    isSelected = activeTab == SettingsFolderTab.INTEGRATIONS,
                    onClick = { activeTab = SettingsFolderTab.INTEGRATIONS },
                    icon = Icons.Default.Extension
                )
                SettingsFolderChip(
                    label = "System Data",
                    isSelected = activeTab == SettingsFolderTab.DATA_STORAGE,
                    onClick = { activeTab = SettingsFolderTab.DATA_STORAGE },
                    icon = Icons.Default.Storage
                )
            }

            // =========================================================================
            // FOLDER 1: AI SYSTEM & CORE INTELLIGENCE
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.AI_SYSTEM) {
                SettingsFolderCard(
                    folderTitle = "AI System & Core Intelligence",
                    folderSubtitle = "Gemini API, Models, System Prompt & Long-term Memory",
                    badge = "Core AI",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = Color(0xFF818CF8),
                    isExpanded = isAiSystemExpanded,
                    onToggleExpand = { isAiSystemExpanded = !isAiSystemExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 1. GEMINI API KEY
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gemini API Key", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
                                Text(
                                    text = if (hasConfiguredKey) "Configured (Active)" else "Not set",
                                    color = if (hasConfiguredKey) SuccessGreen else TextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (hasConfiguredKey) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = currentApiKeyMasked, color = TextMuted, fontSize = 11.5.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                    apiKeyInput = it
                                    testState = TestConnectionState.Idle
                                },
                                placeholder = {
                                    Text(
                                        text = if (hasConfiguredKey) "Enter new key to update..." else "Paste Gemini API Key (AIza... or AQ...)",
                                        color = TextMuted,
                                        fontSize = 12.5.sp
                                    )
                                },
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                                modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (apiKeyInput.isNotBlank()) {
                                            onSaveApiKey(apiKeyInput.trim())
                                            apiKeyInput = ""
                                            testState = TestConnectionState.Idle
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("API key saved successfully")
                                            }
                                        }
                                    },
                                    enabled = apiKeyInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.weight(1f).height(34.dp).testTag("save_api_key_button")
                                ) {
                                    Text("Save", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (hasConfiguredKey) {
                                    OutlinedButton(
                                        onClick = {
                                            onClearApiKey()
                                            apiKeyInput = ""
                                            testState = TestConnectionState.Idle
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("API key cleared")
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                        shape = RoundedCornerShape(7.dp),
                                        modifier = Modifier.height(34.dp).testTag("clear_api_key_button")
                                    ) {
                                        Text("Clear", fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            testState = TestConnectionState.Testing
                                            val keyToTest = apiKeyInput.ifBlank { "" }
                                            val result = onTestConnection(keyToTest, currentModel)
                                            testState = result.fold(
                                                onSuccess = { snippet -> TestConnectionState.Success(snippet) },
                                                onFailure = { err -> TestConnectionState.Failure(err.localizedMessage ?: "Failed") }
                                            )
                                        }
                                    },
                                    enabled = (hasConfiguredKey || apiKeyInput.isNotBlank()) && testState !is TestConnectionState.Testing,
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.height(34.dp).testTag("test_api_connection_button")
                                ) {
                                    if (testState is TestConnectionState.Testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = TextPrimary)
                                    } else {
                                        Text("Test", fontSize = 12.sp, color = TextPrimary)
                                    }
                                }
                            }

                            AnimatedVisibility(visible = testState !is TestConnectionState.Idle) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    when (val s = testState) {
                                        is TestConnectionState.Success -> Text("✓ Connection verified successfully.", color = SuccessGreen, fontSize = 11.5.sp)
                                        is TestConnectionState.Failure -> Text("✕ ${s.errorMessage}", color = ErrorRed, fontSize = 11.5.sp)
                                        else -> {}
                                    }
                                }
                            }
                        }

                        // 2. MODEL SELECTION
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        ) {
                            AppSettingsManager.AVAILABLE_MODELS.forEachIndexed { index, modelOption ->
                                val isSelected = modelOption.id == currentModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectModel(modelOption.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("model_option_${modelOption.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = modelOption.displayName,
                                                color = TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = 13.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SurfaceElevated)
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(text = modelOption.badge, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(text = modelOption.description, color = TextMuted, fontSize = 11.5.sp)
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (index < AppSettingsManager.AVAILABLE_MODELS.size - 1) {
                                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                                }
                            }
                        }

                        // 3. SYSTEM PROMPT
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("System Prompt & Instructions", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Reset Default",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onResetSystemPrompt()
                                            systemPromptInput = AppSettingsManager.DEFAULT_SYSTEM_PROMPT
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Prompt reset to default") }
                                        }
                                        .testTag("reset_system_prompt_button")
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = systemPromptInput,
                                onValueChange = {
                                    systemPromptInput = it
                                    onSaveSystemPrompt(it)
                                },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(7.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BorderMedium,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = SurfaceElevated,
                                    unfocusedContainerColor = SurfaceElevated
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("system_prompt_input")
                            )
                        }

                        // 4. LONG-TERM MEMORY SYSTEM HUB
                        MemoryManagementHub(
                            memories = memories,
                            onSaveMemory = onSaveMemory,
                            onUpdateMemory = onUpdateMemory,
                            onDeleteMemory = onDeleteMemory,
                            onToggleMemory = onToggleMemory,
                            onClearAllMemories = onClearAllMemories,
                            exportTextProvider = exportTextProvider,
                            exportJsonProvider = exportJsonProvider,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER: TERMUX TERMINAL ENVIRONMENT
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.TERMUX) {
                SettingsFolderCard(
                    folderTitle = "Termux Terminal Environment",
                    folderSubtitle = "Local command runner, RUN_COMMAND permission & safety",
                    badge = "Terminal",
                    icon = Icons.Default.Terminal,
                    iconTint = Color(0xFF58A6FF),
                    isExpanded = isTermuxExpanded,
                    onToggleExpand = { isTermuxExpanded = !isTermuxExpanded }
                ) {
                    if (appSettingsManager != null) {
                        com.example.ui.settings.sections.TermuxSettingsSection(
                            appSettingsManager = appSettingsManager,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER: INTELLIGENT LOCAL STORAGE AGENT
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.LOCAL_STORAGE) {
                SettingsFolderCard(
                    folderTitle = "Intelligent Local Storage Agent",
                    folderSubtitle = "SAF roots, deterministic fast indexing & zero-token search",
                    badge = "Storage",
                    icon = Icons.Default.Folder,
                    iconTint = Color(0xFFE5A50A),
                    isExpanded = isLocalStorageExpanded,
                    onToggleExpand = { isLocalStorageExpanded = !isLocalStorageExpanded }
                ) {
                    if (appSettingsManager != null) {
                        com.example.ui.settings.sections.LocalStorageSettingsSection(
                            appSettingsManager = appSettingsManager,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER 2: GIT & VERSION CONTROL MANAGEMENT
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.GIT_MANAGEMENT) {
                SettingsFolderCard(
                    folderTitle = "Git & Version Control Management",
                    folderSubtitle = "GitHub credentials, commit permissions & manual file commands",
                    badge = "Version Control",
                    icon = Icons.Default.Code,
                    iconTint = SuccessGreen,
                    isExpanded = isGitManagementExpanded,
                    onToggleExpand = { isGitManagementExpanded = !isGitManagementExpanded }
                ) {
                    if (appSettingsManager != null && secureStorageManager != null) {
                        ManualGitFileManagerHub(
                            appSettingsManager = appSettingsManager,
                            secureStorageManager = secureStorageManager,
                            onTestGitHub = onTestGitHub ?: { Result.failure(Exception("GitHub test unavailable")) },
                            onCreateFile = onCreateFile ?: { _, _, _, _, _, _ -> Result.failure(Exception("Create file unavailable")) },
                            onUpdateFile = onUpdateFile ?: { _, _, _, _, _, _, _ -> Result.failure(Exception("Update file unavailable")) },
                            onDeleteFile = onDeleteFile ?: { _, _, _, _, _, _ -> Result.failure(Exception("Delete file unavailable")) },
                            onListFiles = onListFiles ?: { _, _, _ -> Result.failure(Exception("List files unavailable")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER: GOOGLE CALENDAR AGENT
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.CALENDAR) {
                SettingsFolderCard(
                    folderTitle = "Google Calendar Agent",
                    folderSubtitle = "Schedule queries, event creation & free time finder",
                    badge = "Calendar",
                    icon = Icons.Default.DateRange,
                    iconTint = Color(0xFF34D399),
                    isExpanded = isCalendarExpanded,
                    onToggleExpand = { isCalendarExpanded = !isCalendarExpanded }
                ) {
                    if (appSettingsManager != null) {
                        com.example.ui.settings.sections.GoogleCalendarSettingsSection(
                            appSettingsManager = appSettingsManager,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER: GOOGLE DRIVE AGENT
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.DRIVE) {
                SettingsFolderCard(
                    folderTitle = "Google Drive Agent",
                    folderSubtitle = "Cloud files, text documents, search & direct transfer",
                    badge = "Drive",
                    icon = Icons.Default.Cloud,
                    iconTint = Color(0xFF60A5FA),
                    isExpanded = isDriveExpanded,
                    onToggleExpand = { isDriveExpanded = !isDriveExpanded }
                ) {
                    if (appSettingsManager != null && secureStorageManager != null) {
                        com.example.ui.settings.sections.GoogleDriveSettingsSection(
                            appSettingsManager = appSettingsManager,
                            secureStorageManager = secureStorageManager,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER 3: CONNECTED SERVICES & 3RD-PARTY APIS
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.INTEGRATIONS) {
                SettingsFolderCard(
                    folderTitle = "Connected Services & 3rd-Party APIs",
                    folderSubtitle = "Gmail connector, Tavily search, SearxNG & built-in knowledge",
                    badge = "Connectors",
                    icon = Icons.Default.Extension,
                    iconTint = Color(0xFFF59E0B),
                    isExpanded = isIntegrationsExpanded,
                    onToggleExpand = { isIntegrationsExpanded = !isIntegrationsExpanded }
                ) {
                    if (appSettingsManager != null && secureStorageManager != null) {
                        AgentToolsSettingsSection(
                            appSettingsManager = appSettingsManager,
                            secureStorageManager = secureStorageManager,
                            onTestTavily = onTestTavily ?: { Result.failure(Exception("Tavily test unavailable")) },
                            onTestGitHub = onTestGitHub ?: { Result.failure(Exception("GitHub test unavailable")) },
                            onTestGmail = onTestGmail ?: { Result.failure(Exception("Gmail test unavailable")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // FOLDER 4: DATA, STORAGE & SYSTEM RESET
            // =========================================================================
            if (activeTab == SettingsFolderTab.ALL || activeTab == SettingsFolderTab.DATA_STORAGE) {
                SettingsFolderCard(
                    folderTitle = "Data, Storage & System Reset",
                    folderSubtitle = "Local workspace, chat history, cache & factory defaults",
                    badge = "System",
                    icon = Icons.Default.Storage,
                    iconTint = SasukeCrimson,
                    isExpanded = isDataStorageExpanded,
                    onToggleExpand = { isDataStorageExpanded = !isDataStorageExpanded }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    ) {
                        // Local Workspace row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearWorkspaceDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Clear Local Workspace Files", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (localWorkspaceFilesCount != null) "$localWorkspaceFilesCount files stored in local sandbox" else "Manage local device workspace",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            Text("Clear", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        // Clear all chats row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearChatsDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("clear_all_history_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Clear All Chat History", color = ErrorRed, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text("Delete conversations stored in local SQLite database", color = TextMuted, fontSize = 11.5.sp)
                            }
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        // Reset settings row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showResetSettingsDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("reset_all_settings_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Reset All Settings to Defaults", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text("Restore model, prompts, and tool toggles to default", color = TextMuted, fontSize = 11.5.sp)
                            }
                            Text("Reset", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Visual Folder Container with interactive Accordion expansion.
 */
@Composable
fun SettingsFolderCard(
    folderTitle: String,
    folderSubtitle: String,
    badge: String,
    icon: ImageVector,
    iconTint: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "folderArrow")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
        // Folder Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = folderTitle,
                            color = TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(iconTint.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badge,
                                color = iconTint,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = folderSubtitle,
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }

        // Expanded Folder Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .padding(bottom = 14.dp)
            ) {
                HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(bottom = 12.dp))
                content()
            }
        }
    }
}

@Composable
fun SettingsFolderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) Color.White else SurfaceElevated
    val text = if (isSelected) Color.Black else TextSecondary
    val border = if (isSelected) Color.White else BorderSubtle

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = text,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                color = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
