package github.ponyhuang.gimi.data.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.ThinkingBlock
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeStreamingThoughtTest {
    private val subject = TestClaude()

    @Test
    fun `thinking delta is mapped to a partial thought part`() {
        val event = RawMessageStreamEvent.ofContentBlockDelta(
            RawContentBlockDeltaEvent.builder()
                .index(0)
                .thinkingDelta("分析中")
                .build(),
        )

        val part = subject.streamingPart(event)

        assertEquals("分析中", part?.text)
        assertEquals(true, part?.thought)
    }

    @Test
    fun `final thinking block keeps its signature`() {
        val block = ContentBlock.ofThinking(
            ThinkingBlock.builder()
                .thinking("完整推理")
                .signature("signed")
                .build(),
        )

        val part = subject.part(block)

        assertEquals("完整推理", part?.text)
        assertEquals(true, part?.thought)
        assertArrayEquals("signed".toByteArray(), part?.thoughtSignature)
    }

    private class TestClaude : Claude("test", mockk<AnthropicClient>(relaxed = true)) {
        fun streamingPart(event: RawMessageStreamEvent) = event.streamingPartOrNull()

        fun part(block: ContentBlock) = block.toPart()
    }
}
