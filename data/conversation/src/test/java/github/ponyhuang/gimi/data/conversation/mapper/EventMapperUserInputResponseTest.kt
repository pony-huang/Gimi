package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 用户输入工具（get_user_choice / adk_request_input）的回执是 role=user 的
 * FunctionResponse，且本身就是工具结果 —— 映射层必须保留它供 chip 配对，
 * 不能落入"确认协议回执丢弃"的旧规则（否则调用 chip 永远显示 ✗）。
 */
class EventMapperUserInputResponseTest {

    @Test
    fun `user input tool response is preserved as an assistant response message`() {
        val event = userResponseEvent(
            FunctionResponse(
                name = "get_user_choice",
                id = "call-1",
                response = mapOf("choice" to "去散步"),
            ),
        )

        val message = EventMapper.fromEvent(event)

        assertEquals(MessageRole.Assistant, message?.role)
        assertEquals("event-input", message?.id)
        val response = message?.functionResponses?.single()
        assertEquals("call-1", response?.id)
        assertEquals("get_user_choice", response?.name)
    }

    @Test
    fun `confirmation protocol response is still ignored`() {
        val event = userResponseEvent(
            FunctionResponse(
                name = "adk_request_confirmation",
                id = "confirm-1",
                response = mapOf("confirmed" to true),
            ),
        )

        assertNull(EventMapper.fromEvent(event))
    }

    private fun userResponseEvent(vararg responses: FunctionResponse): Event = mockk {
        every { id } returns "event-input"
        every { invocationId } returns "invocation-input"
        every { author } returns "user"
        every { content } returns Content(role = Role.USER, parts = listOf(Part()))
        every { functionCalls() } returns emptyList()
        every { functionResponses() } returns responses.toList()
        every { partial } returns false
        every { turnComplete } returns true
        every { errorCode } returns null
        every { errorMessage } returns null
        every { timestamp } returns 123L
    }
}
