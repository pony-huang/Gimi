package github.ponyhuang.asssistantai.ui.chat

import github.ponyhuang.asssistantai.model.FunctionCallView
import github.ponyhuang.asssistantai.model.Message
import github.ponyhuang.asssistantai.model.MessageRole
import github.ponyhuang.asssistantai.model.TextPart
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

    private fun assistantMessage(
        textParts: List<TextPart> = emptyList(),
        functionCalls: List<FunctionCallView> = emptyList(),
    ) = Message(
        author = "assistant",
        role = MessageRole.Assistant,
        textParts = textParts,
        functionCalls = functionCalls,
    )
}
