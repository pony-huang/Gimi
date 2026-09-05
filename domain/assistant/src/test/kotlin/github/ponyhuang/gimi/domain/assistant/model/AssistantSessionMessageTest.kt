package github.ponyhuang.gimi.domain.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSessionMessageTest {

    @Test
    fun captureStartedStartsANewEmptySession() {
        val state = AssistantSessionState(
            turn = AssistantTurn(userText = "旧指令", responseText = "旧回答"),
            messages = listOf(
                AssistantMessage(1, AssistantMessageAuthor.USER, "旧指令"),
                AssistantMessage(2, AssistantMessageAuthor.ASSISTANT, "旧回答"),
            ),
        )

        val next = state.applyPresentationEvent(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.ASSISTANT_PANEL),
        )

        assertEquals(AssistantSessionPhase.LISTENING, next.phase)
        assertTrue(next.messages.isEmpty())
    }

    @Test
    fun submitAppendsUserThenStreamingAssistantMessage() {
        val state = AssistantSessionState()
            .appendUserMessage("帮我查天气")
            .appendAssistantMessage(streaming = true)

        assertEquals(2, state.messages.size)
        assertEquals(AssistantMessageAuthor.USER, state.messages[0].author)
        assertEquals("帮我查天气", state.messages[0].text)
        assertEquals(AssistantMessageAuthor.ASSISTANT, state.messages[1].author)
        assertTrue(state.messages[1].streaming)
    }

    @Test
    fun streamingAssistantMessageIsFinalizedInPlace() {
        val state = AssistantSessionState()
            .appendUserMessage("问题")
            .appendAssistantMessage(streaming = true)
            .updateLastAssistantMessage(text = "完整回答")

        val reply = state.messages.last()
        assertEquals("完整回答", reply.text)
        assertFalse(reply.streaming)
    }

    @Test
    fun toolNamesAreCarriedOnAssistantMessage() {
        val state = AssistantSessionState()
            .appendUserMessage("打开网页")
            .appendAssistantMessage(toolNames = listOf("browse"))

        assertEquals(listOf("browse"), state.messages.last().toolNames)
    }

    @Test
    fun errorFinalizesAndFlagsAssistantMessage() {
        val state = AssistantSessionState()
            .appendUserMessage("问题")
            .appendAssistantMessage(streaming = true)
            .failLastAssistantMessage("模型不可用")

        val reply = state.messages.last()
        assertFalse(reply.streaming)
        assertTrue(reply.isError)
        assertEquals("模型不可用", reply.text)
    }
}
