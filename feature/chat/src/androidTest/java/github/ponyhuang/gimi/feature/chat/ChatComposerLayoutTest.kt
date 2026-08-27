package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChatComposerLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unfocusedEmptyComposerUsesCompactControls() {
        setComposer()

        composeRule.onNodeWithTag("chat_composer_add").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_model_picker").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_composer_microphone").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_send").assertDoesNotExist()
    }

    @Test
    fun focusedEmptyComposerExpandsAndShowsModelSelector() {
        setComposer()

        composeRule.onNodeWithTag("chat_composer_text_field").performClick()

        composeRule.onNodeWithTag("chat_composer_model_picker").assertIsDisplayed()
    }

    @Test
    fun focusedComposerIsWiderThanUnfocusedComposer() {
        setComposer()
        val compactWidth = composeRule.onNodeWithTag("chat_composer_surface")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        composeRule.onNodeWithTag("chat_composer_text_field").performClick()
        composeRule.waitForIdle()

        val expandedWidth = composeRule.onNodeWithTag("chat_composer_surface")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assert(expandedWidth > compactWidth)
    }

    @Test
    fun focusedComposerIsTallerThanUnfocusedComposer() {
        setComposer()
        val compactHeight = composeRule.onNodeWithTag("chat_composer_surface")
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        composeRule.onNodeWithTag("chat_composer_text_field").performClick()
        composeRule.waitForIdle()

        val expandedHeight = composeRule.onNodeWithTag("chat_composer_surface")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assert(expandedHeight > compactHeight)
    }

    @Test
    fun retainedComposerKeepsModelSelectorVisibleWithoutTextFocus() {
        setComposer(retainExpanded = true)

        composeRule.onNodeWithTag("chat_composer_model_picker").assertIsDisplayed()
    }

    @Test
    fun releasingRetainedComposerRestoresTextInputFocus() {
        lateinit var retainExpanded: MutableState<Boolean>
        lateinit var focusManager: FocusManager
        composeRule.setContent {
            retainExpanded = remember { mutableStateOf(false) }
            focusManager = LocalFocusManager.current
            MaterialTheme {
                ChatComposer(
                    onSendClick = { true },
                    onStopClick = { },
                    isGenerating = false,
                    isVoiceInputAvailable = true,
                    retainExpanded = retainExpanded.value,
                    modelSelectorContent = {
                        Text(
                            text = "Test model",
                            modifier = Modifier.testTag("chat_composer_model_picker"),
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("chat_composer_text_field").performClick()
        composeRule.runOnIdle {
            retainExpanded.value = true
            focusManager.clearFocus()
        }
        composeRule.runOnIdle { retainExpanded.value = false }

        composeRule.onNodeWithTag("chat_composer_text_field").assertIsFocused()
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
        composeRule.onNodeWithTag("tool-access-auto").assertDoesNotExist()
        composeRule.onNodeWithTag("tool-access-always").assertIsSelected()
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
        composeRule.onNodeWithTag("tool-access-on-demand").assertIsNotEnabled()
        composeRule.onNodeWithTag("tool-access-always").assertIsNotEnabled()
    }

    @Test
    fun bareEnterTriggersSendWithEnteredText() {
        var sent: MessageData? = null
        setComposer(onSendClick = { sent = it; true })

        composeRule.onNodeWithTag("chat_composer_text_field")
            .performTextInput("Hello")
        composeRule.onNodeWithTag("chat_composer_text_field")
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assert(sent?.text == "Hello") }
    }

    @Test
    fun shiftEnterDoesNotTriggerSendAndKeepsDraft() {
        var sent: MessageData? = null
        setComposer(onSendClick = { sent = it; true })

        composeRule.onNodeWithTag("chat_composer_text_field")
            .performTextInput("Hello")
        composeRule.onNodeWithTag("chat_composer_text_field")
            .performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Enter)
                keyUp(Key.ShiftLeft)
            }

        composeRule.runOnIdle { assert(sent == null) }
        composeRule.onNodeWithTag("chat_composer_text_field").assertTextContains("Hello")
    }

    private fun setComposer(
        messageData: MessageData = MessageData(),
        isGenerating: Boolean = false,
        addToChatState: ChatAddToChatState = ChatAddToChatState(),
        retainExpanded: Boolean = false,
        onToolAccessModeChange: (ToolAccessMode) -> Unit = {},
        onSendClick: (MessageData) -> Boolean = { true },
    ) {
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = onSendClick,
                    onStopClick = { },
                    isGenerating = isGenerating,
                    messageData = messageData,
                    isVoiceInputAvailable = true,
                    retainExpanded = retainExpanded,
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
