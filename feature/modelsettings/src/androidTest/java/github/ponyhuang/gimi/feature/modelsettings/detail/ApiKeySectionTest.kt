package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApiKeySectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testActionIsEnabledOnlyAfterApiKeyInput() {
        var apiKey by mutableStateOf("")
        var testClicks = 0

        composeRule.setContent {
            AsssistantaiTheme {
                ApiKeySection(
                    apiKey = apiKey,
                    keyHelpUrl = "https://example.com/key",
                    isVisible = false,
                    isTesting = false,
                    onApiKeyChange = { apiKey = it },
                    onToggleVisibility = {},
                    onTest = { testClicks += 1 },
                    onOpenKeyHelp = {},
                )
            }
        }

        composeRule.onNodeWithText("检测").assertIsNotEnabled()
        composeRule.onNodeWithText("API 密钥").performTextInput("secret")
        composeRule.onNodeWithText("检测").assertIsEnabled().performClick()

        assertEquals(1, testClicks)
    }
}
