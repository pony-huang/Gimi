package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionModeSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionModeNavigatesAndUpdatesApprovalInBothDirections() {
        val state = mutableStateOf(ChatAddToChatState())
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                ChatAddToChatSheet(
                    state = state.value,
                    onDismiss = {},
                    onTakePhoto = {},
                    onChoosePhotos = {},
                    onChooseFiles = {},
                    imagesEnabled = true,
                    filesEnabled = true,
                    onLocalToolEnabledChange = { _, _ -> },
                    onToolAccessModeChange = {},
                    onReasoningEffortChange = {},
                    onMcpServerEnabledChange = { _, _ -> },
                    onFullAccessChange = {
                        changes.add(it)
                        state.value = state.value.copy(fullAccess = it)
                    },
                    onOfficialToolOpened = {},
                    onOfficialToolFunctionEnabledChange = { _, _, _ -> },
                    onOfficialToolFunctionsRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("permission-mode-nav").performClick()
        composeRule.onNodeWithTag("permission-mode-request").assertIsSelected()
        composeRule.onNodeWithTag("permission-mode-full").performClick().assertIsSelected()
        composeRule.onNodeWithTag("add-to-chat-back").performClick()
        composeRule.onNodeWithTag("add-to-chat-home").assertIsDisplayed()
        composeRule.onNodeWithTag("permission-mode-nav").performClick()
        composeRule.onNodeWithTag("permission-mode-full").assertIsSelected()
        composeRule.onNodeWithTag("permission-mode-request").performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf(true, false), changes) }
    }
}
