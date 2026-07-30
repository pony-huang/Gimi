package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageVisibilityTest {
    @Test
    fun hidesToolOnlyMessageWhenToolActivityIsDisabled() {
        val message = assistantMessage(
            functionCalls = listOf(FunctionCallView("call", "search_media_files", "()")),
        )

        assertFalse(message.isVisibleInChat(showToolActivity = false))
        assertTrue(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun keepsTextMessageWhenToolActivityIsDisabled() {
        val message = assistantMessage(
            textParts = listOf(TextPart(text = "已找到文件")),
            functionCalls = listOf(FunctionCallView("call", "search_media_files", "()")),
        )

        assertTrue(message.isVisibleInChat(showToolActivity = false))
    }

    @Test
    fun hidesConfirmationOnlyMessageEvenWhenToolActivityIsEnabled() {
        val message = assistantMessage(
            functionCalls = listOf(FunctionCallView("confirm-1", ConfirmationToolName, "")),
        )

        assertTrue(message.visibleFunctionCalls().isEmpty())
        assertFalse(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun hidesToolSearchProtocolMessagesEvenWhenToolActivityIsEnabled() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("search-1", ToolSearchProtocolName, "{}")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("search-1", ToolSearchProtocolName)),
        )

        assertTrue(call.visibleFunctionCalls().isEmpty())
        assertTrue(response.visibleFunctionResponses().isEmpty())
        assertFalse(call.isVisibleInChat(showToolActivity = true))
        assertFalse(response.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun keepsRealToolCallsWhileFilteringConfirmation() {
        val message = assistantMessage(
            functionCalls = listOf(
                FunctionCallView("call", "search_media_files", "()"),
                FunctionCallView("confirm-1", ConfirmationToolName, ""),
            ),
        )

        assertTrue(message.visibleFunctionCalls().map { it.name } == listOf("search_media_files"))
        assertTrue(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun foldsResponseOnlyMessageIntoPreviousAssistantMessage() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "maps_geo", "(address=\"白云山\")")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(call, response).foldToolResponses()

        assertEquals(1, folded.size)
        assertEquals(listOf("c1"), folded[0].functionCalls.map { it.id })
        assertEquals(listOf("c1"), folded[0].functionResponses.map { it.id })
    }

    @Test
    fun deduplicatesResponsesDeliveredTwiceByConfirmationFlow() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "maps_geo", "()")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(call, response, response).foldToolResponses()

        assertEquals(1, folded.size)
        assertEquals(1, folded[0].functionResponses.size)
    }

    @Test
    fun keepsResponseOnlyMessageWhenPreviousMessageIsNotAssistant() {
        val userMessage = Message(
            author = "user",
            role = MessageRole.User,
            textParts = listOf(TextPart(text = "当前位置")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(userMessage, response).foldToolResponses()

        assertEquals(2, folded.size)
    }

    @Test
    fun doesNotFoldMessagesThatCarryText() {
        val first = assistantMessage(textParts = listOf(TextPart(text = "先查一下")))
        val second = assistantMessage(
            textParts = listOf(TextPart(text = "结果如下")),
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(first, second).foldToolResponses()

        assertEquals(2, folded.size)
    }

    private fun assistantMessage(
        textParts: List<TextPart> = emptyList(),
        functionCalls: List<FunctionCallView> = emptyList(),
        functionResponses: List<FunctionResponseView> = emptyList(),
    ) = Message(
        author = "assistant",
        role = MessageRole.Assistant,
        textParts = textParts,
        functionCalls = functionCalls,
        functionResponses = functionResponses,
    )
}
