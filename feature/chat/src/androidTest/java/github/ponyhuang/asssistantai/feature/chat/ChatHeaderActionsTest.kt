package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatHeaderActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsActionRemainsAvailableAtTopRight() {
        var settingsClicks = 0
        composeRule.setContent {
            MaterialTheme {
                ChatHeaderActions(
                    onOpenDrawer = { },
                    onNewConversation = { },
                    onOpenSettings = { settingsClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("chat_header_settings")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, settingsClicks)
    }
}
