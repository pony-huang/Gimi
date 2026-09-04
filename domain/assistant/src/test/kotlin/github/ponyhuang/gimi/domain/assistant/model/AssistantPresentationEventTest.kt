package github.ponyhuang.gimi.domain.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPresentationEventTest {
    @Test
    fun `wake capture starts a fresh visible turn`() {
        val state = AssistantSessionState(
            phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
            turn = AssistantTurn("旧问题", "旧回答"),
            presentationVisible = false,
        ).applyPresentationEvent(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
        )

        assertEquals(AssistantSessionPhase.LISTENING, state.phase)
        assertEquals(AssistantInvocationSource.BLUETOOTH_WAKE, state.source)
        assertEquals(null, state.turn)
        assertTrue(state.presentationVisible)
    }

    @Test
    fun `transcript and speaking update the same turn`() {
        val transcribed = AssistantSessionState()
            .applyPresentationEvent(
                AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
            )
            .applyPresentationEvent(AssistantPresentationEvent.Transcribing)
            .applyPresentationEvent(AssistantPresentationEvent.TranscriptReady("打开地图"))
        val speaking = transcribed
            .copy(turn = transcribed.turn?.copy(responseText = "已经打开地图"))
            .applyPresentationEvent(AssistantPresentationEvent.Speaking)

        assertEquals("打开地图", speaking.turn?.userText)
        assertEquals("已经打开地图", speaking.turn?.responseText)
        assertEquals(AssistantSessionPhase.SPEAKING, speaking.phase)
        assertTrue(speaking.presentationVisible)
    }

    @Test
    fun `completion remains visible until host dismisses it`() {
        val completed = AssistantSessionState(taskActive = true, presentationVisible = true)
            .applyPresentationEvent(AssistantPresentationEvent.Completed)

        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, completed.phase)
        assertFalse(completed.taskActive)
        assertTrue(completed.presentationVisible)
    }

    @Test
    fun `dismissed presentation stays hidden for later phases of the same turn`() {
        val dismissed = AssistantSessionState(
            phase = AssistantSessionPhase.GENERATING,
            presentationVisible = false,
        )

        assertFalse(
            dismissed.applyPresentationEvent(AssistantPresentationEvent.Speaking)
                .presentationVisible,
        )
        assertFalse(
            dismissed.applyPresentationEvent(AssistantPresentationEvent.Completed)
                .presentationVisible,
        )
        assertFalse(
            dismissed.applyPresentationEvent(AssistantPresentationEvent.Failed("失败"))
                .presentationVisible,
        )
    }

    @Test
    fun `stopped event releases task and keeps result visible`() {
        val stopped = AssistantSessionState(taskActive = true, presentationVisible = true)
            .applyPresentationEvent(AssistantPresentationEvent.Stopped)

        assertEquals(AssistantSessionPhase.STOPPED, stopped.phase)
        assertFalse(stopped.taskActive)
        assertTrue(stopped.presentationVisible)
    }
}
