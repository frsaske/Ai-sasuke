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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
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
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AgentToolsSettingsSection(
    appSettingsManager: AppSettingsManager,
    secureStorageManager: SecureStorageManager,
    onTestTavily: suspend (String) -> Result<String>,
    onTestGitHub: suspend () -> Result<String>,
    onTestGmail: suspend () -> Result<String> = { Result.failure(Exception("Gmail test not available")) },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var isWebSearchEnabled by remember { mutableStateOf(appSettingsManager.isWebSearchEnabled()) }
    var tavilyKeyInput by remember { mutableStateOf("") }
    var isTavilyKeyVisible by remember { mutableStateOf(false) }
    var tavilyTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var hasCustomTavilyKey by remember { mutableStateOf(secureStorageManager.hasCustomTavilyApiKey()) }
    var currentMaskedTavilyKey by remember { mutableStateOf(secureStorageManager.getMaskedTavilyApiKey()) }

    var isGitHubConnected by remember { mutableStateOf(secureStorageManager.hasGitHubToken()) }
    var isGitHubPresetActive by remember { mutableStateOf(secureStorageManager.isGitHubPresetActive()) }
    var currentMaskedGitHubToken by remember { mutableStateOf(secureStorageManager.getMaskedGitHubToken()) }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var isGitHubTokenVisible by remember { mutableStateOf(false) }
    var gitHubTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    var isGmailEnabled by remember { mutableStateOf(appSettingsManager.isGmailEnabled()) }
    var isGmailConnected by remember { mutableStateOf(secureStorageManager.hasGmailToken()) }
    var currentGmailUserEmail by remember { mutableStateOf(secureStorageManager.getGmailUserEmail()) }
    var currentMaskedGmailToken by remember { mutableStateOf(secureStorageManager.getMaskedGmailToken()) }
    var gmailTokenInput by remember { mutableStateOf("") }
    var isGmailTokenVisible by remember { mutableStateOf(false) }
    var gmailTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var userEmailInput by remember { mutableStateOf("") }
    var isEditingEmail by remember { mutableStateOf(false) }

    var isWeatherEnabled by remember { mutableStateOf(appSettingsManager.isWeatherEnabled()) }
    var isWikipediaEnabled by remember { mutableStateOf(appSettingsManager.isWikipediaEnabled()) }
    var isCurrencyEnabled by remember { mutableStateOf(appSettingsManager.isCurrencyEnabled()) }
    var isTimeEnabled by remember { mutableStateOf(appSettingsManager.isTimeEnabled()) }

    var isShowAgentActivity by remember { mutableStateOf(appSettingsManager.isShowAgentActivityEnabled()) }
    var isRequireConfirmation by remember { mutableStateOf(appSettingsManager.isRequireConfirmationEnabled()) }

    Column(modifier = modifier) {
        Text(
            text = "AGENT TOOLS",
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
            // 1. TAVILY WEB SEARCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Web Search (Tavily AI)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Real-time search & verified web knowledge",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Switch(
                    checked = isWebSearchEnabled,
                    onCheckedChange = {
                        isWebSearchEnabled = it
                        appSettingsManager.setWebSearchEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SasukeCrimson
                    ),
                    modifier = Modifier.testTag("toggle_web_search")
                )
            }

            AnimatedVisibility(visible = isWebSearchEnabled) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tavily Search API Key",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (hasCustomTavilyKey) "Custom Key Active" else "Preset Key Active",
                            color = if (hasCustomTavilyKey) SuccessGreen else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Current: $currentMaskedTavilyKey",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = tavilyKeyInput,
                        onValueChange = {
                            tavilyKeyInput = it
                            tavilyTestState = TestConnectionState.Idle
                        },
                        placeholder = { Text("Paste custom Tavily API Key (tvly-...)", color = TextMuted, fontSize = 12.sp) },
                        visualTransformation = if (isTavilyKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isTavilyKeyVisible = !isTavilyKeyVisible }) {
                                Icon(
                                    imageVector = if (isTavilyKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
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
                        modifier = Modifier.fillMaxWidth().testTag("tavily_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (tavilyKeyInput.isNotBlank()) {
                                    secureStorageManager.saveTavilyApiKey(tavilyKeyInput)
                                    hasCustomTavilyKey = true
                                    currentMaskedTavilyKey = secureStorageManager.getMaskedTavilyApiKey()
                                    tavilyKeyInput = ""
                                    tavilyTestState = TestConnectionState.Idle
                                }
                            },
                            enabled = tavilyKeyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = SasukeCrimson, contentColor = Color.White),
                            modifier = Modifier.height(32.dp).testTag("save_tavily_key_button")
                        ) {
                            Text("Save Key", fontSize = 11.5.sp)
                        }

                        if (hasCustomTavilyKey) {
                            OutlinedButton(
                                onClick = {
                                    secureStorageManager.clearTavilyApiKey()
                                    hasCustomTavilyKey = false
                                    currentMaskedTavilyKey = secureStorageManager.getMaskedTavilyApiKey()
                                    tavilyTestState = TestConnectionState.Idle
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                                modifier = Modifier.height(32.dp).testTag("reset_tavily_key_button")
                            ) {
                                Text("Reset Default", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    tavilyTestState = TestConnectionState.Testing
                                    val res = onTestTavily("Android technology news")
                                    tavilyTestState = res.fold(
                                        onSuccess = { TestConnectionState.Success(it) },
                                        onFailure = { TestConnectionState.Failure(it.message ?: "Failed") }
                                    )
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.height(32.dp).testTag("test_tavily_button")
                        ) {
                            if (tavilyTestState is TestConnectionState.Testing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TextPrimary)
                            } else {
                                Text("Test Search", fontSize = 11.5.sp)
                            }
                        }
                    }

                    if (tavilyTestState !is TestConnectionState.Idle) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (val state = tavilyTestState) {
                                is TestConnectionState.Success -> "✓ ${state.responseSnippet}"
                                is TestConnectionState.Failure -> "✕ ${state.errorMessage}"
                                else -> ""
                            },
                            color = if (tavilyTestState is TestConnectionState.Success) SuccessGreen else ErrorRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 12.dp))

            // 2. GITHUB INTEGRATION
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "GitHub Integration",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isGitHubConnected) {
                                if (isGitHubPresetActive) "Configured Key Active" else "Custom Key Active"
                            } else {
                                "Key Not Set (Bot asks to set key)"
                            },
                            color = if (isGitHubConnected) SuccessGreen else Color(0xFFF59E0B),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isGitHubConnected) {
                    OutlinedButton(
                        onClick = {
                            secureStorageManager.clearGitHubToken()
                            isGitHubConnected = false
                            isGitHubPresetActive = false
                            currentMaskedGitHubToken = ""
                            gitHubTokenInput = ""
                            gitHubTestState = TestConnectionState.Idle
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.height(32.dp).testTag("github_disconnect_button")
                    ) {
                        Text("Disconnect", fontSize = 11.sp)
                    }
                }
            }

            if (!isGitHubConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "GitHub key is not set. If you request GitHub actions, SasukeX will ask you to set your key first.",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = gitHubTokenInput,
                    onValueChange = {
                        gitHubTokenInput = it
                        gitHubTestState = TestConnectionState.Idle
                    },
                    placeholder = { Text("Paste custom GitHub token (ghp_...)", color = TextMuted, fontSize = 12.sp) },
                    visualTransformation = if (isGitHubTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isGitHubTokenVisible = !isGitHubTokenVisible }) {
                            Icon(
                                imageVector = if (isGitHubTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
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
                    modifier = Modifier.fillMaxWidth().testTag("github_token_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (gitHubTokenInput.isNotBlank()) {
                                secureStorageManager.saveGitHubToken(gitHubTokenInput)
                                isGitHubConnected = true
                                isGitHubPresetActive = false
                                currentMaskedGitHubToken = secureStorageManager.getMaskedGitHubToken()
                                gitHubTokenInput = ""
                                gitHubTestState = TestConnectionState.Idle
                            }
                        },
                        enabled = gitHubTokenInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SasukeCrimson, contentColor = Color.White),
                        modifier = Modifier.height(32.dp).testTag("github_save_token_button")
                    ) {
                        Text("Save Key", fontSize = 12.sp)
                    }

                    if (secureStorageManager.hasGitHubToken()) {
                        OutlinedButton(
                            onClick = {
                                secureStorageManager.resetToDefaultGitHubToken()
                                isGitHubConnected = secureStorageManager.hasGitHubToken()
                                isGitHubPresetActive = secureStorageManager.isGitHubPresetActive()
                                currentMaskedGitHubToken = secureStorageManager.getMaskedGitHubToken()
                                gitHubTokenInput = ""
                                gitHubTestState = TestConnectionState.Idle
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.height(32.dp).testTag("github_use_preset_button")
                        ) {
                            Text("Use Configured Key", fontSize = 11.5.sp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current: $currentMaskedGitHubToken",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isGitHubPresetActive && secureStorageManager.hasGitHubToken()) {
                        OutlinedButton(
                            onClick = {
                                secureStorageManager.resetToDefaultGitHubToken()
                                isGitHubConnected = secureStorageManager.hasGitHubToken()
                                isGitHubPresetActive = secureStorageManager.isGitHubPresetActive()
                                currentMaskedGitHubToken = secureStorageManager.getMaskedGitHubToken()
                                gitHubTokenInput = ""
                                gitHubTestState = TestConnectionState.Idle
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Reset Key", fontSize = 11.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                gitHubTestState = TestConnectionState.Testing
                                val res = onTestGitHub()
                                gitHubTestState = res.fold(
                                    onSuccess = { TestConnectionState.Success(it) },
                                    onFailure = { TestConnectionState.Failure(it.message ?: "Failed") }
                                )
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.height(30.dp).testTag("github_test_button")
                    ) {
                        if (gitHubTestState is TestConnectionState.Testing) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TextPrimary)
                        } else {
                            Text("Test GitHub", fontSize = 11.5.sp)
                        }
                    }
                }

                if (gitHubTestState !is TestConnectionState.Idle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (val state = gitHubTestState) {
                            is TestConnectionState.Success -> "✓ ${state.responseSnippet}"
                            is TestConnectionState.Failure -> "✕ ${state.errorMessage}"
                            else -> ""
                        },
                        color = if (gitHubTestState is TestConnectionState.Success) SuccessGreen else ErrorRed,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 12.dp))

            // 3. GMAIL CONNECTOR (GOOGLE WORKSPACE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isGmailConnected) SuccessGreen else SasukeCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gmail Connector",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isGmailConnected) "Connected ($currentGmailUserEmail)" else "OAuth • Read, send, drafts & trash emails",
                            color = if (isGmailConnected) SuccessGreen else TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Switch(
                    checked = isGmailEnabled,
                    onCheckedChange = {
                        isGmailEnabled = it
                        appSettingsManager.setGmailEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SasukeCrimson
                    ),
                    modifier = Modifier.testTag("toggle_gmail_connector")
                )
            }

            AnimatedVisibility(visible = isGmailEnabled) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Status Badge & Account Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gmail Authorization",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isGmailConnected) "Active / Connected" else "Token Required",
                            color = if (isGmailConnected) SuccessGreen else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Account Email row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account: $currentGmailUserEmail",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isEditingEmail) "Cancel" else "Change",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                isEditingEmail = !isEditingEmail
                                userEmailInput = currentGmailUserEmail
                            }
                        )
                    }

                    if (isEditingEmail) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userEmailInput,
                                onValueChange = { userEmailInput = it },
                                placeholder = { Text("your.email@gmail.com", color = TextMuted, fontSize = 12.sp) },
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
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (userEmailInput.isNotBlank()) {
                                        secureStorageManager.saveGmailUserEmail(userEmailInput)
                                        currentGmailUserEmail = secureStorageManager.getGmailUserEmail()
                                        isEditingEmail = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SasukeCrimson, contentColor = Color.White),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Set", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!isGmailConnected) {
                        Text(
                            text = "OAuth client configured with scope 'gmail.modify'. Paste your Google OAuth access token or bearer token below to activate.",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = gmailTokenInput,
                            onValueChange = {
                                gmailTokenInput = it
                                gmailTestState = TestConnectionState.Idle
                            },
                            placeholder = { Text("Paste Google OAuth token (ya29...)", color = TextMuted, fontSize = 12.sp) },
                            visualTransformation = if (isGmailTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { isGmailTokenVisible = !isGmailTokenVisible }) {
                                    Icon(
                                        imageVector = if (isGmailTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
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
                            modifier = Modifier.fillMaxWidth().testTag("gmail_token_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (gmailTokenInput.isNotBlank()) {
                                        secureStorageManager.saveGmailToken(gmailTokenInput)
                                        isGmailConnected = true
                                        currentMaskedGmailToken = secureStorageManager.getMaskedGmailToken()
                                        gmailTokenInput = ""
                                        gmailTestState = TestConnectionState.Idle
                                    }
                                },
                                enabled = gmailTokenInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = SasukeCrimson, contentColor = Color.White),
                                modifier = Modifier.height(34.dp).testTag("gmail_save_token_button")
                            ) {
                                Text("Save Token", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "Token: $currentMaskedGmailToken",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    secureStorageManager.clearGmailToken()
                                    isGmailConnected = false
                                    currentMaskedGmailToken = ""
                                    gmailTokenInput = ""
                                    gmailTestState = TestConnectionState.Idle
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                modifier = Modifier.height(30.dp).testTag("gmail_disconnect_button")
                            ) {
                                Text("Disconnect", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        gmailTestState = TestConnectionState.Testing
                                        val res = onTestGmail()
                                        gmailTestState = res.fold(
                                            onSuccess = { TestConnectionState.Success(it) },
                                            onFailure = { TestConnectionState.Failure(it.message ?: "Failed to connect to Gmail") }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.height(30.dp).testTag("gmail_test_button")
                            ) {
                                if (gmailTestState is TestConnectionState.Testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TextPrimary)
                                } else {
                                    Text("Test Connection", fontSize = 11.5.sp)
                                }
                            }
                        }

                        if (gmailTestState !is TestConnectionState.Idle) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (val state = gmailTestState) {
                                    is TestConnectionState.Success -> "✓ ${state.responseSnippet}"
                                    is TestConnectionState.Failure -> "✕ ${state.errorMessage}"
                                    else -> ""
                                },
                                color = if (gmailTestState is TestConnectionState.Success) SuccessGreen else ErrorRed,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Authorized capabilities: list/search inbox, read full content, write emails, drafts, delete/trash, and modify labels.",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 12.dp))

            // 4. KNOWLEDGE & UTILITY TOOLS
            Text(
                text = "KNOWLEDGE CAPABILITIES",
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            ToolToggleRow(
                icon = Icons.Default.WbSunny,
                label = "Weather (Open-Meteo)",
                description = "Live global forecasts & temperatures",
                checked = isWeatherEnabled,
                onCheckedChange = {
                    isWeatherEnabled = it
                    appSettingsManager.setWeatherEnabled(it)
                }
            )

            ToolToggleRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Wikipedia Search",
                description = "Encyclopedia summaries & articles",
                checked = isWikipediaEnabled,
                onCheckedChange = {
                    isWikipediaEnabled = it
                    appSettingsManager.setWikipediaEnabled(it)
                }
            )

            ToolToggleRow(
                icon = Icons.Default.Paid,
                label = "Currency Exchange",
                description = "ECB foreign exchange rates & conversions",
                checked = isCurrencyEnabled,
                onCheckedChange = {
                    isCurrencyEnabled = it
                    appSettingsManager.setCurrencyEnabled(it)
                }
            )

            ToolToggleRow(
                icon = Icons.Default.Schedule,
                label = "World Clock & Timezones",
                description = "Live local times for any city or timezone",
                checked = isTimeEnabled,
                onCheckedChange = {
                    isTimeEnabled = it
                    appSettingsManager.setTimeEnabled(it)
                }
            )

            HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 12.dp))

            // 4. BEHAVIOR & SAFETY
            Text(
                text = "AGENT BEHAVIOR & SAFETY",
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SimpleToggleRow(
                label = "Show Agent Activity Cards",
                description = "Display live tool execution progress in chat",
                checked = isShowAgentActivity,
                onCheckedChange = {
                    isShowAgentActivity = it
                    appSettingsManager.setShowAgentActivityEnabled(it)
                }
            )

            SimpleToggleRow(
                label = "Require Confirmation for Writes",
                description = "Prompt before modifying files or creating issues",
                checked = isRequireConfirmation,
                onCheckedChange = {
                    isRequireConfirmation = it
                    appSettingsManager.setRequireConfirmationEnabled(it)
                }
            )
        }
    }
}

@Composable
private fun ToolToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SasukeCrimson
            )
        )
    }
}

@Composable
private fun SimpleToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SasukeCrimson
            )
        )
    }
}
