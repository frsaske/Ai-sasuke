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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
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
            title = { Text("Reset Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Restore default model and system prompt? Your API key will be preserved.",
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
                            snackbarHostState.showSnackbar("Settings reset")
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

    if (showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatsDialog = false },
            title = { Text("Clear All Chats", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Delete all saved chats from this device?",
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
                            snackbarHostState.showSnackbar("All chats deleted")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // SECTION 1: API KEY
            Column {
                Text(
                    text = "API KEY",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Gemini Key",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )

                        Text(
                            text = if (hasConfiguredKey) "Saved" else "Not set",
                            color = if (hasConfiguredKey) SuccessGreen else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (hasConfiguredKey) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentApiKeyMasked,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            testState = TestConnectionState.Idle
                        },
                        placeholder = {
                            Text(
                                text = if (hasConfiguredKey) "Enter new key to update..." else "Paste Gemini API Key...",
                                color = TextMuted,
                                fontSize = 13.5.sp
                            )
                        },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderMedium,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

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
                                        snackbarHostState.showSnackbar("API key saved")
                                    }
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = SurfaceContainerHighDark,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("save_api_key_button")
                        ) {
                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (hasConfiguredKey) {
                            OutlinedButton(
                                onClick = {
                                    onClearApiKey()
                                    apiKeyInput = ""
                                    testState = TestConnectionState.Idle
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Key removed")
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = ButtonDefaults.outlinedButtonBorder(true),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(38.dp)
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
                                        onFailure = { err -> TestConnectionState.Failure(err.localizedMessage ?: "Failed") }
                                    )
                                }
                            },
                            enabled = (hasConfiguredKey || apiKeyInput.isNotBlank()) && testState !is TestConnectionState.Testing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = ButtonDefaults.outlinedButtonBorder(true),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("test_api_connection_button")
                        ) {
                            if (testState is TestConnectionState.Testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Test", fontSize = 13.sp)
                            }
                        }
                    }

                    // Test Result Inline
                    AnimatedVisibility(visible = testState !is TestConnectionState.Idle) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            when (val state = testState) {
                                is TestConnectionState.Success -> {
                                    Text(
                                        text = "Connection verified successfully.",
                                        color = SuccessGreen,
                                        fontSize = 12.5.sp
                                    )
                                }
                                is TestConnectionState.Failure -> {
                                    Text(
                                        text = state.errorMessage,
                                        color = ErrorRed,
                                        fontSize = 12.5.sp
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tip: Google AI Studio recently started issuing 'AQ.' keys which have a temporary server-side issue (ACCESS_TOKEN_TYPE_UNSUPPORTED). If an 'AQ.' key fails, generate an 'AIza...' API key in Google Cloud Console under APIs & Services > Credentials.",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // SECTION 2: MODEL SELECTION
            Column {
                Text(
                    text = "MODEL",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    AppSettingsManager.AVAILABLE_MODELS.forEachIndexed { index, modelOption ->
                        val isSelected = modelOption.id == currentModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(modelOption.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("model_option_${modelOption.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelOption.displayName,
                                    color = TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = modelOption.description,
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (index < AppSettingsManager.AVAILABLE_MODELS.size - 1) {
                            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
                        }
                    }
                }
            }

            // SECTION 3: SYSTEM PROMPT
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYSTEM PROMPT",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )

                    Text(
                        text = "Reset",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                onResetSystemPrompt()
                                systemPromptInput = AppSettingsManager.DEFAULT_SYSTEM_PROMPT
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Prompt reset")
                                }
                            }
                            .testTag("reset_system_prompt_button")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = {
                            systemPromptInput = it
                            onSaveSystemPrompt(it)
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderMedium,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("system_prompt_input")
                    )
                }
            }

            // SECTION 4: DATA & MANAGEMENT
            Column {
                Text(
                    text = "DATA",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearChatsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                            .testTag("clear_all_history_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clear all chats",
                            color = ErrorRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetSettingsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                            .testTag("reset_all_settings_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reset all settings",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
