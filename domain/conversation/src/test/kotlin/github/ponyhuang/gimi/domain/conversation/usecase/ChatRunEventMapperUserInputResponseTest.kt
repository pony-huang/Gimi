package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 用户输入工具（get_user_choice / adk_request_input）的回执以 role=user 事件到达，
 * 是工具结果本身 —— 流式归约与历史回放共用本映射，必须保留供 chip 配对成 ✓。
 */
class ChatRunEventMapperUserInputResponseTest {

    @Test
    fun `user input tool response is preserved as an assistant response message`() {
        val event = userResponseEvent(
            ChatFunctionResponse(id = "call-1", name = "adk_request_input"),
        )

        val message = ChatRunEventMapper.fromEvent(event)

        assertEquals(MessageRole.Assistant, message?.role)
        assertEquals("event-input", message?.id)
        assertEquals("adk_request_input", message?.functionResponses?.single()?.name)
        assertEquals("call-1", message?.functionResponses?.single()?.id)
    }

    @Test
    fun `confirmation protocol response is still ignored`() {
        val event = userResponseEvent(
            ChatFunctionResponse(id = "confirm-1", name = "adk_request_confirmation"),
        )

        assertNull(ChatRunEventMapper.fromEvent(event))
    }

    private fun userResponseEvent(vararg responses: ChatFunctionResponse) = ChatRunEvent(
        id = "event-input",
        invocationId = "invocation-input",
        author = "user",
        parts = emptyList(),
        functionCalls = emptyList(),
        functionResponses = responses.toList(),
        partial = false,
        turnComplete = true,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )
}
