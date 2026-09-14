package github.ponyhuang.gimi.data.agent.model

import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionChunk
import io.mockk.mockk
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenaiStreamingThoughtTest {
    private val subject = TestOpenai()

    @Test
    fun `reasoning content extension is mapped to a thought part`() {
        val parts = subject.extractedParts(
            delta(
                additionalProperties = mapOf(
                    "reasoning_content" to JsonValue.from("分析中"),
                ),
            ),
        )

        assertEquals(1, parts.size)
        assertEquals("分析中", parts.single().text)
        assertEquals(true, parts.single().thought)
    }

    @Test
    fun `compatible thinking field and regular content remain distinct`() {
        val parts = subject.extractedParts(
            delta(
                content = "最终答案",
                additionalProperties = mapOf("thinking" to JsonValue.from("推理")),
            ),
        )

        assertEquals(listOf(true, false), parts.map { it.thought })
        assertEquals(listOf("推理", "最终答案"), parts.map { it.text })
    }

    private fun delta(
        content: String? = null,
        additionalProperties: Map<String, JsonValue> = emptyMap(),
    ): ChatCompletionChunk.Choice {
        val delta = ChatCompletionChunk.Choice.Delta.builder()
            .additionalProperties(additionalProperties)
            .also { builder -> content?.let(builder::content) }
            .build()
        return ChatCompletionChunk.Choice.builder()
            .index(0)
            .delta(delta)
            .finishReason(Optional.empty())
            .build()
    }

    private class TestOpenai : Openai("test", mockk<OpenAIClient>(relaxed = true)) {
        fun extractedParts(choice: ChatCompletionChunk.Choice) = choice.streamingParts()
    }
}
