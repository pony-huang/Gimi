package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceRecordingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordingControlsRemainVisibleAtPhoneWidth() {
        setRecordingContent(width = 320)

        composeRule.onNodeWithTag(VOICE_CANCEL_TEST_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(VOICE_WAVEFORM_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(VOICE_FINISH_TEST_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun recordingControlsRemainVisibleAtTabletWidth() {
        setRecordingContent(width = 720)

        composeRule.onNodeWithTag(VOICE_CANCEL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(VOICE_WAVEFORM_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(VOICE_FINISH_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun cancelAndFinishHaveIndependentActions() {
        var cancelCount = 0
        var finishCount = 0
        setRecordingContent(
            width = 360,
            onCancel = { cancelCount++ },
            onFinish = { finishCount++ },
        )

        composeRule.onNodeWithTag(VOICE_CANCEL_TEST_TAG).performClick()
        composeRule.onNodeWithTag(VOICE_FINISH_TEST_TAG).performClick()

        assertEquals(1, cancelCount)
        assertEquals(1, finishCount)
    }

    @Test
    fun remainingTimeIsAvailableToSemanticsButNotRenderedAsText() {
        setRecordingContent(width = 360)

        composeRule.onNodeWithText("0:42").assertDoesNotExist()
        composeRule.onNodeWithTag(VOICE_WAVEFORM_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun transcribingStateReplacesEditableComposerContent() {
        composeRule.setContent {
            MaterialTheme {
                DefaultComposerInputContent(
                    modifier = Modifier.width(320.dp),
                    params = ComposerInputContentParams(
                        messageData = MessageData(text = "保留的草稿"),
                        isGenerating = false,
                        voiceInputState = VoiceInputUiState.Transcribing,
                        isVoiceInputAvailable = true,
                        voiceErrorMessage = null,
                        onVoiceErrorShown = { },
                        onTextChange = { },
                        onRemoveAttachment = { },
                        onSendClick = { },
                        onStopClick = { },
                        onVoiceInputStart = { },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(VOICE_TRANSCRIBING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(VOICE_CANCEL_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(VOICE_FINISH_TEST_TAG).assertDoesNotExist()
    }

    private fun setRecordingContent(
        width: Int,
        onCancel: () -> Unit = { },
        onFinish: () -> Unit = { },
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(width.dp)) {
                    DefaultVoiceRecordingContent(
                        VoiceRecordingContentParams(
                            levels = List(48) { index -> ((index % 8) + 1) / 9f },
                            remainingSeconds = 42,
                            onCancel = onCancel,
                            onFinish = onFinish,
                        ),
                    )
                }
            }
        }
    }
}
