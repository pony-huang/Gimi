package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.apps.App
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 固定 ADK 1.4 的事件与回调边界，避免升级破坏流式归并、拒绝工具或错误恢复。 */
class Adk14EventContractTest {
    @Test
    fun longRunningInputResumesWithTheAnswerWithoutRepeatingTheTool() = runTest {
        var executions = 0
        var modelCalls = 0
        var answerSeen = false
        val tool = object : BaseTool("get_user_choice", "fixture", isLongRunning = true) {
            override fun declaration() = FunctionDeclaration(name, description)
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
                executions++
                context.actions.skipSummarization = true
                return mapOf("status" to "waiting")
            }
        }
        val model = object : Model {
            override val name = "input-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                if (modelCalls++ == 0) {
                    emit(LlmResponse(content = Content(role = "model", parts = listOf(
                        Part(functionCall = FunctionCall(id = "input-1", name = tool.name, args = emptyMap())),
                    ))))
                } else {
                    answerSeen = request.contents.flatMap { it.parts }.any {
                        it.functionResponse?.id == "input-1" && it.functionResponse?.response?.get("value") == "yes"
                    }
                    emit(response("done", false))
                }
            }
        }
        val execution = execution(model, listOf(tool))
        execution.send("ask").toList()
        val resumed = execution.respondToInputRequest("input-1", tool.name, mapOf("value" to "yes")).toList()
        assertEquals(1, executions)
        assertTrue(answerSeen)
        assertTrue(resumed.any { it.content?.parts?.any { part -> part.text == "done" } == true })
    }
    @Test
    fun streamedChunksKeepOneIdEvenWhenPluginReplacesEvents() = runTest {
        val pluginIds = mutableListOf<String>()
        val plugin = object : Plugin {
            override val name = "replace-event"
            override suspend fun onEvent(invocationContext: InvocationContext, event: Event): Event {
                if (event.author == "agent" && event.content != null) pluginIds += event.id
                return event.copy(id = "replacement-id")
            }
        }
        val model = fixtureModel {
            listOf(response("a", true), response("b", true), response("ab", false))
        }
        val execution = execution(model, plugins = listOf(plugin))
        val events = execution.send("hello").toList().filter { it.author == "agent" && it.content != null }
        assertEquals(3, events.size)
        assertEquals(1, events.map { it.id }.distinct().size)
        assertEquals(pluginIds, events.map { it.id })
        assertFalse(events.last().partial)
    }

    @Test
    fun errorOnlyResponseIsDeliveredBeforeExecutionFails() = runTest {
        val execution = execution(fixtureModel { listOf(LlmResponse(errorCode = "MODEL_FAILURE")) })
        val received = mutableListOf<Event>()
        try {
            execution.send("hello").collect { received += it }
            fail("Error-only response must fail the execution")
        } catch (error: IllegalStateException) {
            assertEquals("MODEL_FAILURE", error.message)
        }
        assertEquals("MODEL_FAILURE", received.last().errorCode)
    }

    @Test
    fun rejectedToolRunsAfterCallbackButNeverRunsSideEffect() = runTest {
        var modelCalls = 0
        var executed = 0
        var afterCalls = 0
        val tool = object : BaseTool("write", "fixture") {
            override fun declaration() = FunctionDeclaration(name, description)
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
                executed++
                return mapOf("status" to "executed")
            }
        }
        val plugin = object : Plugin {
            override val name = "reject-tool"
            override suspend fun beforeTool(context: ToolContext, tool: BaseTool, args: Map<String, Any?>): CallbackChoice<Map<String, Any?>, Map<String, Any?>> =
                CallbackChoice.Break(mapOf("status" to "denied"))
            override suspend fun afterTool(context: ToolContext, tool: BaseTool, args: Map<String, Any?>, result: Map<String, Any?>): Map<String, Any?> {
                afterCalls++
                assertEquals("denied", result["status"])
                return result
            }
        }
        val model = fixtureModel {
            if (modelCalls++ == 0) listOf(LlmResponse(content = Content(role = "model", parts = listOf(
                Part(functionCall = FunctionCall(id = "write-1", name = "write", args = emptyMap())),
            )))) else listOf(response("done", false))
        }
        execution(model, listOf(tool), listOf(plugin)).send("write").toList()
        assertEquals(0, executed)
        assertEquals(1, afterCalls)
    }

    private fun execution(model: Model, tools: List<BaseTool> = emptyList(), plugins: List<Plugin> = emptyList()): AgentChatExecution =
        AgentChatExecution("user", "session", InMemoryRunner(App(
            appName = AgentChatRunner.APP_NAME,
            rootAgent = LlmAgent(name = "agent", model = model, tools = tools),
            plugins = plugins,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
        )), emptyMap())

    private fun fixtureModel(responses: () -> List<LlmResponse>): Model = object : Model {
        override val name = "event-fixture"
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
            responses().forEach { emit(it) }
        }
    }

    private fun response(text: String, partial: Boolean) = LlmResponse(
        content = Content(role = "model", parts = listOf(Part(text = text))), partial = partial,
    )
}
