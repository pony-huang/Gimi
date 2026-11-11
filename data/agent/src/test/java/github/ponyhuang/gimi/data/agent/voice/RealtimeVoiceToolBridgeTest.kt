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
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 实时助手工具桥的执行边界：结果 JSON 序列化、未知工具/失败降级为错误对象、
 * 确认类工具在语音通话内的自动批准。
 */
class RealtimeVoiceToolBridgeTest {

    /** 记录型工具：可直接子类化 [BaseTool]，绕开生成工具的参数转换。 */
    private class RecordingTool(
        name: String,
        private val result: Any?,
        private val failure: Exception? = null,
    ) : BaseTool(name, "test tool") {
        val receivedArgs = mutableListOf<Map<String, Any?>>()

        override fun declaration(): FunctionDeclaration? = FunctionDeclaration(name = name, description = description)

        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            receivedArgs += args
            failure?.let { throw it }
            return result ?: ""
        }
    }

    private fun bridge(vararg tools: BaseTool): RealtimeVoiceToolBridge = RealtimeVoiceToolBridge(
        toolsByName = tools.associateBy { it.name },
        invocation = testInvocation(),
        ownedResources = emptyList(),
    )

    @Test
    fun `execute serializes the tool result into the protocol output`() = runTest {
        val tool = RecordingTool("get_weather", mapOf("temperature" to 18, "condition" to "晴"))
        val output = bridge(tool).execute("c1", "get_weather", mapOf("city" to "杭州"))
        assertEquals("""{"temperature":18,"condition":"晴"}""", output)
        assertEquals(listOf(mapOf("city" to "杭州")), tool.receivedArgs)
    }

    @Test
    fun `unknown tool degrades to an error object instead of breaking the session`() = runTest {
        val output = bridge(RecordingTool("get_weather", "{}")).execute("c1", "nope", emptyMap())
        assertEquals("""{"error":"Unknown tool: nope"}""", output)
    }

    @Test
    fun `tool failure degrades to an error object with the message`() = runTest {
        val tool = RecordingTool("get_weather", null, failure = IllegalStateException("boom"))
        val output = bridge(tool).execute("c1", "get_weather", emptyMap())
        assertEquals("""{"error":"boom"}""", output)
    }

    @Test
    fun `confirmation required tools execute under the voice auto approval`() = runTest {
        // requireConfirmation = true 的 FunctionTool 在无 ToolConfirmation 时会拒绝执行；
        // 桥对确认门统一放行（与语音 Chat 模式一致），这里验证放行后真的执行了。
        val approved = object : FunctionTool(name = "set_flashlight", description = "test", requiresConfirmation = { true }) {
            override fun declaration(): FunctionDeclaration? = FunctionDeclaration(name = name, description = description)
            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any = mapOf("success" to true)
        }
        val output = bridge(approved).execute("c1", "set_flashlight", mapOf("enabled" to true))
        assertEquals("""{"success":true}""", output)
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
