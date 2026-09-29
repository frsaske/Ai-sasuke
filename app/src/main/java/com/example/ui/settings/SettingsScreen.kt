package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorBorder
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch

sealed class TestConnectionState {
    data object Idle : TestConnectionState()
    data object Testing : TestConnectionState()
    data class Success(val responseSnippet: String) : TestConnectionState()
    data class Failure(val errorMessage: String) : TestConnectionState()
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var systemPromptInput by remember { mutableStateOf(currentSystemPrompt) }
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showClearChatsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentSystemPrompt) {
        systemPromptInput = currentSystemPrompt
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset All Settings?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore the default model and default system prompt. Your saved API key will remain intact.",
                    color = TextSecondary,
                    fontSize = 14.sp
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
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceContainerHighDark
        )
    }

    if (showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatsDialog = false },
            title = { Text("Clear All Conversations?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "All chat history stored locally on this device will be permanently erased.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllChats()
                        showClearChatsDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("All conversations erased")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Erase All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceContainerHighDark
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI SETTINGS",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "SasukeX Personal Assistant Configuration",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBg
                )
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // 1. GEMINI API KEY SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHighDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gemini API Key",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Key status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (hasConfiguredKey) SuccessGreen.copy(alpha = 0.15f)
                                else WarningAmber.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (hasConfiguredKey) SuccessGreen.copy(alpha = 0.5f)
                                else WarningAmber.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (hasConfiguredKey) "Configured" else "Missing Key",
                            color = if (hasConfiguredKey) SuccessGreen else WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (hasConfiguredKey) {
                        "Active Key: $currentApiKeyMasked (Hardware Encrypted)"
                    } else {
                        "Enter your Google Gemini API key to activate streaming generation."
                    },
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        testState = TestConnectionState.Idle
                    },
                    placeholder = {
                        Text(
                            text = if (hasConfiguredKey) "Enter new key to replace..." else "AIzaSy...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) "Hide Key" else "Show Key",
                                tint = TextMuted
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceContainerDark,
                        unfocusedContainerColor = SurfaceContainerDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons for Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                onSaveApiKey(apiKeyInput)
                                apiKeyInput = ""
                                testState = TestConnectionState.Idle
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Gemini API key encrypted and saved locally.")
                                }
                            }
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("save_api_key_button")
                    ) {
                        Text("Save Key", fontSize = 13.sp)
                    }

                    if (hasConfiguredKey) {
                        OutlinedButton(
                            onClick = {
                                onClearApiKey()
                                apiKeyInput = ""
                                testState = TestConnectionState.Idle
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("API key cleared.")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(ErrorRed.copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("clear_api_key_button")
                        ) {
                            Text("Clear", fontSize = 13.sp)
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
                                    onFailure = { err -> TestConnectionState.Failure(err.localizedMessage ?: "Connection failed") }
                                )
                            }
                        },
                        enabled = (hasConfiguredKey || apiKeyInput.isNotBlank()) && testState !is TestConnectionState.Testing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AccentCyan)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("test_api_connection_button")
                    ) {
                        if (testState is TestConnectionState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Test API", fontSize = 13.sp)
                        }
                    }
                }

                // Test Connection Feedback Result
                AnimatedVisibility(visible = testState !is TestConnectionState.Idle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        when (val state = testState) {
                            is TestConnectionState.Success -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SuccessGreen.copy(alpha = 0.15f))
                                        .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Connection verified! Echo: \"${state.responseSnippet}\"",
                                        color = SuccessGreen,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                            is TestConnectionState.Failure -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ErrorContainer)
                                        .border(1.dp, ErrorBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.errorMessage,
                                        color = ErrorRed,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // 2. MODEL SELECTION SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHighDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Model Selection",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select the Gemini foundation model powering SasukeX.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettingsManager.AVAILABLE_MODELS.forEach { modelOption ->
                        val isSelected = modelOption.id == currentModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SurfaceContainerHighDark else SurfaceContainerDark.copy(alpha = 0.6f))
                                .border(
                                    1.2.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectModel(modelOption.id) }
                                .padding(12.dp)
                                .testTag("model_option_${modelOption.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (isSelected) AccentCyan else TextMuted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AccentCyan)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = modelOption.displayName,
                                        color = TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceDark)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = modelOption.badge,
                                            color = AccentCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = modelOption.description,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. SYSTEM PROMPT SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHighDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "System Prompt",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    TextButton(
                        onClick = {
                            onResetSystemPrompt()
                            systemPromptInput = AppSettingsManager.DEFAULT_SYSTEM_PROMPT
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("System prompt reset to default")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan),
                        modifier = Modifier.testTag("reset_system_prompt_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Defines SasukeX's persona, precision, formatting guidelines, and behavioral constraints.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = systemPromptInput,
                    onValueChange = {
                        systemPromptInput = it
                        onSaveSystemPrompt(it)
                    },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceContainerDark,
                        unfocusedContainerColor = SurfaceContainerDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("system_prompt_input")
                )
            }

            // 4. MANAGEMENT & DANGER ZONE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "Storage & State",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetSettingsDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_all_settings_button")
                    ) {
                        Text("Reset Settings", fontSize = 12.5.sp)
                    }

                    OutlinedButton(
                        onClick = { showClearChatsDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(ErrorRed.copy(alpha = 0.5f))
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_all_history_button")
                    ) {
                        Text("Clear All Chats", fontSize = 12.5.sp)
                    }
                }
            }

            // 5. ARCHITECTURE FOOTER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerDark.copy(alpha = 0.4f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "SasukeX Personal Assistant Core v1.0\n• Hardware Keystore protected credentials\n• Modular architecture ready for GitHub, Google Drive, Gmail & Terminal tools\n• Built on Google Gemini REST & SSE streaming",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
