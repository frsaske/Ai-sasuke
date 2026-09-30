package com.example.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppSettingsManager
import com.example.ui.components.ChatHistoryDrawerContent
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MessageBubble
import com.example.ui.components.MessageComposer
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppScreen {
    CHAT,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(AppScreen.CHAT) }
    var showModelPicker by remember { mutableStateOf(false) }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentModel by viewModel.currentModel.collectAsStateWithLifecycle()
    val currentSystemPrompt by viewModel.currentSystemPrompt.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isNearBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) true
            else {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 3
            }
        }
    }

    LaunchedEffect(viewModel.scrollToBottomEvent) {
        viewModel.scrollToBottomEvent.collectLatest {
            if (isNearBottom && !listState.isScrollInProgress) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Quick Model Switcher Dialog
    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Select Model", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                ) {
                    AppSettingsManager.AVAILABLE_MODELS.forEachIndexed { index, modelOption ->
                        val isSelected = modelOption.id == currentModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectModel(modelOption.id)
                                    showModelPicker = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    contentDescription = null,
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
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text("Close", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Settings Navigation
    if (currentScreen == AppScreen.SETTINGS) {
        BackHandler { currentScreen = AppScreen.CHAT }
        SettingsScreen(
            currentApiKeyMasked = viewModel.secureStorageManager.getMaskedApiKey(),
            hasConfiguredKey = viewModel.secureStorageManager.hasApiKey(),
            onSaveApiKey = { viewModel.saveApiKey(it) },
            onClearApiKey = { viewModel.clearApiKey() },
            onTestConnection = { key, model -> viewModel.testConnection(key, model) },
            currentSystemPrompt = currentSystemPrompt,
            onSaveSystemPrompt = { viewModel.saveSystemPrompt(it) },
            onResetSystemPrompt = { viewModel.resetSystemPrompt() },
            currentModel = currentModel,
            onSelectModel = { viewModel.selectModel(it) },
            onClearAllChats = { viewModel.clearAllConversations() },
            onResetAllSettings = { viewModel.resetAllSettings() },
            onNavigateBack = { currentScreen = AppScreen.CHAT }
        )
        return
    }

    // Drawer Back Handling
    if (drawerState.isOpen) {
        BackHandler {
            coroutineScope.launch { drawerState.close() }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(310.dp)
            ) {
                ChatHistoryDrawerContent(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                    },
                    onNewChat = {
                        viewModel.startNewChat()
                    },
                    onRenameConversation = { id, title ->
                        viewModel.renameConversation(id, title)
                    },
                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    },
                    onClearAllConversations = {
                        viewModel.clearAllConversations()
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showModelPicker = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SasukeX",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                val modelShortName = when (currentModel) {
                                    "gemini-3.5-flash" -> "3.5 Flash"
                                    "gemini-3.1-pro-preview" -> "3.1 Pro"
                                    "gemini-3.1-flash-lite-preview" -> "Flash-Lite"
                                    else -> currentModel
                                }

                                Text(
                                    text = modelShortName,
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch model",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_history_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Chats",
                                    tint = TextPrimary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.startNewChat() },
                                modifier = Modifier.testTag("top_bar_new_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Chat",
                                    tint = TextPrimary
                                )
                            }

                            IconButton(
                                onClick = { currentScreen = AppScreen.SETTINGS },
                                modifier = Modifier.testTag("top_bar_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ObsidianBg
                        )
                    )
                    HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
                }
            },
            bottomBar = {
                MessageComposer(
                    text = composerText,
                    onTextChanged = { viewModel.onComposerTextChanged(it) },
                    isGenerating = isGenerating,
                    onSend = { viewModel.sendMessage() },
                    onStop = { viewModel.stopGeneration() },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            },
            containerColor = ObsidianBg,
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty()) {
                    EmptyStateView(
                        activeModel = currentModel,
                        onSelectPrompt = { prompt ->
                            viewModel.sendMessage(prompt)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 6.dp)
                            .testTag("messages_lazy_column")
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                onRegenerate = { viewModel.regenerateResponse(message.id) },
                                onDelete = { viewModel.deleteMessage(message.id) },
                                onRetry = { viewModel.retryMessage(message.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
