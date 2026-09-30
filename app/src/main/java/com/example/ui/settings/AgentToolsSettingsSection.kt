package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
    onTestSearx: suspend (String) -> Result<String>,
    onTestGitHub: suspend () -> Result<String>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var isWebSearchEnabled by remember { mutableStateOf(appSettingsManager.isWebSearchEnabled()) }
    var searxUrlInput by remember { mutableStateOf(appSettingsManager.getSearxUrl()) }
    var searxTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    var isGitHubConnected by remember { mutableStateOf(secureStorageManager.hasGitHubToken()) }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var isGitHubTokenVisible by remember { mutableStateOf(false) }
    var gitHubTestState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

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
            // 1. WEB SEARCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔎 Web Search (SearXNG)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Real-time web search and page reading",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
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
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "SearXNG Instance URL",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searxUrlInput,
                        onValueChange = {
                            searxUrlInput = it
                            appSettingsManager.setSearxUrl(it)
                            searxTestState = TestConnectionState.Idle
                        },
                        placeholder = { Text("https://search.ononoki.org", color = TextMuted, fontSize = 12.sp) },
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (val state = searxTestState) {
                                is TestConnectionState.Success -> "✓ ${state.responseSnippet}"
                                is TestConnectionState.Failure -> "✕ ${state.errorMessage}"
                                else -> ""
                            },
                            color = when (searxTestState) {
                                is TestConnectionState.Success -> SuccessGreen
                                is TestConnectionState.Failure -> ErrorRed
                                else -> TextMuted
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    searxTestState = TestConnectionState.Testing
                                    val res = onTestSearx("Android news")
                                    searxTestState = res.fold(
                                        onSuccess = { TestConnectionState.Success(it) },
                                        onFailure = { TestConnectionState.Failure(it.message ?: "Failed") }
                                    )
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.height(32.dp).testTag("test_searx_button")
                        ) {
                            if (searxTestState is TestConnectionState.Testing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TextPrimary)
                            } else {
                                Text("Test Search", fontSize = 12.sp)
                            }
                        }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🐙 GitHub Integration",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isGitHubConnected) "Connected with Personal Access Token" else "Not connected (optional)",
                        color = if (isGitHubConnected) SuccessGreen else TextMuted,
                        fontSize = 11.5.sp
                    )
                }

                if (isGitHubConnected) {
                    OutlinedButton(
                        onClick = {
                            secureStorageManager.clearGitHubToken()
                            isGitHubConnected = false
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
                OutlinedTextField(
                    value = gitHubTokenInput,
                    onValueChange = {
                        gitHubTokenInput = it
                        gitHubTestState = TestConnectionState.Idle
                    },
                    placeholder = { Text("Paste GitHub token (ghp_...)", color = TextMuted, fontSize = 12.sp) },
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
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (gitHubTokenInput.isNotBlank()) {
                                secureStorageManager.saveGitHubToken(gitHubTokenInput)
                                isGitHubConnected = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SasukeCrimson, contentColor = Color.White),
                        modifier = Modifier.height(32.dp).testTag("github_save_token_button")
                    ) {
                        Text("Save & Connect", fontSize = 12.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = secureStorageManager.getMaskedGitHubToken(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

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

            // 3. KNOWLEDGE & UTILITY TOOLS
            Text(
                text = "KNOWLEDGE CAPABILITIES",
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            ToolToggleRow(
                label = "🌤 Weather (Open-Meteo)",
                description = "Live global forecasts & temperatures",
                checked = isWeatherEnabled,
                onCheckedChange = {
                    isWeatherEnabled = it
                    appSettingsManager.setWeatherEnabled(it)
                }
            )

            ToolToggleRow(
                label = "📖 Wikipedia Search",
                description = "Encyclopedia summaries & articles",
                checked = isWikipediaEnabled,
                onCheckedChange = {
                    isWikipediaEnabled = it
                    appSettingsManager.setWikipediaEnabled(it)
                }
            )

            ToolToggleRow(
                label = "💱 Currency Exchange (Frankfurter)",
                description = "ECB foreign exchange rates & conversions",
                checked = isCurrencyEnabled,
                onCheckedChange = {
                    isCurrencyEnabled = it
                    appSettingsManager.setCurrencyEnabled(it)
                }
            )

            ToolToggleRow(
                label = "⏰ World Clock & Timezones",
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
            Spacer(modifier = Modifier.height(6.dp))

            ToolToggleRow(
                label = "Show Agent Activity Cards",
                description = "Display live tool execution progress in chat",
                checked = isShowAgentActivity,
                onCheckedChange = {
                    isShowAgentActivity = it
                    appSettingsManager.setShowAgentActivityEnabled(it)
                }
            )

            ToolToggleRow(
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
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
