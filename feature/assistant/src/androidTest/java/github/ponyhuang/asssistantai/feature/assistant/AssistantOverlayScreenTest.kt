package github.ponyhuang.asssistantai.feature.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.asssistantai.domain.assistant.model.PendingAssistantConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantOverlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        state: AssistantOverlayUiState,
        onAction: (AssistantOverlayAction) -> Unit = {},
        onOpenInChat: () -> Unit = {},
    ) {
        composeRule.setContent {
            AssistantOverlayScreen(
                state = state,
                onAction = onAction,
                onOpenInChat = onOpenInChat,
            )
        }
    }

    @Test
    fun listeningShowsWaveformAndStopButton() {
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.LISTENING,
                recordingLevels = listOf(0.2f, 0.6f, 0.4f),
            ),
        )

        composeRule.onNodeWithTag("assistant_waveform").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_stop_listening").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_input").assertIsNotEnabled()
    }

    @Test
    fun followUpStateEnablesInputAndDispatchesKeyboardSubmission() {
        val actions = mutableListOf<AssistantOverlayAction>()
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                responseText = "回答内容",
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag("assistant_response_text").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_input").assertIsEnabled()
        composeRule.onNodeWithTag("assistant_input").performTextInput("追问")
        composeRule.onNodeWithTag("assistant_send").performClick()

        assertTrue(actions.any { it is AssistantOverlayAction.DraftChanged })
        assertTrue(actions.any { it is AssistantOverlayAction.SubmitDraft })
    }

    @Test
    fun confirmationCardShowsCountdownAndDispatchesDecisions() {
        val actions = mutableListOf<AssistantOverlayAction>()
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.AWAITING_CONFIRMATION,
                pendingConfirmation = PendingAssistantConfirmation(
                    confirmationCallId = "c1",
                    toolName = "camera_open",
                    deadlineEpochMs = 0L,
                ),
                confirmationRemainingSeconds = 12,
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag("assistant_confirmation").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_confirm_countdown").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_confirm_approve").performClick()
        composeRule.onNodeWithTag("assistant_confirm_reject").performClick()

        assertEquals(
            listOf(
                AssistantOverlayAction.ApproveConfirmation,
                AssistantOverlayAction.RejectConfirmation,
            ),
            actions,
        )
    }

    @Test
    fun errorStateShowsMessageAndRetry() {
        val actions = mutableListOf<AssistantOverlayAction>()
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.ERROR,
                errorMessage = "网络不可用",
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag("assistant_error").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_retry").performClick()
        assertEquals(listOf(AssistantOverlayAction.RetryAfterError), actions)
    }

    @Test
    fun missingConfigShowsGuidance() {
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.MISSING_CONFIG,
                configIssue = AssistantConfigIssue.MISSING_AGENT_MODEL,
            ),
        )

        composeRule.onNodeWithTag("assistant_missing_config").assertIsDisplayed()
    }

    @Test
    fun generatingStateOffersStopAndClose() {
        val actions = mutableListOf<AssistantOverlayAction>()
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.GENERATING,
                userText = "问题",
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag("assistant_user_text").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_stop_task").performClick()
        composeRule.onNodeWithTag("assistant_close").performClick()

        assertEquals(
            listOf(
                AssistantOverlayAction.StopTask,
                AssistantOverlayAction.CloseOverlay,
            ),
            actions,
        )
    }

    @Test
    fun speakingStateOffersStopPlayback() {
        val actions = mutableListOf<AssistantOverlayAction>()
        render(
            AssistantOverlayUiState(
                phase = AssistantSessionPhase.SPEAKING,
                responseText = "回答内容",
                isSpeaking = true,
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag("assistant_stop_speaking").performClick()
        assertEquals(listOf(AssistantOverlayAction.StopSpeaking), actions)
    }

    @Test
    fun openInChatCallbackIsInvoked() {
        var opened = false
        render(
            AssistantOverlayUiState(phase = AssistantSessionPhase.FOLLOW_UP_IDLE),
            onOpenInChat = { opened = true },
        )

        composeRule.onNodeWithTag("assistant_open_in_chat").performClick()
        assertTrue(opened)
    }
}
