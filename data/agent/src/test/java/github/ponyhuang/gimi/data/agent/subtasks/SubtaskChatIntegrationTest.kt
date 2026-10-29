package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.*
import github.ponyhuang.gimi.data.agent.conversation.AdkChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.model.SubtaskStatus
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 真实主 Runner → 委派工具 → 子工作流 → 领域进度 → 主回答的最小跨层接线回归。 */
class SubtaskChatIntegrationTest {
    @Test
    fun streamsTaskProgressAlongsideParentEventsWithoutLeakingParentInputToWorker() = runTest {
        val sessions = InMemorySessionService()
        val store = SubtaskSessionStore(sessions)
        val config = ModelConfig("service", ApiProtocol.OpenaiChatcompletions, "fixture", "fixture-key", "https://fixture.invalid")
        val tools = object : Toolset {
            override suspend fun getTools(readonlyContext: com.google.adk.kt.agents.ReadonlyContext?) = emptyList<com.google.adk.kt.tools.BaseTool>()
        }
        val executor = SubtaskBatchExecutor(sessions, store, tools, {
            object : Model {
                override val name = "worker_fixture"
                override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                    val text = request.contents.flatMap { it.parts }.mapNotNull { it.text }.joinToString("\n")
                    assertTrue(text.contains("public-material"))
                    assertFalse(text.contains("private-parent-input"))
                    emit(LlmResponse(content = Content(role = "model", parts = listOf(Part(text = "child result")))))
                }
            }
        })
        var calls = 0
        val parent = object : Model {
            override val name = "parent_fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                emit(LlmResponse(content = Content(role = "model", parts = if (calls++ == 0) listOf(
                    Part(functionCall = FunctionCall(id = "delegate-call", name = "delegate_tasks", args = mapOf(
                        "tasks" to listOf(mapOf("title" to "分析", "instruction" to "summarize", "materials" to "public-material")),
                    ))),
                ) else listOf(Part(text = "parent summary")))))
            }
        }
        val access = object : ToolAccessRepository {
            override val defaultToolAccessMode = MutableStateFlow(ToolAccessMode.ALWAYS_AVAILABLE)
            override fun setDefaultToolAccessMode(mode: ToolAccessMode) { defaultToolAccessMode.value = mode }
        }
        val runner = AgentChatRunner(
            factory = { AgentRuntime(LlmAgent(name = "Assistant", model = parent,
                tools = listOf(DelegateTasksTool(executor, config))), config.toRuntimeMetadata()) },
            sessionService = sessions, artifactService = null, memoryService = null,
            toolAccessRepository = access,
        )
        sessions.createSession(SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, "chat"))
        val execution = AdkChatAgentRepository(runner, store).createExecution("chat", ModelSelection("service", "group", "fixture"))
        val events = execution.send("private-parent-input", emptyList()).toList()
        val snapshots = events.mapNotNull { it.subtaskBatch }
        assertTrue(snapshots.isNotEmpty())
        assertEquals(SubtaskStatus.COMPLETED, snapshots.last().tasks.single().status)
        assertEquals("child result", snapshots.last().tasks.single().result)
        assertTrue(events.flatMap { it.parts }.any { it.text == "parent summary" })
        assertFalse(events.flatMap { it.parts }.any { it.text == "child result" })
    }
}
