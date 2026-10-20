package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.domain.a2ui.A2uiInteraction
import github.ponyhuang.gimi.domain.a2ui.A2uiInteractionCodec
import github.ponyhuang.gimi.domain.a2ui.A2uiToolResult
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.usecase.ChatRunEventMapper
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 历史与实时适配必须保留相同协议数据，避免重载后卡片或已提交值消失。 */
class A2uiEventMappingTest {
    @Test
    fun toolRevisionSurvivesBothHistoryAndLiveProjection() {
        val document = A2uiDocument("surface", emptyList())
        val result = FunctionResponse(id = "call", name = A2uiToolResult.TOOL_NAME,
            response = mapOf(A2uiToolResult.DOCUMENT_KEY to A2uiToolResult.encode(document)))
        val persisted = EventMapper.fromEvent(event("assistant", emptyList(), listOf(result)))!!
        val live = ChatRunEventMapper.fromEvent(runEvent("assistant").copy(functionResponses = listOf(
            ChatFunctionResponse("call", A2uiToolResult.TOOL_NAME, a2uiDocument = document),
        )))!!
        assertEquals(document, persisted.functionResponses.single().a2uiDocument)
        assertEquals(persisted.functionResponses, live.functionResponses)
    }

    @Test
    fun submittedInteractionRestoresAsStructuredUserInputWithoutRawJsonBubble() {
        val interaction = A2uiInteraction("surface", "button", "submit", 1, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val raw = A2uiInteractionCodec.encode(interaction)
        val persisted = EventMapper.fromEvent(event("user", listOf(Part(text = raw)), emptyList()))!!
        val live = ChatRunEventMapper.fromEvent(runEvent("user").copy(parts = listOf(ChatRunPart(text = raw))))!!
        assertEquals(interaction, persisted.a2uiInteraction)
        assertEquals(interaction, live.a2uiInteraction)
        assertTrue(persisted.textParts.isEmpty())
        assertTrue(live.textParts.isEmpty())
    }

    private fun runEvent(author: String) = ChatRunEvent("event", "inv", author, emptyList(), emptyList(),
        emptyList(), false, true, null, null, 1)

    private fun event(author: String, parts: List<Part>, responses: List<FunctionResponse>): Event = mockk {
        every { id } returns "event"
        every { invocationId } returns "inv"
        every { this@mockk.author } returns author
        every { content } returns Content(parts = parts)
        every { functionCalls() } returns emptyList()
        every { functionResponses() } returns responses
        every { partial } returns false
        every { turnComplete } returns true
        every { errorCode } returns null
        every { errorMessage } returns null
        every { timestamp } returns 1L
    }
}
