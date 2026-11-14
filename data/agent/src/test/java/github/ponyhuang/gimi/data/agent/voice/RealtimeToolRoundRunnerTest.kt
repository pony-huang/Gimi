@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.State
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeFunctionCallSink

/** 一轮 Realtime 工具调用的编排边界：逐个写回结果、整轮只触发一次二轮推理。 */
class RealtimeToolRoundRunnerTest {

    /** 记录执行的测试工具；返回以工具名构成的 JSON 结果。 */
    private class RecordingTool(name: String) : BaseTool(name, "test tool") {
        var executed = 0

        override fun declaration(): FunctionDeclaration? = FunctionDeclaration(name = name, description = description)

        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            executed++
            return mapOf("name" to name)
        }
    }

    /** 记录回写帧的假通道；trigger 次数单独计数。 */
    private class RecordingSink : RealtimeFunctionCallSink {
        val results = mutableListOf<Pair<String, String>>()
        var triggers = 0

        override suspend fun sendFunctionResult(callId: String, outputJson: String) {
            results += callId to outputJson
        }

        override suspend fun sendResponseTrigger() {
            triggers++
        }
    }

    /** 回写通道在写结果时断开；二轮推理仍必须触发，否则服务端永远不收尾。 */
    private class FailingSink : RealtimeFunctionCallSink {
        var triggers = 0

        override suspend fun sendFunctionResult(callId: String, outputJson: String) {
            throw IllegalStateException("connection closed")
        }

        override suspend fun sendResponseTrigger() {
            triggers++
        }
    }

    @Test
    fun `round writes each result then triggers the second round once`() = runTest {
        val sink = RecordingSink()
        val runner = RealtimeToolRoundRunner(
            RealtimeVoiceToolBridge(
                toolsByName = listOf(RecordingTool("get_weather"), RecordingTool("get_train_price")).associateBy { it.name },
                invocation = testInvocation(),
                ownedResources = emptyList(),
            ),
            sink,
        )
        runner.run(listOf(
            FunctionCall(name = "get_weather", args = mapOf("city" to "杭州"), id = "c1"),
            FunctionCall(name = "get_train_price", args = emptyMap(), id = "c2"),
        ))
        assertEquals(listOf(
            "c1" to """{"name":"get_weather"}""",
            "c2" to """{"name":"get_train_price"}""",
        ), sink.results)
        assertEquals(1, sink.triggers)
    }

    @Test
    fun `calls without a call id are skipped but the round still triggers`() = runTest {
        val tool = RecordingTool("get_weather")
        val sink = RecordingSink()
        RealtimeToolRoundRunner(
            RealtimeVoiceToolBridge(mapOf("get_weather" to tool), testInvocation(), emptyList()),
            sink,
        ).run(listOf(FunctionCall(name = "get_weather", args = emptyMap(), id = null)))
        assertEquals(0, tool.executed)
        assertEquals(1, sink.triggers)
    }

    @Test
    fun `missing reply channel abandons the round without executing tools`() = runTest {
        val tool = RecordingTool("get_weather")
        RealtimeToolRoundRunner(
            RealtimeVoiceToolBridge(mapOf("get_weather" to tool), testInvocation(), emptyList()),
            reply = null,
        ).run(listOf(FunctionCall(name = "get_weather", args = emptyMap(), id = "c1")))
        assertEquals(0, tool.executed)
    }

    @Test
    fun `no tool bridge still closes the round instead of leaving the server waiting`() = runTest {
        val sink = RecordingSink()
        RealtimeToolRoundRunner(bridge = null, reply = sink).run(listOf(
            FunctionCall(name = "get_weather", args = emptyMap(), id = "c1"),
            FunctionCall(name = "get_time", args = emptyMap(), id = "c2"),
        ))
        assertEquals(listOf(
            "c1" to """{"error":"Tool bridge unavailable"}""",
            "c2" to """{"error":"Tool bridge unavailable"}""",
        ), sink.results)
        assertEquals(1, sink.triggers)
    }

    @Test
    fun `a failing result write still triggers the second round`() = runTest {
        val sink = FailingSink()
        val runner = RealtimeToolRoundRunner(
            RealtimeVoiceToolBridge(mapOf("get_weather" to RecordingTool("get_weather")), testInvocation(), emptyList()),
            sink,
        )
        try {
            runner.run(listOf(FunctionCall(name = "get_weather", args = emptyMap(), id = "c1")))
        } catch (_: IllegalStateException) {
            // 回写异常照常向上传播，这里只断言 finally 里的二轮触发。
        }
        assertEquals(1, sink.triggers)
    }

    private companion object {
        /** 与生产侧一致的最小 InvocationContext；占位 agent 永不运行。 */
        fun testInvocation(): InvocationContext = InvocationContext(
            session = Session(
                SessionKey("gimi-test", "user", "session"),
                State(),
                mutableListOf(),
            ),
            runConfig = RunConfig(customMetadata = emptyMap()),
            agent = TestHostAgent,
            invocationId = "inv-1",
        )

        private object TestHostAgent : BaseAgent("test-host", "test host") {
            override fun runAsyncImpl(context: InvocationContext): Flow<Event> = emptyFlow()
        }
    }
}
