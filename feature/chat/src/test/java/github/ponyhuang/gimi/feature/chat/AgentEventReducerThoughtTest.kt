package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 思考段与正文段必须各自拥有稳定且互不相同的 [github.ponyhuang.gimi.domain.conversation.model.TextPart] id。 */
class AgentEventReducerThoughtTest {
    private val runToken = Any()
    private val runtime = ChatSessionRuntime("session").also { it.runToken = runToken }
    private val deltas = mutableListOf<Pair<String, String>>()
    private val reducer = AgentEventReducer(
        runtimeOrNull = { if (it == runtime.sessionId) runtime else null },
        runtimeFor = { runtime },
        publishRuntime = {},
        emitPartDelta = { _, partId, delta -> deltas += partId to delta },
        toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true) {
            every { tools } returns MutableStateFlow(emptyList())
        },
        isAutoApproved = { false },
    )

    @Test
    fun `thought delta and answer delta in one stream take distinct part ids`() {
        reducer.applyEvent(runtime.sessionId, event(text = "先想想", thought = true), runToken)
        reducer.applyEvent(runtime.sessionId, event(text = "答案是 42", thought = false), runToken)

        val message = runtime.messages.single()
        assertEquals(listOf("先想想", "答案是 42"), message.textParts.map { it.text })
        assertEquals(listOf(true, false), message.textParts.map { it.thought })
        assertNotEquals(message.textParts[0].id, message.textParts[1].id)
    }

    @Test
    fun `answer part channel only carries the answer text`() {
        reducer.applyEvent(runtime.sessionId, event(text = "先想想", thought = true), runToken)
        reducer.applyEvent(runtime.sessionId, event(text = "答案是 42", thought = false), runToken)

        val answerPart = runtime.messages.single().textParts.single { !it.thought }
        assertEquals(
            listOf("答案是 42"),
            deltas.filter { it.first == answerPart.id }.map { it.second },
        )
    }

    private fun event(text: String, thought: Boolean): ChatRunEvent = ChatRunEvent(
        id = "stream",
        invocationId = "invocation",
        author = "assistant",
        parts = listOf(ChatRunPart(text = text, thought = thought)),
        functionCalls = emptyList(),
        functionResponses = emptyList(),
        partial = true,
        turnComplete = false,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )
}
