package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.Message
import github.ponyhuang.asssistantai.domain.conversation.model.MessageRole
import github.ponyhuang.asssistantai.domain.conversation.model.TextPart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionRuntimeTest {
    @Test
    fun reseedPartialChannelsPublishesTheFullPrefixBeforeNewDeltas() = runTest {
        val runtime = ChatSessionRuntime("session")
        runtime.messages = listOf(
            Message(
                id = "message",
                author = "assistant",
                role = MessageRole.Assistant,
                textParts = listOf(TextPart(id = "part", text = "complete prefix")),
                partial = true,
            ),
        )

        runtime.reseedPartialChannels()
        assertEquals("complete prefix", runtime.partChannel("part")?.receive())

        runtime.emitPartDelta("part", " + suffix")
        assertEquals(" + suffix", runtime.partChannel("part")?.receive())
        runtime.closePartChannels()
    }
}
