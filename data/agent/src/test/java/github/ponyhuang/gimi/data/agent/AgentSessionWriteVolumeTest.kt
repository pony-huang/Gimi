package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import java.util.Collections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话写入量契约。
 *
 * 升级前怀疑流式分块会逐个落库；实际 ADK 1.4 在 runner 与 `SessionService.appendEvent`
 * 两处都跳过 `partial` 事件，因此一轮对话的持久化次数等于「已定型的非流式事件数」。
 * 这里把该量级固定下来：如果未来某个版本开始持久化分块，或把一次并发工具批次的
 * 响应拆成多条事件，测试会失败并提醒重新评估写入缓冲。
 */
class AgentSessionWriteVolumeTest {

    @Test
    fun streamingChunksAreEmittedButNeverPersisted() = runTest {
        val recorder = RecordingSessionService()
        var step = 0
        val tool = object : BaseTool("clock", "fixture") {
            override fun declaration() = FunctionDeclaration(name, description)
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("time" to "12:00")
        }
        val model = object : Model {
            override val name = "write-volume-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                when (step++) {
                    0 -> {
                        // 先流式输出，再在同一轮里调用工具；否则纯文本的完整回答会直接结束本轮。
                        emit(response("Let me ", partial = true))
                        emit(response("Let me check", partial = true))
                        emit(LlmResponse(content = Content(role = "model", parts = listOf(
                            Part(functionCall = FunctionCall(id = "call-clock", name = tool.name, args = emptyMap())),
                        ))))
                    }
                    else -> {
                        emit(response("It is ", partial = true))
                        emit(response("It is 12:00", partial = true))
                        emit(response("It is 12:00", partial = false))
                    }
                }
            }
        }
        val runner = InMemoryRunner(
            App(
                appName = AgentChatRunner.APP_NAME,
                rootAgent = LlmAgent(name = "agent", model = model, tools = listOf(tool)),
                resumabilityConfig = ResumabilityConfig(isResumable = true),
            ),
            sessionService = recorder,
        )
        val execution = AgentChatExecution("user", "session", runner, emptyMap())

        val emitted = execution.send("hi").toList()

        assertEquals(4, emitted.count { it.partial })
        assertTrue("一轮工具调用必须留下 flush 边界", recorder.flushes.isNotEmpty())
        assertFalse(
            "流式分块不得进入持久化层",
            recorder.appended.any { it.partial },
        )
        // 一轮带工具的对话恰好 5 次写入：用户消息、工具调用、工具响应、收尾文本、
        // 以及收尾的空事件；4 个流式分块全部只走内存。写入量与分块数无关。
        assertEquals(5, recorder.appended.size)
        assertEquals("user", recorder.appended[0].author)
        assertEquals(listOf("clock"), recorder.appended[1].functionCalls().map { it.name })
        assertEquals(listOf("clock"), recorder.appended[2].functionResponses().map { it.name })
        assertEquals("It is 12:00", recorder.appended[3].content?.parts?.single()?.text)
        assertEquals(null, recorder.appended[4].content)
    }

    @Test
    fun oneMergedEventCarriesEveryParallelToolResponse() = runTest {
        val recorder = RecordingSessionService()
        var step = 0
        val tools = listOf("alpha", "beta").map { name ->
            object : BaseTool(name, "fixture") {
                override fun declaration() = FunctionDeclaration(name, "fixture")
                override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
                    mapOf("tool" to name)
            }
        }
        val model = object : Model {
            override val name = "write-volume-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                if (step++ == 0) {
                    emit(LlmResponse(content = Content(role = "model", parts = tools.map { tool ->
                        Part(functionCall = FunctionCall(id = "call-${tool.name}", name = tool.name, args = emptyMap()))
                    })))
                } else {
                    emit(response("Done", partial = false))
                }
            }
        }
        val runner = InMemoryRunner(
            App(
                appName = AgentChatRunner.APP_NAME,
                rootAgent = LlmAgent(name = "agent", model = model, tools = tools),
            ),
            sessionService = recorder,
        )
        val execution = AgentChatExecution("user", "session", runner, emptyMap())

        execution.send("run both").toList()

        // 1.2/1.3 每个工具各写一条响应事件；1.4 合并成一条，写入量随批次工具数不再线性增长。
        val responseEvents = recorder.appended.filter { event -> event.functionResponses().isNotEmpty() }
        assertEquals(1, responseEvents.size)
        assertEquals(2, responseEvents.single().functionResponses().size)
    }

    private fun response(text: String, partial: Boolean) = LlmResponse(
        content = Content(role = "model", parts = listOf(Part(text = text))),
        partial = partial,
    )

    /** 记录 appendEvent / flush 调用，其余行为委托给内存实现。 */
    private class RecordingSessionService(
        private val delegate: SessionService = InMemorySessionService(),
    ) : SessionService by delegate {
        val appended = Collections.synchronizedList(mutableListOf<Event>())
        val flushes = Collections.synchronizedList(mutableListOf<SessionKey?>())

        override suspend fun appendEvent(session: Session, event: Event): Event {
            appended += event
            return delegate.appendEvent(session, event)
        }

        override suspend fun flush(key: SessionKey?) {
            flushes += key
            delegate.flush(key)
        }
    }
}
