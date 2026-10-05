package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppSettingsManager
import com.example.data.local.SecureStorageManager
import com.example.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSettingsScreenRendersAndClickTabs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettingsManager = AppSettingsManager(context)
        val secureStorageManager = SecureStorageManager(context)

        composeTestRule.setContent {
            SettingsScreen(
                currentApiKeyMasked = "AIzaSy...",
                hasConfiguredKey = true,
                onSaveApiKey = {},
                onClearApiKey = {},
                onTestConnection = { _, _ -> Result.success("ok") },
                currentSystemPrompt = "Test prompt",
                onSaveSystemPrompt = {},
                onResetSystemPrompt = {},
                currentModel = "gemini-3.5-flash",
                onSelectModel = {},
                onClearAllChats = {},
                onResetAllSettings = {},
                onNavigateBack = {},
                appSettingsManager = appSettingsManager,
                secureStorageManager = secureStorageManager
            )
        }

        // Test clicking folder tabs
        composeTestRule.onNodeWithText("AI Engine").performClick()
        composeTestRule.onNodeWithText("Git & GitHub").performClick()
        composeTestRule.onNodeWithText("External APIs").performClick()
        composeTestRule.onNodeWithText("System Data").performClick()
    }
}
