package github.ponyhuang.gimi.domain.assistant.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSessionStateTest {
    @Test
    fun activeTaskDependsOnExecutionOwnershipNotOverlayVisibility() {
        assertTrue(
            AssistantSessionState(
                taskActive = true,
                presentationVisible = false,
            ).hasActiveTask,
        )
        assertFalse(
            AssistantSessionState(
                taskActive = false,
                presentationVisible = true,
            ).hasActiveTask,
        )
    }

    @Test
    fun freshCaptureKeepsCapsuleCollapsed() {
        val state = AssistantSessionState().applyPresentationEvent(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
        )
        assertFalse(state.shouldShowConversation)
    }

    @Test
    fun transcriptReadyExpandsConversationPanel() {
        val state = AssistantSessionState().applyPresentationEvent(
            AssistantPresentationEvent.TranscriptReady("今天天气怎么样"),
        )
        assertTrue(state.shouldShowConversation)
    }

    @Test
    fun generatingAndFollowUpKeepConversationExpanded() {
        val generating = AssistantSessionState()
            .applyPresentationEvent(AssistantPresentationEvent.TranscriptReady("讲个笑话"))
            .appendUserMessage("讲个笑话")
            .appendAssistantMessage()
        assertTrue(generating.shouldShowConversation)

        val followUp = generating.applyPresentationEvent(AssistantPresentationEvent.Completed)
        assertTrue(followUp.shouldShowConversation)
    }

    @Test
    fun pendingConfirmationAloneExpandsConversationPanel() {
        val state = AssistantSessionState(
            pendingConfirmation = PendingAssistantConfirmation(
                confirmationCallId = "call-1",
                toolName = "media_volume",
                deadlineEpochMs = 0L,
            ),
        )
        assertTrue(state.shouldShowConversation)
    }

    @Test
    fun newCaptureAfterConversationCollapsesAgain() {
        val conversing = AssistantSessionState()
            .applyPresentationEvent(AssistantPresentationEvent.TranscriptReady("讲个笑话"))
            .appendUserMessage("讲个笑话")
            .appendAssistantMessage()
        assertTrue(conversing.shouldShowConversation)

        val recaptured = conversing.applyPresentationEvent(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
        )
        assertFalse(recaptured.shouldShowConversation)
    }

    @Test
    fun captureAbandonedHidesPresentationImmediately() {
        val listening = AssistantSessionState().applyPresentationEvent(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
        )
        assertTrue(listening.presentationVisible)

        val abandoned = listening.applyPresentationEvent(AssistantPresentationEvent.CaptureAbandoned)
        assertFalse(abandoned.presentationVisible)
        assertFalse(abandoned.shouldShowConversation)
        assertFalse(abandoned.taskActive)
    }
}
