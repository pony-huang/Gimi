package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.conversation.model.AttachmentCategory
import github.ponyhuang.asssistantai.domain.conversation.model.DraftAttachment
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import org.junit.Rule
import org.junit.Test

class ChatComposerLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyComposerShowsLeftToolsAndMicrophoneWithoutSend() {
        setComposer()

        composeRule.onNodeWithTag("chat_composer_add").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_model_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_microphone").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_send").assertDoesNotExist()
    }

    @Test
    fun filledComposerAddsSendBesideMicrophone() {
        setComposer(messageData = MessageData(text = "Hello"))

        composeRule.onNodeWithTag("chat_composer_microphone").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_send").assertIsDisplayed()
    }

    @Test
    fun attachmentRendersInsideComposerWithAccessibleRemoveAction() {
        setComposer(
            messageData = MessageData(
                attachments = listOf(
                    DraftAttachment(
                        reference = "/chat-test/attachment.pdf",
                        displayName = "attachment.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 128,
                        category = AttachmentCategory.DOCUMENT,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag("chat_composer_attachment").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_attachment_remove")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(
            "chat_composer_attachment_remove_visual",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .assertWidthIsEqualTo(28.dp)
        composeRule.onNodeWithTag("chat_composer_send").assertIsDisplayed()
    }

    @Test
    fun generatingComposerReplacesVoiceAndSendWithStop() {
        setComposer(
            messageData = MessageData(text = "Hello"),
            isGenerating = true,
        )

        composeRule.onNodeWithTag("chat_composer_microphone").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_composer_send").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_composer_stop").assertIsDisplayed()
    }

    @Test
    fun addButtonOpensSessionConfigurationSheetAndNavigatesWithinIt() {
        setComposer(
            addToChatState = ChatAddToChatState(
                serviceId = "service",
                configuration = ConversationToolConfiguration(
                    enabledLocalToolIds = setOf("clock"),
                    enabledMcpServerIds = setOf("mcp-1"),
                ),
                localTools = listOf(
                    ToolDescriptor("clock", "Clock", "Read the current time", true),
                ),
                mcpServers = listOf(McpServer(id = "mcp-1", name = "Test MCP")),
            ),
        )

        composeRule.onNodeWithTag("chat_composer_add").performClick()
        composeRule.onNodeWithTag("add-to-chat-home").assertIsDisplayed()
        composeRule.onNodeWithTag("session-tools-nav").performClick()
        composeRule.onNodeWithTag("session-tools-page").assertIsDisplayed()
        composeRule.onNodeWithTag("add-to-chat-back").performClick()
        composeRule.onNodeWithTag("session-mcp-nav").performClick()
        composeRule.onNodeWithTag("session-mcp-page").assertIsDisplayed()
    }

    @Test
    fun addToChatSheetOpensToolAccessPageAndSelectsMode() {
        var selectedMode: ToolAccessMode? = null
        setComposer(
            addToChatState = ChatAddToChatState(
                configuration = ConversationToolConfiguration(),
            ),
            onToolAccessModeChange = { selectedMode = it },
        )

        composeRule.onNodeWithTag("chat_composer_add").performClick()
        composeRule.onNodeWithTag("tool-access-nav").performClick()
        composeRule.onNodeWithTag("tool-access-page").assertIsDisplayed()
        composeRule.onNodeWithTag("tool-access-auto").assertIsSelected()
        composeRule.onNodeWithTag("tool-access-on-demand").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assert(selectedMode == ToolAccessMode.ON_DEMAND)
        }
        composeRule.onNodeWithTag("add-to-chat-back").performClick()
        composeRule.onNodeWithTag("add-to-chat-home").assertIsDisplayed()
    }

    @Test
    fun toolAccessModesAreDisabledWhileAgentRuns() {
        setComposer(
            addToChatState = ChatAddToChatState(
                configuration = ConversationToolConfiguration(),
                isMutationBlocked = true,
            ),
        )

        composeRule.onNodeWithTag("chat_composer_add").performClick()
        composeRule.onNodeWithTag("tool-access-nav").performClick()
        composeRule.onNodeWithTag("tool-access-auto").assertIsNotEnabled()
        composeRule.onNodeWithTag("tool-access-on-demand").assertIsNotEnabled()
        composeRule.onNodeWithTag("tool-access-always").assertIsNotEnabled()
    }

    private fun setComposer(
        messageData: MessageData = MessageData(),
        isGenerating: Boolean = false,
        addToChatState: ChatAddToChatState = ChatAddToChatState(),
        onToolAccessModeChange: (ToolAccessMode) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { true },
                    onStopClick = { },
                    isGenerating = isGenerating,
                    messageData = messageData,
                    isVoiceInputAvailable = true,
                    addToChatState = addToChatState,
                    onToolAccessModeChange = onToolAccessModeChange,
                    modelSelectorContent = {
                        Text(
                            text = "Test model",
                            modifier = Modifier.testTag("chat_composer_model_picker"),
                        )
                    },
                )
            }
        }
    }
}
