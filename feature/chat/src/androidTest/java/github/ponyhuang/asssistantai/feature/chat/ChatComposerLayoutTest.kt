package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
                attachments = listOf("content://chat-test/attachment".toUri()),
            ),
        )

        composeRule.onNodeWithTag("chat_composer_attachment").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_attachment_remove")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
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

    private fun setComposer(
        messageData: MessageData = MessageData(),
        isGenerating: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { },
                    onStopClick = { },
                    isGenerating = isGenerating,
                    messageData = messageData,
                    isVoiceInputAvailable = true,
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
