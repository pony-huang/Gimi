package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.data.agent.tools.search.INLINE_DEFINITIONS
import github.ponyhuang.gimi.data.agent.tools.search.InlineToolSearchEvents
import github.ponyhuang.gimi.data.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.data.agent.tools.search.inlinePendingKey
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)
class InlineToolSearchPersistenceTest {
    @Test
    fun emptySearchResultPrecedesPersistedSystemEventAndReplaysOnNextTurn() = runTest {
        val sessions = InMemorySessionService()
        val clock = object : BaseTool("clock", "Read clock") {
            override fun declaration() = FunctionDeclaration(name, description)
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("time" to "12:00")
        }
        val search = object : BaseTool("tool_search", "Search tools") {
            override fun declaration() = FunctionDeclaration(name, description)
            override suspend fun processLlmRequest(context: ToolContext, request: LlmRequest): LlmRequest =
                InlineToolSearchEvents.appendPending(context, super.processLlmRequest(context, request), listOf(clock.declaration()))
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
                context.actions.stateDelta[ToolSearchToolset.STATE_KEY_LOADED_TOOLS] = listOf("clock")
                context.actions.stateDelta[inlinePendingKey(context.agentName)] = listOf(
                    adkJson.encodeToString(FunctionDeclaration.serializer(), clock.declaration()),
                )
                return emptyMap<String, Any>()
            }
        }
        val requests = mutableListOf<LlmRequest>()
        var failAfterInsertion = true
        val model = object : Model {
            override val name = "fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                requests += request
                if (requests.size == 2 && failAfterInsertion) {
                    failAfterInsertion = false
                    error("Interrupted after system insertion")
                }
                val content = if (requests.size == 1) Content(role = "model", parts = listOf(
                    Part(functionCall = FunctionCall(id = "search", name = "tool_search", args = emptyMap())),
                )) else Content(role = "model", parts = listOf(Part(text = "Done")))
                emit(LlmResponse(content = content))
            }
        }
        val runner = InMemoryRunner(App(
            appName = AgentChatRunner.APP_NAME,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
            rootAgent = LlmAgent(name = "agent", model = model, tools = listOf(search, clock)),
        ), sessionService = sessions)
        val metadata = ToolRunMetadata.of(ModelRuntimeMetadata(
            "deepseek", ApiProtocol.Anthropic, "deepseek-v4-pro", "https://example.test",
        ), null, true)
        val execution = AgentChatExecution("user", "session", runner, metadata)
        try {
            execution.send("search").toList()
            fail("Expected interruption")
        } catch (expected: IllegalStateException) {
            assertEquals("Interrupted after system insertion", expected.message)
        }
        execution.send("search", retry = true).toList()
        execution.send("continue").toList()

        val events = sessions.getSession(SessionKey(AgentChatRunner.APP_NAME, "user", "session"))!!.events
        val index = events.indexOfFirst { it.content?.role == "system" }
        assertTrue(index > 0)
        assertEquals(emptyMap<String, Any>(), events[index - 1].functionResponses().single().response)
        assertEquals(1, events.count { it.content?.role == "system" })
        val restored = adkJson.decodeFromString(Event.serializer(), adkJson.encodeToString(Event.serializer(), events[index]))
        assertNotNull(restored.content!!.parts.single().partMetadata!![INLINE_DEFINITIONS])
        assertEquals(1, requests.last().contents.count { it.role == "system" })
    }
}
