package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.events.Event
import com.google.adk.kt.models.*
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.a2ui.*
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 重试是独立付费边界，只允许复用已归档任务，不能重跑主 Agent 或改变 surface。 */
class A2uiCreateRetryTest {
    @Test
    fun retryUsesArchivedFactsAndOriginalSurfaceWithoutAParentRun() = runTest {
        val sessions = InMemorySessionService()
        val key = SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, "chat")
        val session = sessions.createSession(key)
        val task = A2uiGenerationTask("call", "surface", "invocation", "trusted image facts", setOf("Image"), A2uiGenerationStatus.FAILED)
        sessions.appendEvent(session, Event(author = "Assistant", content = Content(role = Role.MODEL, parts = listOf(
            Part(functionResponse = FunctionResponse(id = "call", name = A2uiToolResult.TOOL_NAME, response = taskResult(task))),
        ))))
        var calls = 0
        val model = object : Model {
            override val name = "fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): kotlinx.coroutines.flow.Flow<LlmResponse> {
                calls++
                assertTrue(request.contents.single().parts.single().text!!.contains(task.changes))
                assertEquals(listOf("render_a2ui"), request.config.tools!!.flatMap { it.functionDeclarations.orEmpty() }.map { it.name })
                return flowOf(LlmResponse(content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
                    name = "render_a2ui", args = mapOf("components" to """[{"id":"root","component":"Image","url":"https://fixture.invalid/image"}]""", "data" to "{}"),
                ))))))
            }
        }
        val factory = mockk<AgentLLMModelFactory> {
            every { selectModelConfig(any()) } returns mockk(relaxed = true)
            every { createModel(any()) } returns model
        }
        val retry = A2uiCreateRetry(factory, mockk(relaxed = true), sessions)

        val events = retry.retry("chat", ModelSelection("service", "group", "fixture"), task.callId, A2uiCapabilities("catalog", "{}")).toList()

        assertEquals(1, calls)
        assertEquals(2, events.size)
        @Suppress("UNCHECKED_CAST")
        val result = events.last().customMetadata!![A2uiGenerationTaskCodec.RESULT_KEY] as Map<String, Any?>
        assertEquals(task.surfaceId, A2uiToolResult.extract(A2uiToolResult.TOOL_NAME, result)!!.surfaceId)
        assertEquals(task.invocationId, events.last().invocationId)
        assertTrue(sessions.getSession(key)!!.events.none { it.author == "user" })
    }
}
